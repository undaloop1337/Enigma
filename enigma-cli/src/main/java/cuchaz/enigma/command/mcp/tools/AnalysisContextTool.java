package cuchaz.enigma.command.mcp.tools;

import java.util.Collection;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class AnalysisContextTool extends BaseTool {
	public AnalysisContextTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "analysis_context";
	}

	@Override
	public String description() {
		return "Return a comprehensive context bundle for a class: references, inheritance, mapping status of neighbors, strings, and relationships. Reduces multiple tool calls to one.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		JsonObject result = classJson(project, entry);

		// Mapping status
		EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
		result.addProperty("mapped", mapping.targetName() != null);
		result.addProperty("targetName", mapping.targetName());

		// Inheritance
		Collection<ClassEntry> parents = project.getJarIndex().getInheritanceIndex().getParents(entry);
		Collection<ClassEntry> children = project.getJarIndex().getInheritanceIndex().getChildren(entry);
		result.add("parents", classArray(project, parents, 20));
		result.add("children", classArray(project, children, 20));

		// References
		int incomingRefs = project.getJarIndex().getReferenceIndex().getReferencesToClass(entry).size();
		result.addProperty("incomingReferences", incomingRefs);

		// Methods summary
		JsonArray methods = new JsonArray();
		project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry))
				.limit(30)
				.forEach(m -> {
					JsonObject mi = new JsonObject();
					mi.addProperty("name", m.getName());
					mi.addProperty("desc", m.getDesc().toString());
					EntryMapping mm = project.getMapper().getDeobfMapping(m);
					mi.addProperty("mapped", mm.targetName() != null);
					mi.addProperty("targetName", mm.targetName());
					mi.addProperty("references", project.getJarIndex().getReferenceIndex().getReferencesToMethod(m).size());
					methods.add(mi);
				});
		result.add("methods", methods);

		// Fields summary
		JsonArray fields = new JsonArray();
		project.getJarIndex().getEntryIndex().getFields().stream()
				.filter(f -> f.getParent().equals(entry))
				.limit(30)
				.forEach(f -> {
					JsonObject fi = new JsonObject();
					fi.addProperty("name", f.getName());
					fi.addProperty("desc", f.getDesc().toString());
					EntryMapping fm = project.getMapper().getDeobfMapping(f);
					fi.addProperty("mapped", fm.targetName() != null);
					fi.addProperty("targetName", fm.targetName());
					fields.add(fi);
				});
		result.add("fields", fields);

		// Neighbor mapping coverage
		long mappedNeighbors = parents.stream()
				.filter(p -> project.getMapper().getDeobfMapping(p).targetName() != null).count();
		long totalNeighbors = parents.size() + children.size();
		result.addProperty("neighborMappingCoverage", totalNeighbors == 0 ? 0 : (double) mappedNeighbors / totalNeighbors);

		return result;
	}
}
