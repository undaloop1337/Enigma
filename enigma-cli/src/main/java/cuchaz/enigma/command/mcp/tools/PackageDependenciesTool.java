package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class PackageDependenciesTool extends BaseTool {
	public PackageDependenciesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "package_dependencies";
	}

	@Override
	public String description() {
		return "Show inter-package dependency graph: which packages depend on which.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("packageName", stringProperty("Optional package to focus on. If omitted, shows full graph."));
		properties.add("limit", integerProperty("Maximum number of dependency edges."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		String packageName = getString(arguments, "packageName");
		int limit = limit(arguments, 200);

		// Build package dependency map
		Map<String, Set<String>> deps = new HashMap<>();

		for (MethodEntry method : project.getJarIndex().getEntryIndex().getMethods()) {
			String srcPackage = getPackage(method.getParent());

			for (MethodEntry called : project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(method)) {
				String dstPackage = getPackage(called.getParent());
				if (!srcPackage.equals(dstPackage)) {
					deps.computeIfAbsent(srcPackage, k -> new HashSet<>()).add(dstPackage);
				}
			}
		}

		JsonArray edges = new JsonArray();
		int count = 0;

		if (packageName != null) {
			// Show deps for a specific package
			Set<String> outgoing = deps.getOrDefault(packageName, Set.of());
			for (String dep : outgoing.stream().sorted().toList()) {
				if (count++ >= limit) break;
				JsonObject edge = new JsonObject();
				edge.addProperty("from", packageName);
				edge.addProperty("to", dep);
				edge.addProperty("direction", "outgoing");
				edges.add(edge);
			}
			// Show who depends on this package
			for (var entry : deps.entrySet()) {
				if (count >= limit) break;
				if (entry.getValue().contains(packageName)) {
					JsonObject edge = new JsonObject();
					edge.addProperty("from", entry.getKey());
					edge.addProperty("to", packageName);
					edge.addProperty("direction", "incoming");
					edges.add(edge);
					count++;
				}
			}
		} else {
			for (var entry : deps.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
				for (String dep : entry.getValue().stream().sorted().toList()) {
					if (count++ >= limit) break;
					JsonObject edge = new JsonObject();
					edge.addProperty("from", entry.getKey());
					edge.addProperty("to", dep);
					edges.add(edge);
				}
				if (count >= limit) break;
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("packageCount", deps.size());
		result.addProperty("edgeCount", edges.size());
		result.add("edges", edges);
		return result;
	}

	private static String getPackage(ClassEntry entry) {
		String name = entry.getFullName();
		int lastSlash = name.lastIndexOf('/');
		return lastSlash < 0 ? "" : name.substring(0, lastSlash);
	}
}
