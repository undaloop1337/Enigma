package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class EventSystemAnalysisTool extends BaseTool {
	public EventSystemAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "event_system_analysis";
	}

	@Override
	public String description() {
		return "Detect event/listener architectures: functional interfaces used as listeners, registration patterns, event classes.";
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
		JsonArray listeners = new JsonArray();
		JsonArray eventClasses = new JsonArray();
		JsonArray registrars = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (listeners.size() + eventClasses.size() + registrars.size() >= limit) break;
			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			// Listener interface: interface with 1-2 void methods taking a single object param
			if ((node.access & Opcodes.ACC_INTERFACE) != 0) {
				long voidEventMethods = node.methods.stream()
						.filter(m -> (m.access & Opcodes.ACC_ABSTRACT) != 0)
						.filter(m -> m.desc.matches("\\(L[^;]+;\\)V"))
						.count();
				if (voidEventMethods >= 1 && node.methods.size() <= 3) {
					JsonObject item = classJson(project, entry);
					item.addProperty("kind", "listener_interface");
					item.addProperty("abstractMethods", node.methods.size());
					listeners.add(item);
				}
			}

			// Event class: non-interface, extends a common base, has no complex logic
			if ((node.access & Opcodes.ACC_INTERFACE) == 0 && node.superName != null) {
				boolean hasGetters = node.methods.stream()
						.filter(m -> !m.name.equals("<init>") && !m.name.equals("<clinit>"))
						.allMatch(m -> m.desc.startsWith("()") && !m.desc.equals("()V"));
				if (hasGetters && node.methods.size() >= 2 && node.fields.size() >= 1) {
					// Looks like a data-carrying event
					JsonObject item = classJson(project, entry);
					item.addProperty("kind", "event_class");
					item.addProperty("fields", node.fields.size());
					eventClasses.add(item);
				}
			}

			// Registrar: has methods like addListener, register, subscribe, on
			for (MethodNode m : node.methods) {
				String name = m.name.toLowerCase();
				if ((name.startsWith("add") || name.startsWith("register") || name.startsWith("subscribe") || name.equals("on"))
						&& m.desc.contains(")V") && m.desc.contains("(L")) {
					JsonObject item = classJson(project, entry);
					item.addProperty("kind", "event_registrar");
					item.addProperty("registrationMethod", m.name + m.desc);
					registrars.add(item);
					break;
				}
			}
		}

		JsonObject result = new JsonObject();
		result.add("listeners", listeners);
		result.add("eventClasses", eventClasses);
		result.add("registrars", registrars);
		return result;
	}
}
