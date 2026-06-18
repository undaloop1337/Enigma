package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class ExceptionFlowTool extends BaseTool {
	public ExceptionFlowTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "exception_flow";
	}

	@Override
	public String description() {
		return "Map exception handling: which methods catch which exceptions, custom exception classes, error message strings.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Optional class to analyze."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 50);

		JsonArray handlers = new JsonArray();
		JsonArray customExceptions = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (handlers.size() + customExceptions.size() >= limit) break;
			if (className != null && !entry.getFullName().equals(className)) continue;

			// Check if this is a custom exception class
			for (ClassEntry ancestor : project.getJarIndex().getInheritanceIndex().getAncestors(entry)) {
				String name = ancestor.getFullName();
				if (name.equals("java/lang/Exception") || name.equals("java/lang/RuntimeException")
						|| name.equals("java/lang/Throwable") || name.equals("java/lang/Error")) {
					JsonObject item = classJson(project, entry);
					item.addProperty("extendsFrom", name);
					customExceptions.add(item);
					break;
				}
			}

			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if (method.tryCatchBlocks == null) continue;
				for (TryCatchBlockNode tcb : method.tryCatchBlocks) {
					if (handlers.size() >= limit) break;
					JsonObject item = new JsonObject();
					item.addProperty("class", entry.getFullName());
					item.addProperty("method", method.name);
					item.addProperty("methodDesc", method.desc);
					item.addProperty("exceptionType", tcb.type == null ? "finally" : tcb.type);
					handlers.add(item);
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("customExceptionCount", customExceptions.size());
		result.addProperty("handlerCount", handlers.size());
		result.add("customExceptions", customExceptions);
		result.add("handlers", handlers);
		return result;
	}
}
