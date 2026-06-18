package cuchaz.enigma.command.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class PackageStatsTool extends BaseTool {
	public PackageStatsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "package_stats";
	}

	@Override
	public String description() {
		return "Summarize packages by class count for triaging a jar.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		schema.getAsJsonObject("properties").add("limit", integerProperty("Maximum number of packages to return."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		Map<String, Integer> counts = new LinkedHashMap<>();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			String packageName = entry.getPackageName();
			if (packageName == null) {
				packageName = "";
			}
			counts.merge(packageName, 1, Integer::sum);
		}

		JsonArray packages = new JsonArray();
		counts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.limit(limit(arguments, 100))
				.forEach(entry -> {
					JsonObject item = new JsonObject();
					item.addProperty("package", entry.getKey());
					item.addProperty("classes", entry.getValue());
					packages.add(item);
				});

		JsonObject result = new JsonObject();
		result.add("packages", packages);
		return result;
	}
}
