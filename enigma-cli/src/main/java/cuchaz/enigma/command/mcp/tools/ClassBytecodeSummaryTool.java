package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class ClassBytecodeSummaryTool extends BaseTool {
	public ClassBytecodeSummaryTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "class_bytecode_summary";
	}

	@Override
	public String description() {
		return "Return raw ASM class metadata and method instruction counts.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		properties.add("includeMethods", booleanProperty("Include per-method instruction counts. Defaults to true."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		ClassNode node = requireClassNode(entry);
		JsonObject result = classJson(project, entry);
		result.addProperty("version", node.version);
		result.addProperty("access", node.access);
		result.addProperty("superName", node.superName);
		result.add("interfaces", strings(node.interfaces));
		result.addProperty("fieldCount", node.fields.size());
		result.addProperty("methodCount", node.methods.size());

		if (getBoolean(arguments, "includeMethods", true)) {
			JsonArray methods = new JsonArray();
			for (MethodNode method : node.methods) {
				JsonObject item = new JsonObject();
				item.addProperty("name", method.name);
				item.addProperty("desc", method.desc);
				item.addProperty("access", method.access);
				item.addProperty("instructions", method.instructions.size());
				methods.add(item);
			}
			result.add("methods", methods);
		}

		return result;
	}

	private ClassNode requireClassNode(ClassEntry entry) throws McpException {
		ClassNode node = project.getClassProvider().get(entry.getFullName());
		if (node == null) {
			throw new McpException(INVALID_PARAMS, "Class bytes not available: " + entry.getFullName());
		}
		return node;
	}
}
