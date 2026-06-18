package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class EntryPointsTool extends BaseTool {
	public EntryPointsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "entry_points";
	}

	@Override
	public String description() {
		return "Find likely static entry point and lifecycle methods.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		schema.getAsJsonObject("properties").add("limit", integerProperty("Maximum methods to return."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		JsonArray methods = new JsonArray();
		int limit = limit(arguments, 100);

		project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(this::isEntryPointLike)
				.sorted(Comparator.comparing(MethodEntry::toString))
				.limit(limit)
				.forEach(method -> methods.add(methodJson(project, method)));

		JsonObject result = new JsonObject();
		result.add("methods", methods);
		return result;
	}

	private boolean isEntryPointLike(MethodEntry entry) {
		String name = entry.getName();
		String desc = entry.getDesc().toString();
		return name.equals("main") && desc.equals("([Ljava/lang/String;)V")
				|| name.equals("premain")
				|| name.equals("agentmain")
				|| name.equals("onInitialize")
				|| name.equals("init")
				|| name.equals("start")
				|| name.equals("run");
	}
}
