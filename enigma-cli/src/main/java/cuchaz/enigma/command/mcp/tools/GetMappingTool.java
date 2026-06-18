package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.Entry;

public class GetMappingTool extends BaseTool {
	public GetMappingTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "get_mapping";
	}

	@Override
	public String description() {
		return "Get the current mapping for a class, method, or field entry.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("type", stringProperty("class, method, or field."));
		properties.add("className", stringProperty("Class name or owner."));
		properties.add("name", stringProperty("Method or field name."));
		properties.add("desc", stringProperty("Method or field descriptor."));
		require(schema, "type", "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		Entry<?> entry = requireEntry(project, arguments);
		return mappingResult(project, entry, project.getMapper().getDeobfMapping(entry));
	}
}
