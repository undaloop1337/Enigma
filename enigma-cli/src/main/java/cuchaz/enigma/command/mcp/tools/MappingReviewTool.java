package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class MappingReviewTool extends BaseTool {
	public MappingReviewTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "mapping_review";
	}

	@Override
	public String description() {
		return "Flag mappings that may need review: very short names, placeholder-looking names, inconsistent with hierarchy.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("limit", integerProperty("Maximum issues to report."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		int limit = limit(arguments, 50);
		JsonArray issues = new JsonArray();

		// Check classes
		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (issues.size() >= limit) break;
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;
			String name = getSimpleName(mapping.targetName());

			if (name.length() <= 2) {
				addIssue(issues, entry.toString(), mapping.targetName(), "very_short_name", "Name is only " + name.length() + " characters");
			} else if (name.matches("(?i)(temp|tmp|unknown|test|foo|bar|xxx|todo).*")) {
				addIssue(issues, entry.toString(), mapping.targetName(), "placeholder_name", "Looks like a placeholder");
			} else if (name.matches("[a-z].*") && !name.contains("/")) {
				addIssue(issues, entry.toString(), mapping.targetName(), "lowercase_class", "Class names should be PascalCase");
			}
		}

		// Check methods
		for (MethodEntry entry : project.getJarIndex().getEntryIndex().getMethods()) {
			if (issues.size() >= limit) break;
			if (entry.isConstructor()) continue;
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;

			if (mapping.targetName().length() <= 1) {
				addIssue(issues, entry.toString(), mapping.targetName(), "very_short_name", "Single character method name");
			} else if (mapping.targetName().matches("[A-Z].*")) {
				addIssue(issues, entry.toString(), mapping.targetName(), "uppercase_method", "Method names should be camelCase");
			}
		}

		// Check fields
		for (FieldEntry entry : project.getJarIndex().getEntryIndex().getFields()) {
			if (issues.size() >= limit) break;
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;

			if (mapping.targetName().length() <= 1) {
				addIssue(issues, entry.toString(), mapping.targetName(), "very_short_name", "Single character field name");
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("issueCount", issues.size());
		result.add("issues", issues);
		return result;
	}

	private static void addIssue(JsonArray issues, String entry, String name, String kind, String reason) {
		JsonObject issue = new JsonObject();
		issue.addProperty("entry", entry);
		issue.addProperty("mappedName", name);
		issue.addProperty("kind", kind);
		issue.addProperty("reason", reason);
		issues.add(issue);
	}

	private static String getSimpleName(String name) {
		int slash = name.lastIndexOf('/');
		return slash >= 0 ? name.substring(slash + 1) : name;
	}
}
