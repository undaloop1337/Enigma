package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.validation.ValidationContext;

public class ValidateMappingsTool extends BaseTool {
	public ValidateMappingsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "validate_mappings";
	}

	@Override
	public String description() {
		return "Validate all current mappings for conflicts and errors.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("packageFilter", stringProperty("Optional package prefix to scope validation."));
		properties.add("limit", integerProperty("Maximum number of issues to report."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		String packageFilter = getString(arguments, "packageFilter");
		int limit = limit(arguments, 100);

		JsonArray issues = new JsonArray();
		int checked = 0;

		for (Entry<?> entry : project.getMapper().getObfEntries().toList()) {
			if (issues.size() >= limit) break;

			if (packageFilter != null) {
				String className = entry instanceof ClassEntry ce ? ce.getFullName() : entry.getContainingClass().getFullName();
				if (!className.startsWith(packageFilter)) continue;
			}

			EntryMapping mapping = project.getMapper().getDeobfMapping(entry);
			if (mapping.targetName() == null) continue;

			checked++;
			ValidationContext vc = new ValidationContext();
			project.getMapper().validatePutMapping(vc, entry, mapping);

			if (!vc.canProceed()) {
				JsonObject issue = new JsonObject();
				issue.addProperty("entry", entry.toString());
				issue.addProperty("targetName", mapping.targetName());
				issue.add("messages", messages(vc));
				issues.add(issue);
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("checkedEntries", checked);
		result.addProperty("issueCount", issues.size());
		result.add("issues", issues);
		return result;
	}
}
