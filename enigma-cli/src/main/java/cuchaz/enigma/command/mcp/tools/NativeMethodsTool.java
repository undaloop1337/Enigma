package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class NativeMethodsTool extends BaseTool {
	public NativeMethodsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "native_methods";
	}

	@Override
	public String description() {
		return "Find all native method declarations and related System.loadLibrary calls.";
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
		JsonArray nativeMethods = new JsonArray();
		JsonArray libraryLoads = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (nativeMethods.size() + libraryLoads.size() >= limit) break;
			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if ((method.access & Opcodes.ACC_NATIVE) != 0) {
					JsonObject item = new JsonObject();
					item.addProperty("class", entry.getFullName());
					item.addProperty("method", method.name);
					item.addProperty("desc", method.desc);
					nativeMethods.add(item);
				}

				// Find System.loadLibrary calls
				if (method.instructions == null) continue;
				String lastString = null;
				for (AbstractInsnNode insn : method.instructions) {
					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
						lastString = s;
					}
					if (insn instanceof MethodInsnNode min) {
						if (min.owner.equals("java/lang/System") && (min.name.equals("loadLibrary") || min.name.equals("load"))) {
							JsonObject item = new JsonObject();
							item.addProperty("class", entry.getFullName());
							item.addProperty("method", method.name);
							item.addProperty("library", lastString);
							libraryLoads.add(item);
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("nativeMethodCount", nativeMethods.size());
		result.addProperty("libraryLoadCount", libraryLoads.size());
		result.add("nativeMethods", nativeMethods);
		result.add("libraryLoads", libraryLoads);
		return result;
	}
}
