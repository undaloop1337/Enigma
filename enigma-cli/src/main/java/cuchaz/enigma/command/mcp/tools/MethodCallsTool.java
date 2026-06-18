package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class MethodCallsTool extends BaseTool {
	public MethodCallsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "get_method_calls";
	}

	@Override
	public String description() {
		return "List methods directly called by a method.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Method name."));
		properties.add("desc", stringProperty("Method descriptor."));
		properties.add("limit", integerProperty("Maximum number of calls to return."));
		require(schema, "className", "name", "desc");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		MethodEntry entry = requireKnownMethod(project, arguments);
		JsonArray calls = new JsonArray();
		project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(entry).stream()
				.sorted(Comparator.comparing(MethodEntry::toString))
				.limit(limit(arguments, 100))
				.forEach(method -> calls.add(methodJson(project, method)));

		JsonObject result = methodJson(project, entry);
		result.add("calls", calls);
		return result;
	}
}
