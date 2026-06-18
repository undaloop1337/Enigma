package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class MappingStatsTool extends BaseTool {
	public MappingStatsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "mapping_stats";
	}

	@Override
	public String description() {
		return "Show mapping progress: how many classes, methods, and fields are mapped vs unmapped.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("packageFilter", stringProperty("Optional package prefix filter."));
		properties.add("showUnmapped", booleanProperty("Include list of unmapped entries. Defaults to false."));
		properties.add("limit", integerProperty("Maximum unmapped entries to show per category."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		String packageFilter = getString(arguments, "packageFilter");
		boolean showUnmapped = getBoolean(arguments, "showUnmapped", false);
		int limit = limit(arguments, 50);

		int totalClasses = 0, mappedClasses = 0;
		int totalMethods = 0, mappedMethods = 0;
		int totalFields = 0, mappedFields = 0;

		JsonArray unmappedClasses = new JsonArray();
		JsonArray unmappedMethods = new JsonArray();
		JsonArray unmappedFields = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (packageFilter != null && !entry.getFullName().startsWith(packageFilter)) {
				continue;
			}
			totalClasses++;
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() != null) {
				mappedClasses++;
			} else if (showUnmapped && unmappedClasses.size() < limit) {
				unmappedClasses.add(entry.getFullName());
			}
		}

		for (MethodEntry entry : project.getJarIndex().getEntryIndex().getMethods()) {
			if (packageFilter != null && !entry.getParent().getFullName().startsWith(packageFilter)) {
				continue;
			}
			if (entry.isConstructor()) {
				continue;
			}
			totalMethods++;
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() != null) {
				mappedMethods++;
			} else if (showUnmapped && unmappedMethods.size() < limit) {
				unmappedMethods.add(entry.toString());
			}
		}

		for (FieldEntry entry : project.getJarIndex().getEntryIndex().getFields()) {
			if (packageFilter != null && !entry.getParent().getFullName().startsWith(packageFilter)) {
				continue;
			}
			totalFields++;
			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() != null) {
				mappedFields++;
			} else if (showUnmapped && unmappedFields.size() < limit) {
				unmappedFields.add(entry.toString());
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("totalClasses", totalClasses);
		result.addProperty("mappedClasses", mappedClasses);
		result.addProperty("totalMethods", totalMethods);
		result.addProperty("mappedMethods", mappedMethods);
		result.addProperty("totalFields", totalFields);
		result.addProperty("mappedFields", mappedFields);
		result.addProperty("overallProgress",
				totalClasses + totalMethods + totalFields == 0 ? 0.0
						: (double) (mappedClasses + mappedMethods + mappedFields) / (totalClasses + totalMethods + totalFields));

		if (showUnmapped) {
			result.add("unmappedClasses", unmappedClasses);
			result.add("unmappedMethods", unmappedMethods);
			result.add("unmappedFields", unmappedFields);
		}

		return result;
	}
}
