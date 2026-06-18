package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ReferencesTool extends BaseTool {
	public ReferencesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "get_references";
	}

	@Override
	public String description() {
		return "Find references to a class, method, or field.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("type", stringProperty("class, method, or field."));
		properties.add("className", stringProperty("Class name or owner."));
		properties.add("name", stringProperty("Method or field name."));
		properties.add("desc", stringProperty("Method or field descriptor."));
		properties.add("limit", integerProperty("Maximum number of references to return."));
		require(schema, "type", "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String type = getString(arguments, "type");
		int limit = limit(arguments, 100);
		JsonArray references = new JsonArray();

		if ("class".equals(type)) {
			ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
			addReferences(references, project.getJarIndex().getReferenceIndex().getReferencesToClass(entry), limit);
		} else if ("method".equals(type)) {
			MethodEntry entry = requireKnownMethod(project, arguments);
			addReferences(references, project.getJarIndex().getReferenceIndex().getReferencesToMethod(entry), limit);
		} else if ("field".equals(type)) {
			FieldEntry entry = requireKnownField(project, arguments);
			addReferences(references, project.getJarIndex().getReferenceIndex().getReferencesToField(entry), limit);
		} else {
			throw new McpException(INVALID_PARAMS, "Unknown reference type: " + type);
		}

		JsonObject result = new JsonObject();
		result.add("references", references);
		return result;
	}
}
