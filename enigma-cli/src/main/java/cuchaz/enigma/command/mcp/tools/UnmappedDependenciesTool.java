package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class UnmappedDependenciesTool extends BaseTool {
	public UnmappedDependenciesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "unmapped_dependencies";
	}

	@Override
	public String description() {
		return "Given a mapped class, find all its dependencies that remain unmapped. Helps expand the mapping frontier.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("A mapped class to find unmapped dependencies for."));
		properties.add("limit", integerProperty("Maximum unmapped deps to return."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		int limit = limit(arguments, 50);

		Set<ClassEntry> deps = new HashSet<>();

		// From method calls
		for (MethodEntry method : project.getJarIndex().getEntryIndex().getMethods()) {
			if (!method.getParent().equals(entry)) continue;
			for (MethodEntry called : project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(method)) {
				ClassEntry target = called.getParent();
				if (!target.equals(entry) && project.getJarIndex().getEntryIndex().hasClass(target)) {
					deps.add(target);
				}
			}
		}

		// From field types
		for (FieldEntry field : project.getJarIndex().getEntryIndex().getFields()) {
			if (!field.getParent().equals(entry)) continue;
			String desc = field.getDesc().toString();
			if (desc.startsWith("L") && desc.endsWith(";")) {
				String typeName = desc.substring(1, desc.length() - 1);
				ClassEntry typeEntry = ClassEntry.parse(typeName);
				if (project.getJarIndex().getEntryIndex().hasClass(typeEntry)) {
					deps.add(typeEntry);
				}
			}
		}

		// From inheritance
		deps.addAll(project.getJarIndex().getInheritanceIndex().getParents(entry));

		// Filter to unmapped only
		JsonArray unmapped = new JsonArray();
		deps.stream()
				.filter(d -> project.getMapper().getDeobfMapping(d).targetName() == null)
				.sorted(Comparator.comparingInt((ClassEntry d) ->
						project.getJarIndex().getReferenceIndex().getReferencesToClass(d).size()).reversed())
				.limit(limit)
				.forEach(d -> {
					JsonObject item = classJson(project, d);
					item.addProperty("references", project.getJarIndex().getReferenceIndex().getReferencesToClass(d).size());
					unmapped.add(item);
				});

		JsonObject result = classJson(project, entry);
		result.addProperty("unmappedCount", unmapped.size());
		result.add("unmappedDependencies", unmapped);
		return result;
	}
}
