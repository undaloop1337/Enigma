package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class EnumAnalysisTool extends BaseTool {
	public EnumAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "enum_analysis";
	}

	@Override
	public String description() {
		return "Analyze enum classes: list constants, fields, and methods.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name. If omitted, lists all enum classes."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 50);

		if (className != null) {
			ClassEntry entry = requireKnownClass(project, className);
			return analyzeEnum(entry);
		}

		// List all enum classes
		JsonArray enums = new JsonArray();
		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (enums.size() >= limit) break;

			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node != null && (node.access & Opcodes.ACC_ENUM) != 0) {
				JsonObject item = classJson(project, entry);
				long constantCount = node.fields.stream()
						.filter(f -> (f.access & Opcodes.ACC_ENUM) != 0)
						.count();
				item.addProperty("constants", constantCount);
				enums.add(item);
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("enumCount", enums.size());
		result.add("enums", enums);
		return result;
	}

	private JsonObject analyzeEnum(ClassEntry entry) throws McpException {
		ClassNode node = project.getClassProvider().get(entry.getFullName());
		if (node == null) {
			throw new McpException(INVALID_PARAMS, "Class bytes not available: " + entry.getFullName());
		}

		if ((node.access & Opcodes.ACC_ENUM) == 0) {
			throw new McpException(INVALID_PARAMS, "Not an enum class: " + entry.getFullName());
		}

		JsonObject result = classJson(project, entry);

		JsonArray constants = new JsonArray();
		JsonArray fields = new JsonArray();
		for (FieldNode field : node.fields) {
			if ((field.access & Opcodes.ACC_ENUM) != 0) {
				JsonObject item = new JsonObject();
				item.addProperty("name", field.name);
				item.addProperty("desc", field.desc);
				constants.add(item);
			} else if ((field.access & Opcodes.ACC_STATIC) == 0) {
				JsonObject item = new JsonObject();
				item.addProperty("name", field.name);
				item.addProperty("desc", field.desc);
				item.addProperty("access", field.access);
				fields.add(item);
			}
		}

		result.add("constants", constants);
		result.add("instanceFields", fields);

		JsonArray methods = new JsonArray();
		project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry))
				.filter(m -> !m.getName().equals("<init>") && !m.getName().equals("<clinit>")
						&& !m.getName().equals("values") && !m.getName().equals("valueOf"))
				.forEach(m -> methods.add(methodJson(project, m)));
		result.add("methods", methods);

		return result;
	}
}
