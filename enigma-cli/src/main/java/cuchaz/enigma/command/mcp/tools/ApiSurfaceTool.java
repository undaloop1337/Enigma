package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ApiSurfaceTool extends BaseTool {
	public ApiSurfaceTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "api_surface";
	}

	@Override
	public String description() {
		return "Identify the public API surface: public classes/methods that are not called internally, likely intended for external use.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		int limit = limit(arguments, 50);
		JsonArray publicClasses = new JsonArray();
		JsonArray publicMethods = new JsonArray();

		// Public classes with no internal references (entry points)
		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (publicClasses.size() >= limit) break;
			int access = project.getJarIndex().getEntryIndex().getAccess(entry);
			if ((access & Opcodes.ACC_PUBLIC) == 0) continue;
			if (entry.isInnerClass()) continue;

			int refs = project.getJarIndex().getReferenceIndex().getReferencesToClass(entry).size();
			if (refs == 0) {
				publicClasses.add(classJson(project, entry));
			}
		}

		// Public methods with no internal callers
		for (MethodEntry entry : project.getJarIndex().getEntryIndex().getMethods()) {
			if (publicMethods.size() >= limit) break;
			if (entry.isConstructor()) continue;
			int access = project.getJarIndex().getEntryIndex().getAccess(entry);
			if ((access & Opcodes.ACC_PUBLIC) == 0) continue;

			int refs = project.getJarIndex().getReferenceIndex().getReferencesToMethod(entry).size();
			if (refs == 0) {
				publicMethods.add(methodJson(project, entry));
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("publicUnreferencedClasses", publicClasses.size());
		result.addProperty("publicUnreferencedMethods", publicMethods.size());
		result.add("classes", publicClasses);
		result.add("methods", publicMethods);
		return result;
	}
}
