package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.FieldEntry;

public class FieldAccessTool extends BaseTool {
	public FieldAccessTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "get_field_access";
	}

	@Override
	public String description() {
		return "Find all methods that read or write a specific field.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Field name."));
		properties.add("desc", stringProperty("Field descriptor."));
		properties.add("limit", integerProperty("Maximum number of accessors to return."));
		require(schema, "className", "name", "desc");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		FieldEntry entry = requireKnownField(project, arguments);
		int limit = limit(arguments, 100);

		var refs = project.getJarIndex().getReferenceIndex().getReferencesToField(entry);

		JsonArray accessors = new JsonArray();
		refs.stream()
				.sorted(Comparator.comparing(EntryReference::toString))
				.limit(limit)
				.forEach(ref -> {
					JsonObject item = new JsonObject();
					if (ref.context != null) {
						item.addProperty("method", ref.context.toString());
						item.addProperty("class", ref.context.getParent().getFullName());
						item.addProperty("deobfuscatedClass", project.getMapper().deobfuscate(ref.context.getParent()).getFullName());
					}
					item.addProperty("accessType", ref.targetType.getKind().name());
					accessors.add(item);
				});

		JsonObject result = fieldJson(project, entry);
		result.addProperty("totalAccessors", refs.size());
		result.add("accessors", accessors);
		return result;
	}
}
