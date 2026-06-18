package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ClassDependenciesTool extends BaseTool {
	public ClassDependenciesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "class_dependencies";
	}

	@Override
	public String description() {
		return "Show all classes that a given class depends on (via method calls, field access, inheritance).";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Obfuscated JVM class name."));
		properties.add("limit", integerProperty("Maximum number of dependencies to return."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		int limit = limit(arguments, 200);
		Set<ClassEntry> dependencies = new HashSet<>();

		// Inheritance dependencies
		dependencies.addAll(project.getJarIndex().getInheritanceIndex().getParents(entry));

		// Method call dependencies
		for (MethodEntry method : project.getJarIndex().getEntryIndex().getMethods()) {
			if (method.getParent().equals(entry)) {
				for (MethodEntry called : project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(method)) {
					dependencies.add(called.getParent());
				}
			}
		}

		// Field type dependencies (fields owned by this class referencing other classes)
		for (FieldEntry field : project.getJarIndex().getEntryIndex().getFields()) {
			if (field.getParent().equals(entry)) {
				String desc = field.getDesc().toString();
				if (desc.startsWith("L") && desc.endsWith(";")) {
					String depName = desc.substring(1, desc.length() - 1);
					ClassEntry dep = ClassEntry.parse(depName);
					if (project.getJarIndex().getEntryIndex().hasClass(dep)) {
						dependencies.add(dep);
					}
				}
			}
		}

		// Remove self
		dependencies.remove(entry);

		JsonArray deps = new JsonArray();
		dependencies.stream()
				.sorted(Comparator.comparing(ClassEntry::getFullName))
				.limit(limit)
				.forEach(dep -> deps.add(classJson(project, dep)));

		JsonObject result = classJson(project, entry);
		result.addProperty("dependencyCount", dependencies.size());
		result.add("dependencies", deps);
		return result;
	}
}
