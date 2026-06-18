package cuchaz.enigma.command.mcp.tools;

import java.util.Collection;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class InheritanceTool extends BaseTool {
	public InheritanceTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "get_inheritance";
	}

	@Override
	public String description() {
		return "Inspect class parents, children, ancestors, or descendants.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		properties.add("direction", stringProperty("parents, children, ancestors, or descendants."));
		properties.add("limit", integerProperty("Maximum number of classes to return."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		String direction = getString(arguments, "direction", "parents");
		Collection<ClassEntry> classes;

		if (direction.equals("parents")) {
			classes = project.getJarIndex().getInheritanceIndex().getParents(entry);
		} else if (direction.equals("children")) {
			classes = project.getJarIndex().getInheritanceIndex().getChildren(entry);
		} else if (direction.equals("ancestors")) {
			classes = project.getJarIndex().getInheritanceIndex().getAncestors(entry);
		} else if (direction.equals("descendants")) {
			classes = project.getJarIndex().getInheritanceIndex().getDescendants(entry);
		} else {
			throw new McpException(INVALID_PARAMS, "Unknown direction: " + direction);
		}

		JsonObject result = classJson(project, entry);
		result.addProperty("direction", direction);
		result.add("classes", classArray(project, classes, limit(arguments, 100)));
		return result;
	}
}
