package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.utils.validation.ValidationContext;

public class SetClassMappingTool extends BaseTool {
	public SetClassMappingTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "set_class_mapping";
	}

	@Override
	public String description() {
		return "Set an in-memory class mapping for the open project.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		properties.add("targetName", stringProperty("New deobfuscated class name, or empty to clear."));
		properties.add("javadoc", stringProperty("Optional javadoc text."));
		require(schema, "className", "targetName");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		return setMapping(project, entry, arguments);
	}

	protected static JsonObject setMapping(EnigmaProject project, Entry<?> entry, JsonObject arguments) {
		String targetName = getString(arguments, "targetName");
		if (targetName != null && targetName.isEmpty()) {
			targetName = null;
		}
		ValidationContext vc = new ValidationContext();
		EntryMapping mapping = new EntryMapping(targetName, getString(arguments, "javadoc"));
		boolean changed = project.getMapper().putMapping(vc, entry, mapping);
		JsonObject result = mappingResult(project, entry, project.getMapper().getDeobfMapping(entry));
		result.addProperty("success", vc.canProceed());
		result.addProperty("changed", changed);
		result.add("messages", messages(vc));
		return result;
	}
}
