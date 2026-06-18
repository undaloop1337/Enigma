package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.validation.ValidationContext;

public class BatchSetMappingsTool extends BaseTool {
	public BatchSetMappingsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "batch_set_mappings";
	}

	@Override
	public String description() {
		return "Set multiple mappings at once. Each entry specifies type, identifiers, and targetName.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");

		JsonObject mappingsProperty = new JsonObject();
		mappingsProperty.addProperty("type", "array");
		mappingsProperty.addProperty("description", "Array of mapping objects. Each has: type (class/method/field), className, name (for method/field), desc (for method/field), targetName, javadoc (optional).");
		properties.add("mappings", mappingsProperty);

		require(schema, "mappings");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		JsonArray mappings = arguments.getAsJsonArray("mappings");
		if (mappings == null || mappings.isEmpty()) {
			throw new McpException(INVALID_PARAMS, "mappings array is required and must not be empty");
		}

		int success = 0;
		int failed = 0;
		JsonArray results = new JsonArray();

		for (JsonElement element : mappings) {
			JsonObject mapping = element.getAsJsonObject();
			String type = getString(mapping, "type");
			String targetName = getString(mapping, "targetName");
			String javadoc = getString(mapping, "javadoc");

			if (targetName != null && targetName.isEmpty()) {
				targetName = null;
			}

			try {
				Entry<?> entry;
				if ("class".equals(type)) {
					entry = requireKnownClass(project, getString(mapping, "className"));
				} else if ("method".equals(type)) {
					entry = requireKnownMethod(project, mapping);
				} else if ("field".equals(type)) {
					entry = requireKnownField(project, mapping);
				} else {
					JsonObject item = new JsonObject();
					item.addProperty("success", false);
					item.addProperty("error", "Unknown type: " + type);
					results.add(item);
					failed++;
					continue;
				}

				ValidationContext vc = new ValidationContext();
				EntryMapping entryMapping = new EntryMapping(targetName, javadoc);
				project.getMapper().putMapping(vc, entry, entryMapping);

				JsonObject item = new JsonObject();
				item.addProperty("success", vc.canProceed());
				item.addProperty("entry", entry.toString());
				item.addProperty("targetName", targetName);
				if (!vc.canProceed()) {
					item.add("messages", messages(vc));
					failed++;
				} else {
					success++;
				}
				results.add(item);
			} catch (McpException e) {
				JsonObject item = new JsonObject();
				item.addProperty("success", false);
				item.addProperty("error", e.getMessage());
				results.add(item);
				failed++;
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("total", mappings.size());
		result.addProperty("success", success);
		result.addProperty("failed", failed);
		result.add("results", results);
		return result;
	}
}
