package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class PluginSystemDetectionTool extends BaseTool {
	public PluginSystemDetectionTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "plugin_system_detection";
	}

	@Override
	public String description() {
		return "Find plugin/extension loading mechanisms: ServiceLoader, custom ClassLoader, annotation-based registration.";
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
		JsonArray serviceLoaders = new JsonArray();
		JsonArray classLoaders = new JsonArray();
		JsonArray pluginInterfaces = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (serviceLoaders.size() + classLoaders.size() + pluginInterfaces.size() >= limit) break;

			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			// Custom ClassLoader
			if (node.superName != null && (node.superName.equals("java/lang/ClassLoader")
					|| node.superName.equals("java/net/URLClassLoader")
					|| node.superName.contains("ClassLoader"))) {
				JsonObject item = classJson(project, entry);
				item.addProperty("kind", "custom_classloader");
				item.addProperty("extends", node.superName);
				classLoaders.add(item);
			}

			// ServiceLoader usage
			for (MethodNode method : node.methods) {
				for (AbstractInsnNode insn : method.instructions) {
					if (insn instanceof MethodInsnNode min) {
						if (min.owner.equals("java/util/ServiceLoader") && min.name.equals("load")) {
							JsonObject item = new JsonObject();
							item.addProperty("class", entry.getFullName());
							item.addProperty("method", method.name);
							item.addProperty("kind", "service_loader");
							serviceLoaders.add(item);
							break;
						}
					}
				}
			}

			// Plugin-like interfaces: interfaces with methods like getName, getVersion, onEnable
			if ((node.access & 0x0200) != 0) { // interface
				boolean hasLifecycle = false;
				for (MethodNode m : node.methods) {
					if (m.name.equals("getName") || m.name.equals("getVersion") || m.name.equals("getDescription")
							|| m.name.equals("onEnable") || m.name.equals("onDisable") || m.name.equals("onLoad")
							|| m.name.equals("initialize") || m.name.equals("start") || m.name.equals("stop")) {
						hasLifecycle = true;
						break;
					}
				}
				if (hasLifecycle && node.methods.size() >= 2) {
					JsonObject item = classJson(project, entry);
					item.addProperty("kind", "plugin_interface");
					item.addProperty("methods", node.methods.size());
					pluginInterfaces.add(item);
				}
			}
		}

		JsonObject result = new JsonObject();
		result.add("serviceLoaders", serviceLoaders);
		result.add("classLoaders", classLoaders);
		result.add("pluginInterfaces", pluginInterfaces);
		return result;
	}
}
