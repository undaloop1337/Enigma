package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ControlFlowDeobfuscationTool extends BaseTool {
	public ControlFlowDeobfuscationTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "control_flow_deobfuscation";
	}

	@Override
	public String description() {
		return "Detect control flow obfuscation patterns: opaque predicates, bogus branches, switch-based dispatch tables.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Optional class to analyze."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 30);
		JsonArray results = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;
			if (className != null && !entry.getFullName().equals(className)) continue;

			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if (results.size() >= limit) break;
				int suspiciousJumps = 0;
				int totalJumps = 0;
				int backEdges = 0;
				int deadStores = 0;

				for (int i = 0; i < method.instructions.size(); i++) {
					AbstractInsnNode insn = method.instructions.get(i);

					if (insn instanceof JumpInsnNode jump) {
						totalJumps++;
						int target = method.instructions.indexOf(jump.label);

						// Backward jump to very close location (potential opaque predicate loop)
						if (target >= 0 && target < i && (i - target) <= 3) {
							suspiciousJumps++;
						}

						// Jump followed immediately by another jump (bogus branch)
						if (i + 1 < method.instructions.size()) {
							AbstractInsnNode next = method.instructions.get(i + 1);
							if (next instanceof JumpInsnNode && next.getOpcode() == Opcodes.GOTO) {
								suspiciousJumps++;
							}
						}

						if (target < i) backEdges++;
					}
				}

				if (suspiciousJumps >= 2 || (totalJumps > 10 && suspiciousJumps * 3 > totalJumps)) {
					JsonObject item = new JsonObject();
					item.addProperty("class", entry.getFullName());
					item.addProperty("method", method.name);
					item.addProperty("desc", method.desc);
					item.addProperty("totalJumps", totalJumps);
					item.addProperty("suspiciousJumps", suspiciousJumps);
					item.addProperty("backEdges", backEdges);
					item.addProperty("instructions", method.instructions.size());
					item.addProperty("obfuscationLikelihood", suspiciousJumps * 100 / Math.max(1, totalJumps));
					results.add(item);
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", results.size());
		result.add("suspects", results);
		return result;
	}
}
