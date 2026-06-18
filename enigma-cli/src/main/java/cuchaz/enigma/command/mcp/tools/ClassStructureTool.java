package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ClassStructureTool extends BaseTool {
	public ClassStructureTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "get_class_structure";
	}

	@Override
	public String description() {
		return "Return fields, methods, and inheritance for a class.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		properties.add("includeMethods", booleanProperty("Include methods. Defaults to true."));
		properties.add("includeFields", booleanProperty("Include fields. Defaults to true."));
		properties.add("limit", integerProperty("Maximum methods and fields per section."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		int limit = limit(arguments, 200);
		JsonObject result = classJson(project, entry);
		result.addProperty("access", project.getJarIndex().getEntryIndex().getAccess(entry));
		result.add("parents", classArray(project, project.getJarIndex().getInheritanceIndex().getParents(entry), limit));
		result.add("children", classArray(project, project.getJarIndex().getInheritanceIndex().getChildren(entry), limit));

		if (getBoolean(arguments, "includeFields", true)) {
			JsonArray fields = new JsonArray();
			project.getJarIndex().getEntryIndex().getFields().stream()
					.filter(field -> field.getParent().equals(entry))
					.sorted(Comparator.comparing(FieldEntry::toString))
					.limit(limit)
					.forEach(field -> fields.add(fieldJson(project, field)));
			result.add("fields", fields);
		}

		if (getBoolean(arguments, "includeMethods", true)) {
			JsonArray methods = new JsonArray();
			project.getJarIndex().getEntryIndex().getMethods().stream()
					.filter(method -> method.getParent().equals(entry))
					.sorted(Comparator.comparing(MethodEntry::toString))
					.limit(limit)
					.forEach(method -> methods.add(methodJson(project, method)));
			result.add("methods", methods);
		}

		return result;
	}
}
