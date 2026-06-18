package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class SearchMembersTool extends BaseTool {
	public SearchMembersTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "search_members";
	}

	@Override
	public String description() {
		return "Search methods and fields by name, owner, or descriptor.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("query", stringProperty("Substring to search."));
		properties.add("kind", stringProperty("method, field, or all. Defaults to all."));
		properties.add("className", stringProperty("Optional owner class filter."));
		properties.add("limit", integerProperty("Maximum number of members to return."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		String query = lower(getString(arguments, "query"));
		String kind = getString(arguments, "kind", "all");
		String className = getString(arguments, "className");
		int limit = limit(arguments, 100);
		JsonArray members = new JsonArray();

		if (!kind.equals("field")) {
			project.getJarIndex().getEntryIndex().getMethods().stream()
					.sorted(Comparator.comparing(MethodEntry::toString))
					.filter(entry -> matchesOwner(entry, className))
					.filter(entry -> query == null || matchesMethod(entry, query))
					.limit(limit)
					.forEach(entry -> members.add(methodJson(project, entry)));
		}

		if (!kind.equals("method") && members.size() < limit) {
			project.getJarIndex().getEntryIndex().getFields().stream()
					.sorted(Comparator.comparing(FieldEntry::toString))
					.filter(entry -> matchesOwner(entry, className))
					.filter(entry -> query == null || matchesField(entry, query))
					.limit(limit - members.size())
					.forEach(entry -> members.add(fieldJson(project, entry)));
		}

		JsonObject result = new JsonObject();
		result.add("members", members);
		return result;
	}

	private static boolean matchesMethod(MethodEntry entry, String query) {
		return lower(entry.getParent().getFullName()).contains(query) || lower(entry.getName()).contains(query) || lower(entry.getDesc().toString()).contains(query);
	}

	private static boolean matchesField(FieldEntry entry, String query) {
		return lower(entry.getParent().getFullName()).contains(query) || lower(entry.getName()).contains(query) || lower(entry.getDesc().toString()).contains(query);
	}
}
