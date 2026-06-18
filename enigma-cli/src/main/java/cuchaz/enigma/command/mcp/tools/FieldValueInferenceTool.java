package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class FieldValueInferenceTool extends BaseTool {
	public FieldValueInferenceTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "field_value_inference";
	}

	@Override
	public String description() {
		return "For final/static fields, infer their values from static initializers or constant pool.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Class to analyze."));
		properties.add("limit", integerProperty("Maximum results."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		int limit = limit(arguments, 50);

		ClassNode node = project.getClassProvider().get(entry.getFullName());
		if (node == null) {
			throw new McpException(INVALID_PARAMS, "Class bytes not available");
		}

		JsonArray fields = new JsonArray();
		for (FieldNode field : node.fields) {
			if (fields.size() >= limit) break;
			JsonObject item = new JsonObject();
			item.addProperty("name", field.name);
			item.addProperty("desc", field.desc);
			item.addProperty("static", (field.access & Opcodes.ACC_STATIC) != 0);
			item.addProperty("final", (field.access & Opcodes.ACC_FINAL) != 0);

			// Compile-time constant
			if (field.value != null) {
				item.addProperty("value", field.value.toString());
				item.addProperty("source", "constant_pool");
			} else if ((field.access & Opcodes.ACC_STATIC) != 0) {
				// Try to find in <clinit>
				String value = findClinitValue(node, field.name, field.desc);
				if (value != null) {
					item.addProperty("value", value);
					item.addProperty("source", "clinit");
				}
			}

			fields.add(item);
		}

		JsonObject result = classJson(project, entry);
		result.add("fields", fields);
		return result;
	}

	private static String findClinitValue(ClassNode node, String fieldName, String fieldDesc) {
		for (MethodNode method : node.methods) {
			if (!method.name.equals("<clinit>")) continue;
			String lastValue = null;
			for (AbstractInsnNode insn : method.instructions) {
				if (insn instanceof LdcInsnNode ldc) {
					lastValue = String.valueOf(ldc.cst);
				}
				if (insn.getOpcode() == Opcodes.PUTSTATIC) {
					var fin = (org.objectweb.asm.tree.FieldInsnNode) insn;
					if (fin.name.equals(fieldName) && fin.desc.equals(fieldDesc) && fin.owner.equals(node.name)) {
						return lastValue;
					}
				}
			}
		}
		return null;
	}
}
