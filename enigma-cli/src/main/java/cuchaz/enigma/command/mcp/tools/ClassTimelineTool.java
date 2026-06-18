package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ClassTimelineTool extends BaseTool {
	public ClassTimelineTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "class_timeline";
	}

	@Override
	public String description() {
		return "Order classes by likely initialization order based on static initializers, constructor dependencies, and reference structure.";
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

		// Build dependency graph based on constructor/clinit calls
		List<ClassEntry> sorted = new ArrayList<>();
		Set<ClassEntry> visited = new HashSet<>();

		// Start from classes with no dependencies (leaves)
		List<ClassEntry> allClasses = new ArrayList<>(project.getJarIndex().getEntryIndex().getClasses());
		allClasses.sort(Comparator.comparingInt((ClassEntry e) -> getDependencyCount(e)));

		for (ClassEntry entry : allClasses) {
			if (sorted.size() >= limit) break;
			if (!visited.contains(entry)) {
				visitTimeline(entry, visited, sorted, limit);
			}
		}

		JsonArray timeline = new JsonArray();
		for (int i = 0; i < Math.min(sorted.size(), limit); i++) {
			JsonObject item = classJson(project, sorted.get(i));
			item.addProperty("order", i);
			item.addProperty("dependencies", getDependencyCount(sorted.get(i)));
			timeline.add(item);
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", timeline.size());
		result.add("timeline", timeline);
		return result;
	}

	private void visitTimeline(ClassEntry entry, Set<ClassEntry> visited, List<ClassEntry> sorted, int limit) {
		if (visited.contains(entry) || sorted.size() >= limit) return;
		visited.add(entry);

		// Visit dependencies first
		for (ClassEntry parent : project.getJarIndex().getInheritanceIndex().getParents(entry)) {
			if (project.getJarIndex().getEntryIndex().hasClass(parent)) {
				visitTimeline(parent, visited, sorted, limit);
			}
		}

		sorted.add(entry);
	}

	private int getDependencyCount(ClassEntry entry) {
		int count = 0;
		for (MethodEntry method : project.getJarIndex().getEntryIndex().getMethods()) {
			if (!method.getParent().equals(entry)) continue;
			if (!method.getName().equals("<init>") && !method.getName().equals("<clinit>")) continue;
			count += project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(method).size();
		}
		return count + project.getJarIndex().getInheritanceIndex().getParents(entry).size();
	}
}
