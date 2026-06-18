package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class ThreadAnalysisTool extends BaseTool {
	public ThreadAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "thread_analysis";
	}

	@Override
	public String description() {
		return "Analyze concurrency: Thread/Runnable implementations, synchronized blocks, volatile fields, executor usage.";
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
		JsonArray threadClasses = new JsonArray();
		JsonArray volatileFields = new JsonArray();
		JsonArray synchronizedMethods = new JsonArray();
		JsonArray executorUsage = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (threadClasses.size() + volatileFields.size() + synchronizedMethods.size() >= limit * 3) break;

			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			// Thread/Runnable/Callable implementations
			if (isThreadLike(node)) {
				JsonObject item = classJson(project, entry);
				item.addProperty("kind", getThreadKind(node));
				threadClasses.add(item);
			}

			// Volatile fields
			for (FieldNode field : node.fields) {
				if ((field.access & Opcodes.ACC_VOLATILE) != 0 && volatileFields.size() < limit) {
					JsonObject item = new JsonObject();
					item.addProperty("class", entry.getFullName());
					item.addProperty("field", field.name);
					item.addProperty("desc", field.desc);
					volatileFields.add(item);
				}
			}

			// Synchronized methods
			for (MethodNode method : node.methods) {
				if ((method.access & Opcodes.ACC_SYNCHRONIZED) != 0 && synchronizedMethods.size() < limit) {
					JsonObject item = new JsonObject();
					item.addProperty("class", entry.getFullName());
					item.addProperty("method", method.name);
					item.addProperty("desc", method.desc);
					synchronizedMethods.add(item);
				}

				// Executor usage
				if (executorUsage.size() < limit) {
					for (AbstractInsnNode insn : method.instructions) {
						if (insn instanceof MethodInsnNode min) {
							if (min.owner.contains("Executor") || min.owner.contains("ThreadPool")
									|| (min.owner.equals("java/util/concurrent/Executors") && min.name.startsWith("new"))) {
								JsonObject item = new JsonObject();
								item.addProperty("class", entry.getFullName());
								item.addProperty("method", method.name);
								item.addProperty("api", min.owner + "." + min.name);
								executorUsage.add(item);
								break;
							}
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.add("threadClasses", threadClasses);
		result.add("volatileFields", volatileFields);
		result.add("synchronizedMethods", synchronizedMethods);
		result.add("executorUsage", executorUsage);
		return result;
	}

	private static boolean isThreadLike(ClassNode node) {
		if (node.superName != null && (node.superName.equals("java/lang/Thread") || node.superName.contains("TimerTask"))) return true;
		if (node.interfaces != null) {
			for (String iface : node.interfaces) {
				if (iface.equals("java/lang/Runnable") || iface.equals("java/util/concurrent/Callable")) return true;
			}
		}
		return false;
	}

	private static String getThreadKind(ClassNode node) {
		if (node.superName != null && node.superName.equals("java/lang/Thread")) return "Thread";
		if (node.interfaces != null && node.interfaces.contains("java/lang/Runnable")) return "Runnable";
		if (node.interfaces != null && node.interfaces.contains("java/util/concurrent/Callable")) return "Callable";
		return "TimerTask";
	}
}
