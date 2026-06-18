package cuchaz.enigma.command.mcp.tools;

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

public class ResourceReferencesTool extends BaseTool {
	public ResourceReferencesTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "resource_references";
	}

	@Override
	public String description() {
		return "Find references to resources: getResource, getResourceAsStream, file path constants, asset loading patterns.";
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
		int limit = limit(arguments, 100);
		JsonArray results = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;
			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if (results.size() >= limit) break;
				String lastString = null;

				for (AbstractInsnNode insn : method.instructions) {
					if (results.size() >= limit) break;

					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
						lastString = s;
						// Detect file path or resource patterns
						if (isResourcePath(s)) {
							JsonObject item = new JsonObject();
							item.addProperty("class", entry.getFullName());
							item.addProperty("method", method.name);
							item.addProperty("resource", s);
							item.addProperty("kind", "path_constant");
							results.add(item);
						}
					}

					if (insn instanceof MethodInsnNode min) {
						if (isResourceLoadMethod(min)) {
							JsonObject item = new JsonObject();
							item.addProperty("class", entry.getFullName());
							item.addProperty("method", method.name);
							item.addProperty("api", min.owner + "." + min.name);
							item.addProperty("resource", lastString);
							item.addProperty("kind", "api_call");
							results.add(item);
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", results.size());
		result.add("references", results);
		return result;
	}

	private static boolean isResourcePath(String s) {
		if (s.length() < 3 || s.length() > 200) return false;
		return s.contains("/") && (s.endsWith(".png") || s.endsWith(".json") || s.endsWith(".xml")
				|| s.endsWith(".properties") || s.endsWith(".txt") || s.endsWith(".yml")
				|| s.endsWith(".cfg") || s.endsWith(".class") || s.endsWith(".dat")
				|| s.endsWith(".nbt") || s.endsWith(".ogg") || s.endsWith(".wav")
				|| s.endsWith(".frag") || s.endsWith(".vert") || s.endsWith(".glsl"));
	}

	private static boolean isResourceLoadMethod(MethodInsnNode min) {
		return (min.name.equals("getResource") || min.name.equals("getResourceAsStream"))
				|| (min.owner.equals("java/lang/ClassLoader") && min.name.equals("getResource"))
				|| (min.owner.equals("java/nio/file/Paths") && min.name.equals("get"))
				|| (min.owner.equals("java/io/File") && min.name.equals("<init>"));
	}
}
