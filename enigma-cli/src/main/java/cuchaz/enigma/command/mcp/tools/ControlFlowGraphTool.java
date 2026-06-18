package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ControlFlowGraphTool extends BaseTool {
	public ControlFlowGraphTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "control_flow_graph";
	}

	@Override
	public String description() {
		return "Analyze a method's control flow: basic blocks, branches, loops, and exception handlers.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Method name."));
		properties.add("desc", stringProperty("Method descriptor."));
		require(schema, "className", "name", "desc");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		MethodEntry entry = requireKnownMethod(project, arguments);
		ClassNode classNode = project.getClassProvider().get(entry.getParent().getFullName());
		if (classNode == null) {
			throw new McpException(INVALID_PARAMS, "Class bytes not available");
		}

		MethodNode method = null;
		for (MethodNode mn : classNode.methods) {
			if (mn.name.equals(entry.getName()) && mn.desc.equals(entry.getDesc().toString())) {
				method = mn;
				break;
			}
		}

		if (method == null) {
			throw new McpException(INVALID_PARAMS, "Method not found in bytecode");
		}

		// Count basic blocks (split at labels, jumps, returns)
		int blockCount = 1;
		int jumpCount = 0;
		int returnCount = 0;
		int switchCount = 0;
		int loopHintCount = 0;

		List<Integer> labelIndices = new ArrayList<>();
		for (int i = 0; i < method.instructions.size(); i++) {
			AbstractInsnNode insn = method.instructions.get(i);
			if (insn instanceof LabelNode) {
				labelIndices.add(i);
				blockCount++;
			}
			if (insn instanceof JumpInsnNode jump) {
				jumpCount++;
				// Backward jump = loop hint
				int targetIndex = method.instructions.indexOf(jump.label);
				if (targetIndex < i) {
					loopHintCount++;
				}
			}
			int opcode = insn.getOpcode();
			if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
				returnCount++;
			}
			if (insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) {
				switchCount++;
			}
		}

		JsonObject result = methodJson(project, entry);
		result.addProperty("instructions", method.instructions.size());
		result.addProperty("basicBlocks", blockCount);
		result.addProperty("jumps", jumpCount);
		result.addProperty("returns", returnCount);
		result.addProperty("switches", switchCount);
		result.addProperty("loopHints", loopHintCount);
		result.addProperty("tryCatchBlocks", method.tryCatchBlocks == null ? 0 : method.tryCatchBlocks.size());

		if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
			JsonArray handlers = new JsonArray();
			for (TryCatchBlockNode tcb : method.tryCatchBlocks) {
				JsonObject handler = new JsonObject();
				handler.addProperty("type", tcb.type == null ? "finally" : tcb.type);
				handlers.add(handler);
			}
			result.add("exceptionHandlers", handlers);
		}

		return result;
	}
}
