package cuchaz.enigma.command.mcp.tools;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class NamingConventionAnalysisTool extends BaseTool {
	public NamingConventionAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "naming_convention_analysis";
	}

	@Override
	public String description() {
		return "Analyze existing mapped names for consistency: camelCase vs snake_case, abbreviation patterns, naming violations.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("limit", integerProperty("Maximum violations to report."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		int limit = limit(arguments, 50);
		Map<String, Integer> patterns = new HashMap<>();
		JsonArray violations = new JsonArray();

		// Analyze class names
		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;
			String name = getSimpleName(mapping.targetName());
			String pattern = detectPattern(name);
			patterns.merge("class:" + pattern, 1, Integer::sum);

			if (!pattern.equals("PascalCase") && violations.size() < limit) {
				JsonObject v = new JsonObject();
				v.addProperty("entry", entry.getFullName());
				v.addProperty("mappedName", mapping.targetName());
				v.addProperty("pattern", pattern);
				v.addProperty("expected", "PascalCase");
				violations.add(v);
			}
		}

		// Analyze method names
		for (MethodEntry entry : project.getJarIndex().getEntryIndex().getMethods()) {
			if (entry.isConstructor()) continue;
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;
			String pattern = detectPattern(mapping.targetName());
			patterns.merge("method:" + pattern, 1, Integer::sum);

			if (!pattern.equals("camelCase") && violations.size() < limit) {
				JsonObject v = new JsonObject();
				v.addProperty("entry", entry.toString());
				v.addProperty("mappedName", mapping.targetName());
				v.addProperty("pattern", pattern);
				v.addProperty("expected", "camelCase");
				violations.add(v);
			}
		}

		// Analyze field names
		for (FieldEntry entry : project.getJarIndex().getEntryIndex().getFields()) {
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;
			String pattern = detectPattern(mapping.targetName());
			patterns.merge("field:" + pattern, 1, Integer::sum);

			if (!pattern.equals("camelCase") && !pattern.equals("UPPER_SNAKE") && violations.size() < limit) {
				JsonObject v = new JsonObject();
				v.addProperty("entry", entry.toString());
				v.addProperty("mappedName", mapping.targetName());
				v.addProperty("pattern", pattern);
				v.addProperty("expected", "camelCase or UPPER_SNAKE");
				violations.add(v);
			}
		}

		JsonObject patternStats = new JsonObject();
		patterns.forEach(patternStats::addProperty);

		JsonObject result = new JsonObject();
		result.add("patterns", patternStats);
		result.addProperty("violationCount", violations.size());
		result.add("violations", violations);
		return result;
	}

	private static String getSimpleName(String name) {
		int slash = name.lastIndexOf('/');
		return slash >= 0 ? name.substring(slash + 1) : name;
	}

	private static String detectPattern(String name) {
		if (name.matches("[A-Z][A-Z0-9_]*")) return "UPPER_SNAKE";
		if (name.matches("[a-z][a-z0-9_]*(_[a-z0-9]+)+")) return "snake_case";
		if (name.matches("[A-Z][a-zA-Z0-9]*")) return "PascalCase";
		if (name.matches("[a-z][a-zA-Z0-9]*")) return "camelCase";
		return "other";
	}
}
