package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class MethodBodyTool extends BaseTool {
	public MethodBodyTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "get_method_body";
	}

	@Override
	public String description() {
		return "Return bytecode instruction summary for a specific method.";
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
			throw new McpException(INVALID_PARAMS, "Class bytes not available: " + entry.getParent().getFullName());
		}

		MethodNode methodNode = null;
		for (MethodNode mn : classNode.methods) {
			if (mn.name.equals(entry.getName()) && mn.desc.equals(entry.getDesc().toString())) {
				methodNode = mn;
				break;
			}
		}

		if (methodNode == null) {
			throw new McpException(INVALID_PARAMS, "Method not found in class bytes: " + entry);
		}

		JsonObject result = methodJson(project, entry);
		result.addProperty("maxStack", methodNode.maxStack);
		result.addProperty("maxLocals", methodNode.maxLocals);
		result.addProperty("instructions", methodNode.instructions.size());
		result.addProperty("tryCatchBlocks", methodNode.tryCatchBlocks == null ? 0 : methodNode.tryCatchBlocks.size());

		if (methodNode.localVariables != null) {
			JsonArray locals = new JsonArray();
			methodNode.localVariables.forEach(lv -> {
				JsonObject local = new JsonObject();
				local.addProperty("name", lv.name);
				local.addProperty("desc", lv.desc);
				local.addProperty("index", lv.index);
				locals.add(local);
			});
			result.add("localVariables", locals);
		}

		if (methodNode.exceptions != null && !methodNode.exceptions.isEmpty()) {
			result.add("exceptions", strings(methodNode.exceptions));
		}

		result.addProperty("isAbstract", (methodNode.access & Opcodes.ACC_ABSTRACT) != 0);
		result.addProperty("isNative", (methodNode.access & Opcodes.ACC_NATIVE) != 0);
		result.addProperty("isSynchronized", (methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0);

		return result;
	}
}
