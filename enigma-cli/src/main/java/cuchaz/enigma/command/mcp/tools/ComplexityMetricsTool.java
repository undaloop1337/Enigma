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

public class ComplexityMetricsTool extends BaseTool {
	public ComplexityMetricsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "complexity_metrics";
	}

	@Override
	public String description() {
		return "Compute complexity metrics for a class or package: method count, field count, reference density, inheritance depth, coupling.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Class to analyze. If omitted, returns top complex classes."));
		properties.add("limit", integerProperty("Maximum results when listing."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 30);

		if (className != null) {
			ClassEntry entry = requireKnownClass(project, className);
			return metricsForClass(entry);
		}

		// Return top complex classes
		JsonArray classes = new JsonArray();
		project.getJarIndex().getEntryIndex().getClasses().stream()
				.map(this::metricsForClass)
				.sorted((a, b) -> b.get("complexity").getAsInt() - a.get("complexity").getAsInt())
				.limit(limit)
				.forEach(classes::add);

		JsonObject result = new JsonObject();
		result.add("classes", classes);
		return result;
	}

	private JsonObject metricsForClass(ClassEntry entry) {
		JsonObject item = classJson(project, entry);

		long methodCount = project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry)).count();
		long fieldCount = project.getJarIndex().getEntryIndex().getFields().stream()
				.filter(f -> f.getParent().equals(entry)).count();
		int incomingRefs = project.getJarIndex().getReferenceIndex().getReferencesToClass(entry).size();
		int parents = project.getJarIndex().getInheritanceIndex().getAncestors(entry).size();
		int children = project.getJarIndex().getInheritanceIndex().getDescendants(entry).size();

		// Outgoing coupling: unique classes referenced
		long outgoingCoupling = project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry))
				.flatMap(m -> project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(m).stream())
				.map(m -> m.getParent().getFullName())
				.distinct()
				.filter(n -> !n.equals(entry.getFullName()))
				.count();

		int complexity = (int) (methodCount * 2 + fieldCount + incomingRefs + outgoingCoupling + parents + children);

		item.addProperty("methods", methodCount);
		item.addProperty("fields", fieldCount);
		item.addProperty("incomingReferences", incomingRefs);
		item.addProperty("outgoingCoupling", outgoingCoupling);
		item.addProperty("inheritanceDepth", parents);
		item.addProperty("descendants", children);
		item.addProperty("complexity", complexity);
		return item;
	}
}
