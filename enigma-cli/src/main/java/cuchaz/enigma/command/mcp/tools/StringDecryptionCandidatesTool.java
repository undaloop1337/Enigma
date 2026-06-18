package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class StringDecryptionCandidatesTool extends BaseTool {
	public StringDecryptionCandidatesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "string_decryption_candidates";
	}

	@Override
	public String description() {
		return "Identify methods that likely perform string decryption/deobfuscation based on patterns: XOR/shift operations, byte array to String conversion, called many times with different constants.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("minCallers", integerProperty("Minimum number of unique callers. Defaults to 3."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		int minCallers = getInt(arguments, "minCallers", 3);
		int limit = limit(arguments, 30);

		record Candidate(MethodEntry entry, int score, int callers, String reason) {}
		List<Candidate> candidates = new ArrayList<>();

		for (MethodEntry method : project.getJarIndex().getEntryIndex().getMethods()) {
			if (method.isConstructor()) continue;
			String desc = method.getDesc().toString();

			// Must return String or byte[]
			if (!desc.endsWith(")Ljava/lang/String;") && !desc.endsWith(")[B")) continue;

			int callers = project.getJarIndex().getReferenceIndex().getReferencesToMethod(method).size();
			if (callers < minCallers) continue;

			ClassNode node = project.getClassProvider().get(method.getParent().getFullName());
			if (node == null) continue;

			for (MethodNode mn : node.methods) {
				if (!mn.name.equals(method.getName()) || !mn.desc.equals(desc)) continue;

				int score = 0;
				StringBuilder reason = new StringBuilder();

				// Check for XOR, shift, array operations
				boolean hasXor = false, hasShift = false, hasArrayOps = false, hasNew = false;
				for (AbstractInsnNode insn : mn.instructions) {
					int op = insn.getOpcode();
					if (op == Opcodes.IXOR || op == Opcodes.LXOR) hasXor = true;
					if (op == Opcodes.ISHL || op == Opcodes.ISHR || op == Opcodes.IUSHR) hasShift = true;
					if (op == Opcodes.BALOAD || op == Opcodes.BASTORE || op == Opcodes.CALOAD || op == Opcodes.CASTORE) hasArrayOps = true;
					if (insn instanceof MethodInsnNode min) {
						if (min.owner.equals("java/lang/String") && min.name.equals("<init>")) hasNew = true;
					}
				}

				if (hasXor) { score += 30; reason.append("XOR "); }
				if (hasShift) { score += 20; reason.append("SHIFT "); }
				if (hasArrayOps) { score += 15; reason.append("ARRAY_OPS "); }
				if (hasNew) { score += 10; reason.append("STRING_CTOR "); }
				score += Math.min(callers, 50); // More callers = more likely a utility

				if (score >= 20) {
					reason.append("callers=").append(callers);
					candidates.add(new Candidate(method, score, callers, reason.toString().trim()));
				}
				break;
			}
		}

		candidates.sort(Comparator.comparingInt((Candidate c) -> c.score()).reversed());

		JsonArray results = new JsonArray();
		candidates.stream().limit(limit).forEach(c -> {
			JsonObject item = methodJson(project, c.entry());
			item.addProperty("score", c.score());
			item.addProperty("callers", c.callers());
			item.addProperty("reason", c.reason());
			results.add(item);
		});

		JsonObject result = new JsonObject();
		result.addProperty("candidateCount", results.size());
		result.add("candidates", results);
		return result;
	}
}
