package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class MappingCoverageReportTool extends BaseTool {
	public MappingCoverageReportTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "mapping_coverage_report";
	}

	@Override
	public String description() {
		return "Report mapping coverage broken down by package: classes, methods, and fields mapped vs total.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("limit", integerProperty("Maximum packages to show."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		int limit = limit(arguments, 50);

		Map<String, int[]> stats = new LinkedHashMap<>(); // [totalClasses, mappedClasses, totalMethods, mappedMethods, totalFields, mappedFields]

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			String pkg = entry.getPackageName() == null ? "(default)" : entry.getPackageName();
			int[] s = stats.computeIfAbsent(pkg, k -> new int[6]);
			s[0]++;
			if (project.getMapper().getDeobfMapping(entry).targetName() != null) s[1]++;
		}

		for (MethodEntry entry : project.getJarIndex().getEntryIndex().getMethods()) {
			if (entry.isConstructor()) continue;
			String pkg = entry.getParent().getPackageName() == null ? "(default)" : entry.getParent().getPackageName();
			int[] s = stats.computeIfAbsent(pkg, k -> new int[6]);
			s[2]++;
			if (project.getMapper().getDeobfMapping(entry).targetName() != null) s[3]++;
		}

		for (FieldEntry entry : project.getJarIndex().getEntryIndex().getFields()) {
			String pkg = entry.getParent().getPackageName() == null ? "(default)" : entry.getParent().getPackageName();
			int[] s = stats.computeIfAbsent(pkg, k -> new int[6]);
			s[4]++;
			if (project.getMapper().getDeobfMapping(entry).targetName() != null) s[5]++;
		}

		JsonArray packages = new JsonArray();
		stats.entrySet().stream()
				.sorted(Comparator.comparingInt((Map.Entry<String, int[]> e) -> e.getValue()[0]).reversed())
				.limit(limit)
				.forEach(e -> {
					int[] s = e.getValue();
					JsonObject item = new JsonObject();
					item.addProperty("package", e.getKey());
					item.addProperty("totalClasses", s[0]);
					item.addProperty("mappedClasses", s[1]);
					item.addProperty("totalMethods", s[2]);
					item.addProperty("mappedMethods", s[3]);
					item.addProperty("totalFields", s[4]);
					item.addProperty("mappedFields", s[5]);
					int total = s[0] + s[2] + s[4];
					int mapped = s[1] + s[3] + s[5];
					item.addProperty("coveragePercent", total == 0 ? 0.0 : Math.round(1000.0 * mapped / total) / 10.0);
					packages.add(item);
				});

		JsonObject result = new JsonObject();
		result.addProperty("packageCount", stats.size());
		result.add("packages", packages);
		return result;
	}
}
