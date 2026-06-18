package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class CallGraphTool extends BaseTool {
	public CallGraphTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "call_graph";
	}

	@Override
	public String description() {
		return "Show the call graph for a method: callers (who calls it) and callees (what it calls).";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Method name."));
		properties.add("desc", stringProperty("Method descriptor."));
		properties.add("limit", integerProperty("Maximum entries per direction."));
		require(schema, "className", "name", "desc");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		MethodEntry entry = requireKnownMethod(project, arguments);
		int limit = limit(arguments, 50);

		// Callees: methods this method calls
		var callees = project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(entry);
		JsonArray calleesJson = new JsonArray();
		callees.stream()
				.sorted(Comparator.comparing(MethodEntry::toString))
				.limit(limit)
				.forEach(m -> calleesJson.add(methodJson(project, m)));

		// Callers: methods that reference this method
		var callerRefs = project.getJarIndex().getReferenceIndex().getReferencesToMethod(entry);
		JsonArray callersJson = new JsonArray();
		callerRefs.stream()
				.filter(ref -> ref.context != null)
				.map(ref -> MethodEntry.parse(
						ref.context.getParent().getFullName(),
						ref.context.getName(),
						ref.context.getDesc().toString()))
				.distinct()
				.sorted(Comparator.comparing(MethodEntry::toString))
				.limit(limit)
				.forEach(m -> callersJson.add(methodJson(project, m)));

		JsonObject result = methodJson(project, entry);
		result.addProperty("callerCount", callerRefs.size());
		result.addProperty("calleeCount", callees.size());
		result.add("callers", callersJson);
		result.add("callees", calleesJson);
		return result;
	}
}
