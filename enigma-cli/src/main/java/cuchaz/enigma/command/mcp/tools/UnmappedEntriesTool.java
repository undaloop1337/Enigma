package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class UnmappedEntriesTool extends BaseTool {
	public UnmappedEntriesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "unmapped_entries";
	}

	@Override
	public String description() {
		return "Find unmapped classes, methods, or fields, prioritized by reference count.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("kind", stringProperty("class, method, field, or all. Defaults to all."));
		properties.add("packageFilter", stringProperty("Optional package prefix filter."));
		properties.add("limit", integerProperty("Maximum entries per category."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		String kind = getString(arguments, "kind", "all");
		String packageFilter = getString(arguments, "packageFilter");
		int limit = limit(arguments, 30);
		JsonObject result = new JsonObject();

		if (kind.equals("all") || kind.equals("class")) {
			JsonArray classes = new JsonArray();
			project.getJarIndex().getEntryIndex().getClasses().stream()
					.filter(entry -> packageFilter == null || entry.getFullName().startsWith(packageFilter))
					.filter(entry -> project.getMapper().getDeobfMapping(entry).targetName() == null)
					.sorted(Comparator.comparingInt((ClassEntry e) -> project.getJarIndex().getReferenceIndex().getReferencesToClass(e).size()).reversed())
					.limit(limit)
					.forEach(entry -> {
						JsonObject item = classJson(project, entry);
						item.addProperty("references", project.getJarIndex().getReferenceIndex().getReferencesToClass(entry).size());
						classes.add(item);
					});
			result.add("unmappedClasses", classes);
		}

		if (kind.equals("all") || kind.equals("method")) {
			JsonArray methods = new JsonArray();
			project.getJarIndex().getEntryIndex().getMethods().stream()
					.filter(entry -> !entry.isConstructor())
					.filter(entry -> packageFilter == null || entry.getParent().getFullName().startsWith(packageFilter))
					.filter(entry -> project.getMapper().getDeobfMapping(entry).targetName() == null)
					.sorted(Comparator.comparingInt((MethodEntry e) -> project.getJarIndex().getReferenceIndex().getReferencesToMethod(e).size()).reversed())
					.limit(limit)
					.forEach(entry -> {
						JsonObject item = methodJson(project, entry);
						item.addProperty("references", project.getJarIndex().getReferenceIndex().getReferencesToMethod(entry).size());
						methods.add(item);
					});
			result.add("unmappedMethods", methods);
		}

		if (kind.equals("all") || kind.equals("field")) {
			JsonArray fields = new JsonArray();
			project.getJarIndex().getEntryIndex().getFields().stream()
					.filter(entry -> packageFilter == null || entry.getParent().getFullName().startsWith(packageFilter))
					.filter(entry -> project.getMapper().getDeobfMapping(entry).targetName() == null)
					.sorted(Comparator.comparingInt((FieldEntry e) -> project.getJarIndex().getReferenceIndex().getReferencesToField(e).size()).reversed())
					.limit(limit)
					.forEach(entry -> {
						JsonObject item = fieldJson(project, entry);
						item.addProperty("references", project.getJarIndex().getReferenceIndex().getReferencesToField(entry).size());
						fields.add(item);
					});
			result.add("unmappedFields", fields);
		}

		return result;
	}
}
