package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class SearchClassesTool extends BaseTool {
	private final String toolName;
	private final String toolDescription;

	public SearchClassesTool(EnigmaProject project) {
		this(project, "search_classes", "Search classes by obfuscated or deobfuscated name.");
	}

	public SearchClassesTool(EnigmaProject project, String name, String description) {
		super(project);
		this.toolName = name;
		this.toolDescription = description;
	}

	@Override
	public String name() {
		return toolName;
	}

	@Override
	public String description() {
		return toolDescription;
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("query", stringProperty("Optional substring to filter class names."));
		properties.add("limit", integerProperty("Maximum number of classes to return."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		String query = lower(getString(arguments, "query"));
		int limit = limit(arguments, 100);
		JsonArray classes = new JsonArray();

		project.getJarIndex().getEntryIndex().getClasses().stream()
				.sorted(Comparator.comparing(ClassEntry::getFullName))
				.filter(entry -> query == null || lower(entry.getFullName()).contains(query) || lower(project.getMapper().deobfuscate(entry).getFullName()).contains(query))
				.limit(limit)
				.forEach(entry -> classes.add(classJson(project, entry)));

		JsonObject result = new JsonObject();
		result.add("classes", classes);
		return result;
	}
}
