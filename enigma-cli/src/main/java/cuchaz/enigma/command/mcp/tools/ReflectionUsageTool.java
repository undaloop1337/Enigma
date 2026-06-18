package cuchaz.enigma.command.mcp.tools;

import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class ReflectionUsageTool extends BaseTool {
	public ReflectionUsageTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "reflection_usage";
	}

	@Override
	public String description() {
		return "Find all uses of reflection: Class.forName, getMethod, getDeclaredField, newInstance, Method.invoke, Proxy, MethodHandle. Resolves string targets where possible.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Optional class to scope search."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 100);
		JsonArray results = new JsonArray();

		for (ClassEntry classEntry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;
			if (className != null && !classEntry.getFullName().equals(className)) continue;

			ClassNode node = project.getClassProvider().get(classEntry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if (results.size() >= limit) break;

				String lastLdc = null;
				for (AbstractInsnNode insn : method.instructions) {
					if (results.size() >= limit) break;

					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
						lastLdc = s;
					}

					if (insn instanceof MethodInsnNode min) {
						String kind = getReflectionKind(min);
						if (kind != null) {
							JsonObject item = new JsonObject();
							item.addProperty("class", classEntry.getFullName());
							item.addProperty("method", method.name);
							item.addProperty("methodDesc", method.desc);
							item.addProperty("reflectionKind", kind);
							item.addProperty("api", min.owner + "." + min.name);
							if (lastLdc != null) {
								item.addProperty("resolvedTarget", lastLdc);
							}
							results.add(item);
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", results.size());
		result.add("usages", results);
		return result;
	}

	private static String getReflectionKind(MethodInsnNode min) {
		String owner = min.owner;
		String name = min.name;
		if (owner.equals("java/lang/Class")) {
			if (name.equals("forName")) return "class_lookup";
			if (name.equals("getMethod") || name.equals("getDeclaredMethod")) return "method_lookup";
			if (name.equals("getField") || name.equals("getDeclaredField")) return "field_lookup";
			if (name.equals("newInstance")) return "instantiation";
			if (name.equals("getConstructor") || name.equals("getDeclaredConstructor")) return "constructor_lookup";
		}
		if (owner.equals("java/lang/reflect/Method") && name.equals("invoke")) return "method_invoke";
		if (owner.equals("java/lang/reflect/Field") && (name.equals("get") || name.equals("set"))) return "field_access";
		if (owner.equals("java/lang/reflect/Constructor") && name.equals("newInstance")) return "constructor_invoke";
		if (owner.equals("java/lang/reflect/Proxy") && name.equals("newProxyInstance")) return "proxy_creation";
		if (owner.startsWith("java/lang/invoke/MethodHandle")) return "method_handle";
		if (owner.equals("java/lang/invoke/MethodHandles$Lookup")) return "method_handle_lookup";
		return null;
	}
}
