package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class FindOverridesTool extends BaseTool {
	public FindOverridesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "find_overrides";
	}

	@Override
	public String description() {
		return "Find all methods that override a given method across the class hierarchy.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Method name."));
		properties.add("desc", stringProperty("Method descriptor."));
		properties.add("limit", integerProperty("Maximum overrides to return."));
		require(schema, "className", "name", "desc");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		MethodEntry entry = requireKnownMethod(project, arguments);
		int limit = limit(arguments, 100);

		Collection<ClassEntry> descendants = project.getJarIndex().getInheritanceIndex().getDescendants(entry.getParent());
		List<MethodEntry> overrides = new ArrayList<>();

		for (ClassEntry descendant : descendants) {
			MethodEntry override = MethodEntry.parse(descendant.getFullName(), entry.getName(), entry.getDesc().toString());
			if (project.getJarIndex().getEntryIndex().hasMethod(override)) {
				overrides.add(override);
			}
		}

		JsonArray results = new JsonArray();
		overrides.stream()
				.sorted(Comparator.comparing(MethodEntry::toString))
				.limit(limit)
				.forEach(m -> results.add(methodJson(project, m)));

		JsonObject result = methodJson(project, entry);
		result.addProperty("overrideCount", overrides.size());
		result.add("overrides", results);
		return result;
	}
}
