package cuchaz.enigma.command.mcp.tools;

import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class DesignPatternDetectionTool extends BaseTool {
	public DesignPatternDetectionTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "design_pattern_detection";
	}

	@Override
	public String description() {
		return "Identify common design patterns: Singleton, Factory, Builder, Observer/Listener, Iterator, Comparable.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Optional class to check. If omitted, scans all classes."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 50);
		JsonArray results = new JsonArray();

		if (className != null) {
			ClassEntry entry = requireKnownClass(project, className);
			detectPatterns(entry, results);
		} else {
			for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
				if (results.size() >= limit) break;
				detectPatterns(entry, results);
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", results.size());
		result.add("patterns", results);
		return result;
	}

	private void detectPatterns(ClassEntry entry, JsonArray results) {
		ClassNode node = project.getClassProvider().get(entry.getFullName());
		if (node == null) return;

		// Singleton: private constructor + static field of own type + getInstance
		boolean hasPrivateConstructor = false;
		boolean hasStaticSelfField = false;
		boolean hasGetInstance = false;

		for (MethodNode m : node.methods) {
			if (m.name.equals("<init>") && (m.access & Opcodes.ACC_PRIVATE) != 0) hasPrivateConstructor = true;
			if ((m.access & Opcodes.ACC_STATIC) != 0 && m.desc.endsWith(")L" + node.name + ";")) hasGetInstance = true;
		}
		for (FieldNode f : node.fields) {
			if ((f.access & Opcodes.ACC_STATIC) != 0 && f.desc.equals("L" + node.name + ";")) hasStaticSelfField = true;
		}
		if (hasPrivateConstructor && hasStaticSelfField) {
			addPattern(results, entry, "Singleton", 90);
		}

		// Factory: static methods returning interface/abstract types
		int factoryMethods = 0;
		for (MethodNode m : node.methods) {
			if ((m.access & Opcodes.ACC_STATIC) != 0 && m.desc.contains(")L") && !m.name.equals("<clinit>")) {
				String returnType = m.desc.substring(m.desc.lastIndexOf(')') + 2, m.desc.length() - 1);
				if (!returnType.equals(node.name) && project.getJarIndex().getEntryIndex().hasClass(ClassEntry.parse(returnType))) {
					factoryMethods++;
				}
			}
		}
		if (factoryMethods >= 2) {
			addPattern(results, entry, "Factory", 70);
		}

		// Builder: methods returning self type (chaining)
		int chainingMethods = 0;
		for (MethodNode m : node.methods) {
			if (!m.name.equals("<init>") && m.desc.endsWith(")L" + node.name + ";")) chainingMethods++;
		}
		if (chainingMethods >= 3) {
			addPattern(results, entry, "Builder", 80);
		}

		// Listener/Observer: interface with 1-2 methods, name-like pattern
		if ((node.access & Opcodes.ACC_INTERFACE) != 0 && node.methods.size() <= 3 && node.methods.size() >= 1) {
			boolean hasEventLikeMethod = node.methods.stream()
					.anyMatch(m -> m.desc.startsWith("(L") && m.desc.endsWith(")V"));
			if (hasEventLikeMethod) {
				addPattern(results, entry, "Listener/Observer", 60);
			}
		}

		// Iterator: implements hasNext()/next() pattern
		boolean hasHasNext = false, hasNext = false;
		for (MethodNode m : node.methods) {
			if (m.name.equals("hasNext") && m.desc.equals("()Z")) hasHasNext = true;
			if (m.name.equals("next") && m.desc.equals("()Ljava/lang/Object;")) hasNext = true;
		}
		if (hasHasNext && hasNext) {
			addPattern(results, entry, "Iterator", 90);
		}
	}

	private void addPattern(JsonArray results, ClassEntry entry, String pattern, int confidence) {
		JsonObject item = classJson(project, entry);
		item.addProperty("pattern", pattern);
		item.addProperty("confidence", confidence);
		results.add(item);
	}
}
