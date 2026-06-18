package cuchaz.enigma.command.mcp.tools;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class DiffClassesTool extends BaseTool {
	public DiffClassesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "diff_classes";
	}

	@Override
	public String description() {
		return "Compare two classes structurally: field differences, method signature differences, inheritance differences.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("classA", stringProperty("First class name."));
		properties.add("classB", stringProperty("Second class name."));
		require(schema, "classA", "classB");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String classAName = getString(arguments, "classA");
		String classBName = getString(arguments, "classB");
		ClassEntry entryA = requireKnownClass(project, classAName);
		ClassEntry entryB = requireKnownClass(project, classBName);

		ClassNode nodeA = project.getClassProvider().get(entryA.getFullName());
		ClassNode nodeB = project.getClassProvider().get(entryB.getFullName());
		if (nodeA == null || nodeB == null) {
			throw new McpException(INVALID_PARAMS, "Class bytes not available");
		}

		JsonObject result = new JsonObject();
		result.add("classA", classJson(project, entryA));
		result.add("classB", classJson(project, entryB));

		// Compare methods
		Set<String> methodsA = new HashSet<>();
		Set<String> methodsB = new HashSet<>();
		for (MethodNode m : nodeA.methods) methodsA.add(m.name + m.desc);
		for (MethodNode m : nodeB.methods) methodsB.add(m.name + m.desc);

		JsonArray onlyInA = new JsonArray();
		JsonArray onlyInB = new JsonArray();
		JsonArray common = new JsonArray();

		for (String m : methodsA) {
			if (methodsB.contains(m)) common.add(m);
			else onlyInA.add(m);
		}
		for (String m : methodsB) {
			if (!methodsA.contains(m)) onlyInB.add(m);
		}

		result.add("methodsOnlyInA", onlyInA);
		result.add("methodsOnlyInB", onlyInB);
		result.add("commonMethods", common);

		// Compare fields
		Set<String> fieldsA = new HashSet<>();
		Set<String> fieldsB = new HashSet<>();
		for (FieldNode f : nodeA.fields) fieldsA.add(f.name + ":" + f.desc);
		for (FieldNode f : nodeB.fields) fieldsB.add(f.name + ":" + f.desc);

		JsonArray fieldsOnlyInA = new JsonArray();
		JsonArray fieldsOnlyInB = new JsonArray();
		for (String f : fieldsA) { if (!fieldsB.contains(f)) fieldsOnlyInA.add(f); }
		for (String f : fieldsB) { if (!fieldsA.contains(f)) fieldsOnlyInB.add(f); }

		result.add("fieldsOnlyInA", fieldsOnlyInA);
		result.add("fieldsOnlyInB", fieldsOnlyInB);

		// Compare inheritance
		result.addProperty("superA", nodeA.superName);
		result.addProperty("superB", nodeB.superName);
		result.addProperty("sameSuperclass", nodeA.superName != null && nodeA.superName.equals(nodeB.superName));

		return result;
	}
}
