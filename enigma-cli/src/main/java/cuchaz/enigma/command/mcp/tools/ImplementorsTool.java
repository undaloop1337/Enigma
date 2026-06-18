package cuchaz.enigma.command.mcp.tools;

import java.util.Collection;
import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class ImplementorsTool extends BaseTool {
	public ImplementorsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "get_implementors";
	}

	@Override
	public String description() {
		return "Find all classes that implement or extend a given class or interface, recursively.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		properties.add("recursive", booleanProperty("Include indirect implementors. Defaults to true."));
		properties.add("limit", integerProperty("Maximum number of implementors to return."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		boolean recursive = getBoolean(arguments, "recursive", true);
		int limit = limit(arguments, 200);

		Collection<ClassEntry> implementors;
		if (recursive) {
			implementors = project.getJarIndex().getInheritanceIndex().getDescendants(entry);
		} else {
			implementors = project.getJarIndex().getInheritanceIndex().getChildren(entry);
		}

		JsonArray classes = new JsonArray();
		implementors.stream()
				.sorted(Comparator.comparing(ClassEntry::getFullName))
				.limit(limit)
				.forEach(impl -> classes.add(classJson(project, impl)));

		JsonObject result = classJson(project, entry);
		result.addProperty("recursive", recursive);
		result.addProperty("totalImplementors", implementors.size());
		result.add("implementors", classes);
		return result;
	}
}
