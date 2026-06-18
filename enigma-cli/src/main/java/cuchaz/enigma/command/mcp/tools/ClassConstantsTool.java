package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class ClassConstantsTool extends BaseTool {
	public ClassConstantsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "class_constants";
	}

	@Override
	public String description() {
		return "Extract LDC string and numeric constants from a class.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		properties.add("limit", integerProperty("Maximum number of constants to return."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		ClassNode node = project.getClassProvider().get(entry.getFullName());

		if (node == null) {
			throw new McpException(INVALID_PARAMS, "Class bytes not available: " + entry.getFullName());
		}

		JsonArray constants = new JsonArray();
		int limit = limit(arguments, 200);

		for (MethodNode method : node.methods) {
			for (AbstractInsnNode instruction : method.instructions) {
				if (instruction instanceof LdcInsnNode ldc) {
					JsonObject item = new JsonObject();
					item.addProperty("method", method.name);
					item.addProperty("desc", method.desc);
					item.addProperty("type", ldc.cst == null ? "null" : ldc.cst.getClass().getSimpleName());
					item.addProperty("value", String.valueOf(ldc.cst));
					constants.add(item);

					if (constants.size() >= limit) {
						break;
					}
				}
			}

			if (constants.size() >= limit) {
				break;
			}
		}

		JsonObject result = classJson(project, entry);
		result.add("constants", constants);
		return result;
	}
}
