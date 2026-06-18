package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class DeadCodeTool extends BaseTool {
	public DeadCodeTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "dead_code";
	}

	@Override
	public String description() {
		return "Find unreferenced classes and methods (potential dead code).";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("kind", stringProperty("class, method, or all. Defaults to all."));
		properties.add("packageFilter", stringProperty("Optional package prefix filter."));
		properties.add("limit", integerProperty("Maximum entries per category."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		String kind = getString(arguments, "kind", "all");
		String packageFilter = getString(arguments, "packageFilter");
		int limit = limit(arguments, 50);
		JsonObject result = new JsonObject();

		if (kind.equals("all") || kind.equals("class")) {
			JsonArray deadClasses = new JsonArray();
			for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
				if (deadClasses.size() >= limit) break;
				if (packageFilter != null && !entry.getFullName().startsWith(packageFilter)) continue;
				if (entry.isInnerClass()) continue;

				int refs = project.getJarIndex().getReferenceIndex().getReferencesToClass(entry).size();
				int children = project.getJarIndex().getInheritanceIndex().getChildren(entry).size();
				if (refs == 0 && children == 0) {
					deadClasses.add(classJson(project, entry));
				}
			}
			result.add("deadClasses", deadClasses);
			result.addProperty("deadClassCount", deadClasses.size());
		}

		if (kind.equals("all") || kind.equals("method")) {
			JsonArray deadMethods = new JsonArray();
			for (MethodEntry entry : project.getJarIndex().getEntryIndex().getMethods()) {
				if (deadMethods.size() >= limit) break;
				if (packageFilter != null && !entry.getParent().getFullName().startsWith(packageFilter)) continue;
				if (entry.isConstructor() || entry.getName().equals("<clinit>")) continue;
				if (entry.getName().equals("main")) continue;

				int refs = project.getJarIndex().getReferenceIndex().getReferencesToMethod(entry).size();
				if (refs == 0) {
					deadMethods.add(methodJson(project, entry));
				}
			}
			result.add("deadMethods", deadMethods);
			result.addProperty("deadMethodCount", deadMethods.size());
		}

		return result;
	}
}
