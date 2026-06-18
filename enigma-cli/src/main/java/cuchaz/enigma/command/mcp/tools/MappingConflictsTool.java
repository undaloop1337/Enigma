package cuchaz.enigma.command.mcp.tools;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class MappingConflictsTool extends BaseTool {
	private static final Set<String> JAVA_KEYWORDS = Set.of(
			"abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
			"const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
			"finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
			"interface", "long", "native", "new", "package", "private", "protected", "public",
			"return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
			"throw", "throws", "transient", "try", "void", "volatile", "while"
	);

	public MappingConflictsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "mapping_conflicts";
	}

	@Override
	public String description() {
		return "Detect naming conflicts: duplicate target names in same scope, Java keyword collisions, case-folding collisions.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("limit", integerProperty("Maximum conflicts to report."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		int limit = limit(arguments, 50);
		JsonArray conflicts = new JsonArray();

		// Check for duplicate names within the same class scope
		Map<String, Map<String, String>> methodsByClass = new HashMap<>(); // class -> {name -> first entry}
		Map<String, Map<String, String>> fieldsByClass = new HashMap<>();

		for (MethodEntry entry : project.getJarIndex().getEntryIndex().getMethods()) {
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;

			String key = entry.getParent().getFullName() + "#" + mapping.targetName();
			String existing = methodsByClass.computeIfAbsent(entry.getParent().getFullName(), k -> new HashMap<>())
					.putIfAbsent(mapping.targetName() + entry.getDesc(), entry.toString());

			// Check keyword
			if (JAVA_KEYWORDS.contains(mapping.targetName()) && conflicts.size() < limit) {
				JsonObject c = new JsonObject();
				c.addProperty("kind", "java_keyword");
				c.addProperty("entry", entry.toString());
				c.addProperty("name", mapping.targetName());
				conflicts.add(c);
			}
		}

		for (FieldEntry entry : project.getJarIndex().getEntryIndex().getFields()) {
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;

			String scope = entry.getParent().getFullName();
			Map<String, String> scopeNames = fieldsByClass.computeIfAbsent(scope, k -> new HashMap<>());
			String existing = scopeNames.put(mapping.targetName(), entry.toString());
			if (existing != null && conflicts.size() < limit) {
				JsonObject c = new JsonObject();
				c.addProperty("kind", "duplicate_field_name");
				c.addProperty("entry1", existing);
				c.addProperty("entry2", entry.toString());
				c.addProperty("name", mapping.targetName());
				conflicts.add(c);
			}

			if (JAVA_KEYWORDS.contains(mapping.targetName()) && conflicts.size() < limit) {
				JsonObject c = new JsonObject();
				c.addProperty("kind", "java_keyword");
				c.addProperty("entry", entry.toString());
				c.addProperty("name", mapping.targetName());
				conflicts.add(c);
			}
		}

		// Check class name conflicts (same simple name in same package)
		Map<String, String> classNames = new HashMap<>();
		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;
			String key = mapping.targetName().toLowerCase(Locale.ROOT);
			String existing = classNames.put(key, entry.getFullName());
			if (existing != null && conflicts.size() < limit) {
				JsonObject c = new JsonObject();
				c.addProperty("kind", "case_folding_collision");
				c.addProperty("entry1", existing);
				c.addProperty("entry2", entry.getFullName());
				c.addProperty("name", mapping.targetName());
				conflicts.add(c);
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("conflictCount", conflicts.size());
		result.add("conflicts", conflicts);
		return result;
	}
}
