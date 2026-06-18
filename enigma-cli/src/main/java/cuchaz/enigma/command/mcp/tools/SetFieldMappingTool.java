package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;

public class SetFieldMappingTool extends BaseTool {
	public SetFieldMappingTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "set_field_mapping";
	}

	@Override
	public String description() {
		return "Set an in-memory field mapping for the open project.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Obfuscated field name."));
		properties.add("desc", stringProperty("Field descriptor."));
		properties.add("targetName", stringProperty("New deobfuscated field name, or empty to clear."));
		properties.add("javadoc", stringProperty("Optional javadoc text."));
		require(schema, "className", "name", "desc", "targetName");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		return SetClassMappingTool.setMapping(project, requireKnownField(project, arguments), arguments);
	}
}
