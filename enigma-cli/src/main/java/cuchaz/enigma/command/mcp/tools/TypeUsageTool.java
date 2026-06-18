package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class TypeUsageTool extends BaseTool {
	public TypeUsageTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "type_usage";
	}

	@Override
	public String description() {
		return "Find where a class type is used as field type, method parameter, or return type.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name to search for."));
		properties.add("limit", integerProperty("Maximum results."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		int limit = limit(arguments, 100);
		String typeDesc = "L" + entry.getFullName() + ";";

		JsonArray fieldUsages = new JsonArray();
		JsonArray methodParamUsages = new JsonArray();
		JsonArray methodReturnUsages = new JsonArray();

		// Fields with this type
		for (FieldEntry field : project.getJarIndex().getEntryIndex().getFields()) {
			if (fieldUsages.size() >= limit) break;
			if (field.getDesc().toString().contains(typeDesc)) {
				fieldUsages.add(fieldJson(project, field));
			}
		}

		// Methods with this type in signature
		for (MethodEntry method : project.getJarIndex().getEntryIndex().getMethods()) {
			if (methodParamUsages.size() >= limit && methodReturnUsages.size() >= limit) break;
			String desc = method.getDesc().toString();
			if (!desc.contains(typeDesc)) continue;

			int parenClose = desc.indexOf(')');
			String params = desc.substring(1, parenClose);
			String ret = desc.substring(parenClose + 1);

			if (params.contains(typeDesc) && methodParamUsages.size() < limit) {
				methodParamUsages.add(methodJson(project, method));
			}
			if (ret.contains(typeDesc) && methodReturnUsages.size() < limit) {
				methodReturnUsages.add(methodJson(project, method));
			}
		}

		JsonObject result = classJson(project, entry);
		result.add("asFieldType", fieldUsages);
		result.add("asParameter", methodParamUsages);
		result.add("asReturnType", methodReturnUsages);
		return result;
	}
}
