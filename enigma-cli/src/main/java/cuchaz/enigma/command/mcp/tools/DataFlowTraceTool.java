package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class DataFlowTraceTool extends BaseTool {
	public DataFlowTraceTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "data_flow_trace";
	}

	@Override
	public String description() {
		return "Trace data flow: track where a method parameter, field, or return value propagates (forward from source, backward to origin).";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("methodName", stringProperty("Method name (source for tracing)."));
		properties.add("methodDesc", stringProperty("Method descriptor."));
		properties.add("direction", stringProperty("forward (from return values/field writes) or backward (to parameter sources). Defaults to forward."));
		properties.add("depth", integerProperty("Maximum trace depth. Defaults to 3."));
		properties.add("limit", integerProperty("Maximum results."));
		require(schema, "className", "methodName", "methodDesc");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		String methodName = getString(arguments, "methodName");
		String methodDesc = getString(arguments, "methodDesc");
		String direction = getString(arguments, "direction", "forward");
		int depth = getInt(arguments, "depth", 3);
		int limit = limit(arguments, 50);

		ClassEntry classEntry = requireKnownClass(project, className);
		MethodEntry startMethod = MethodEntry.parse(className, methodName, methodDesc);

		JsonArray trace = new JsonArray();

		if ("forward".equals(direction)) {
			traceForward(startMethod, trace, depth, limit);
		} else {
			traceBackward(startMethod, trace, depth, limit);
		}

		JsonObject result = new JsonObject();
		result.addProperty("startClass", className);
		result.addProperty("startMethod", methodName);
		result.addProperty("direction", direction);
		result.addProperty("depth", depth);
		result.add("trace", trace);
		return result;
	}

	private void traceForward(MethodEntry start, JsonArray trace, int maxDepth, int limit) {
		Set<String> visited = new HashSet<>();
		Queue<TraceEntry> queue = new LinkedList<>();
		queue.add(new TraceEntry(start, 0));
		visited.add(start.toString());

		while (!queue.isEmpty() && trace.size() < limit) {
			TraceEntry current = queue.poll();
			if (current.depth > maxDepth) continue;

			// Find what this method calls and what fields it writes
			ClassNode node = project.getClassProvider().get(current.method.getParent().getFullName());
			if (node == null) continue;

			for (MethodNode mn : node.methods) {
				if (!mn.name.equals(current.method.getName()) || !mn.desc.equals(current.method.getDesc().toString())) continue;

				for (AbstractInsnNode insn : mn.instructions) {
					if (trace.size() >= limit) return;

					if (insn instanceof MethodInsnNode min) {
						String key = min.owner + "." + min.name + min.desc;
						if (!visited.contains(key)) {
							visited.add(key);
							JsonObject item = new JsonObject();
							item.addProperty("kind", "method_call");
							item.addProperty("class", min.owner);
							item.addProperty("method", min.name);
							item.addProperty("desc", min.desc);
							item.addProperty("depth", current.depth + 1);
							trace.add(item);

							MethodEntry next = MethodEntry.parse(min.owner, min.name, min.desc);
							if (project.getJarIndex().getEntryIndex().hasMethod(next)) {
								queue.add(new TraceEntry(next, current.depth + 1));
							}
						}
					} else if (insn instanceof FieldInsnNode fin) {
						if (insn.getOpcode() == Opcodes.PUTFIELD || insn.getOpcode() == Opcodes.PUTSTATIC) {
							String key = "field:" + fin.owner + "." + fin.name;
							if (!visited.contains(key)) {
								visited.add(key);
								JsonObject item = new JsonObject();
								item.addProperty("kind", "field_write");
								item.addProperty("class", fin.owner);
								item.addProperty("field", fin.name);
								item.addProperty("desc", fin.desc);
								item.addProperty("depth", current.depth + 1);
								trace.add(item);
							}
						}
					}
				}
				break;
			}
		}
	}

	private void traceBackward(MethodEntry start, JsonArray trace, int maxDepth, int limit) {
		Set<String> visited = new HashSet<>();
		Queue<TraceEntry> queue = new LinkedList<>();
		queue.add(new TraceEntry(start, 0));
		visited.add(start.toString());

		while (!queue.isEmpty() && trace.size() < limit) {
			TraceEntry current = queue.poll();
			if (current.depth > maxDepth) continue;

			// Find who calls this method
			var refs = project.getJarIndex().getReferenceIndex().getReferencesToMethod(current.method);
			for (var ref : refs) {
				if (trace.size() >= limit) return;
				if (ref.context == null) continue;

				String key = ref.context.toString();
				if (!visited.contains(key)) {
					visited.add(key);
					JsonObject item = new JsonObject();
					item.addProperty("kind", "called_by");
					item.addProperty("class", ref.context.getParent().getFullName());
					item.addProperty("method", ref.context.getName());
					item.addProperty("desc", ref.context.getDesc().toString());
					item.addProperty("depth", current.depth + 1);
					trace.add(item);

					MethodEntry caller = MethodEntry.parse(ref.context.getParent().getFullName(), ref.context.getName(), ref.context.getDesc().toString());
					if (current.depth + 1 < maxDepth) {
						queue.add(new TraceEntry(caller, current.depth + 1));
					}
				}
			}
		}
	}

	private record TraceEntry(MethodEntry method, int depth) {}
}
