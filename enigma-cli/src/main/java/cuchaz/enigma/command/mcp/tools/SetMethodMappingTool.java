package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;

public class SetMethodMappingTool extends BaseTool {
	public SetMethodMappingTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "set_method_mapping";
	}

	@Override
	public String description() {
		return "Set an in-memory method mapping for the open project.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Obfuscated method name."));
		properties.add("desc", stringProperty("Method descriptor."));
		properties.add("targetName", stringProperty("New deobfuscated method name, or empty to clear."));
		properties.add("javadoc", stringProperty("Optional javadoc text."));
		require(schema, "className", "name", "desc", "targetName");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		return SetClassMappingTool.setMapping(project, requireKnownMethod(project, arguments), arguments);
	}
}
