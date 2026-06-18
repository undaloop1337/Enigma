package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.ParentedEntry;

public class InnerClassesTool extends BaseTool {
	public InnerClassesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "inner_classes";
	}

	@Override
	public String description() {
		return "Show inner class hierarchy for a given class (inner classes it contains, or its outer class).";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		properties.add("limit", integerProperty("Maximum number of inner classes to return."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		int limit = limit(arguments, 100);

		JsonObject result = classJson(project, entry);

		// Find outer class
		if (entry.isInnerClass()) {
			ClassEntry outer = entry.getOuterClass();
			if (outer != null) {
				result.add("outerClass", classJson(project, outer));
			}
		}

		// Find inner classes from childrenByClass
		Map<ClassEntry, List<ParentedEntry<?>>> childrenMap = project.getJarIndex().getChildrenByClass();
		List<ParentedEntry<?>> children = childrenMap.get(entry);

		JsonArray innerClasses = new JsonArray();
		if (children != null) {
			children.stream()
					.filter(child -> child instanceof ClassEntry)
					.map(child -> (ClassEntry) child)
					.sorted(Comparator.comparing(ClassEntry::getFullName))
					.limit(limit)
					.forEach(inner -> innerClasses.add(classJson(project, inner)));
		}

		result.add("innerClasses", innerClasses);
		result.addProperty("innerClassCount", innerClasses.size());
		return result;
	}
}
