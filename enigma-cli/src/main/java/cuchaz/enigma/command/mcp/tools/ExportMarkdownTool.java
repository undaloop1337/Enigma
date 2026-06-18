package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ExportMarkdownTool extends BaseTool {
	public ExportMarkdownTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "export_markdown";
	}

	@Override
	public String description() {
		return "Export project information as formatted Markdown: class structure, mappings, or analysis results.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("type", stringProperty("What to export: class_summary, mapping_report, package_overview, or project_summary."));
		properties.add("className", stringProperty("Class name (for class_summary type)."));
		properties.add("packageFilter", stringProperty("Optional package prefix filter."));
		properties.add("limit", integerProperty("Maximum entries per section."));
		require(schema, "type");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String type = getString(arguments, "type");
		int limit = limit(arguments, 50);

		String markdown = switch (type) {
			case "class_summary" -> classMarkdown(arguments, limit);
			case "mapping_report" -> mappingReportMarkdown(arguments, limit);
			case "package_overview" -> packageOverviewMarkdown(arguments, limit);
			case "project_summary" -> projectSummaryMarkdown(limit);
			default -> throw new McpException(INVALID_PARAMS, "Unknown export type: " + type);
		};

		JsonObject result = new JsonObject();
		result.addProperty("type", type);
		result.addProperty("markdown", markdown);
		return result;
	}

	private String classMarkdown(JsonObject arguments, int limit) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		ClassEntry deobf = project.getMapper().deobfuscate(entry);
		StringBuilder sb = new StringBuilder();

		sb.append("# Class: `").append(deobf.getFullName()).append("`\n\n");
		sb.append("| Property | Value |\n|----------|-------|\n");
		sb.append("| Obfuscated | `").append(entry.getFullName()).append("` |\n");
		sb.append("| Deobfuscated | `").append(deobf.getFullName()).append("` |\n");

		var parents = project.getJarIndex().getInheritanceIndex().getParents(entry);
		if (!parents.isEmpty()) {
			sb.append("| Parents | ");
			parents.forEach(p -> sb.append("`").append(p.getFullName()).append("` "));
			sb.append("|\n");
		}

		sb.append("\n## Methods\n\n");
		sb.append("| Name | Descriptor | Mapped |\n|------|-----------|--------|\n");
		project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry))
				.sorted(Comparator.comparing(MethodEntry::getName))
				.limit(limit)
				.forEach(m -> {
					EntryMapping mapping = project.getMapper().getDeobfMapping(m);
					sb.append("| `").append(m.getName()).append("` | `").append(m.getDesc()).append("` | ");
					sb.append(mapping.targetName() != null ? "`" + mapping.targetName() + "`" : "-");
					sb.append(" |\n");
				});

		sb.append("\n## Fields\n\n");
		sb.append("| Name | Type | Mapped |\n|------|------|--------|\n");
		project.getJarIndex().getEntryIndex().getFields().stream()
				.filter(f -> f.getParent().equals(entry))
				.sorted(Comparator.comparing(FieldEntry::getName))
				.limit(limit)
				.forEach(f -> {
					EntryMapping mapping = project.getMapper().getDeobfMapping(f);
					sb.append("| `").append(f.getName()).append("` | `").append(f.getDesc()).append("` | ");
					sb.append(mapping.targetName() != null ? "`" + mapping.targetName() + "`" : "-");
					sb.append(" |\n");
				});

		return sb.toString();
	}

	private String mappingReportMarkdown(JsonObject arguments, int limit) {
		String packageFilter = getString(arguments, "packageFilter");
		StringBuilder sb = new StringBuilder();
		sb.append("# Mapping Report\n\n");

		int totalClasses = 0, mapped = 0;
		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (packageFilter != null && !entry.getFullName().startsWith(packageFilter)) continue;
			totalClasses++;
			if (project.getMapper().getDeobfMapping(entry).targetName() != null) mapped++;
		}

		int totalMethods = 0, mappedMethods = 0;
		for (MethodEntry entry : project.getJarIndex().getEntryIndex().getMethods()) {
			if (entry.isConstructor()) continue;
			if (packageFilter != null && !entry.getParent().getFullName().startsWith(packageFilter)) continue;
			totalMethods++;
			if (project.getMapper().getDeobfMapping(entry).targetName() != null) mappedMethods++;
		}

		sb.append("## Progress\n\n");
		sb.append("| Category | Mapped | Total | Progress |\n|----------|--------|-------|----------|\n");
		sb.append(String.format("| Classes | %d | %d | %.1f%% |\n", mapped, totalClasses, totalClasses == 0 ? 0 : 100.0 * mapped / totalClasses));
		sb.append(String.format("| Methods | %d | %d | %.1f%% |\n", mappedMethods, totalMethods, totalMethods == 0 ? 0 : 100.0 * mappedMethods / totalMethods));

		sb.append("\n## Recently Mapped Classes\n\n");
		sb.append("| Obfuscated | Deobfuscated |\n|-----------|-------------|\n");
		project.getJarIndex().getEntryIndex().getClasses().stream()
				.filter(e -> packageFilter == null || e.getFullName().startsWith(packageFilter))
				.filter(e -> project.getMapper().getDeobfMapping(e).targetName() != null)
				.sorted(Comparator.comparing(ClassEntry::getFullName))
				.limit(limit)
				.forEach(e -> {
					EntryMapping m = project.getMapper().getDeobfMapping(e);
					sb.append("| `").append(e.getFullName()).append("` | `").append(m.targetName()).append("` |\n");
				});

		return sb.toString();
	}

	private String packageOverviewMarkdown(JsonObject arguments, int limit) {
		String packageFilter = getString(arguments, "packageFilter");
		StringBuilder sb = new StringBuilder();
		sb.append("# Package Overview\n\n");
		sb.append("| Package | Classes | Mapped |\n|---------|---------|--------|\n");

		java.util.Map<String, int[]> stats = new java.util.LinkedHashMap<>();
		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			String pkg = entry.getPackageName() == null ? "(default)" : entry.getPackageName();
			if (packageFilter != null && !pkg.startsWith(packageFilter)) continue;
			int[] counts = stats.computeIfAbsent(pkg, k -> new int[2]);
			counts[0]++;
			if (project.getMapper().getDeobfMapping(entry).targetName() != null) counts[1]++;
		}

		stats.entrySet().stream()
				.sorted(java.util.Map.Entry.<String, int[]>comparingByValue((a, b) -> b[0] - a[0]))
				.limit(limit)
				.forEach(e -> sb.append("| `").append(e.getKey()).append("` | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n"));

		return sb.toString();
	}

	private String projectSummaryMarkdown(int limit) {
		StringBuilder sb = new StringBuilder();
		sb.append("# Project Summary\n\n");
		sb.append("| Metric | Count |\n|--------|-------|\n");
		sb.append("| Classes | ").append(project.getJarIndex().getEntryIndex().getClasses().size()).append(" |\n");
		sb.append("| Methods | ").append(project.getJarIndex().getEntryIndex().getMethods().size()).append(" |\n");
		sb.append("| Fields | ").append(project.getJarIndex().getEntryIndex().getFields().size()).append(" |\n\n");

		sb.append("## Top Referenced Classes\n\n");
		sb.append("| Class | References |\n|-------|------------|\n");
		project.getJarIndex().getEntryIndex().getClasses().stream()
				.sorted(Comparator.comparingInt((ClassEntry e) -> project.getJarIndex().getReferenceIndex().getReferencesToClass(e).size()).reversed())
				.limit(limit)
				.forEach(e -> {
					int refs = project.getJarIndex().getReferenceIndex().getReferencesToClass(e).size();
					String deobf = project.getMapper().deobfuscate(e).getFullName();
					sb.append("| `").append(deobf).append("` | ").append(refs).append(" |\n");
				});

		return sb.toString();
	}
}