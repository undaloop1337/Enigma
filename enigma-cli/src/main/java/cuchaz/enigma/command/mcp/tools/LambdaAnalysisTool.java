package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class LambdaAnalysisTool extends BaseTool {
	public LambdaAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "lambda_analysis";
	}

	@Override
	public String description() {
		return "Find lambda expressions and method references in a class or across the project.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Optional class to analyze. If omitted, searches all classes."));
		properties.add("limit", integerProperty("Maximum number of lambdas to return."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 100);
		JsonArray lambdas = new JsonArray();

		if (className != null) {
			ClassEntry entry = requireKnownClass(project, className);
			collectLambdas(entry, lambdas, limit);
		} else {
			for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
				if (lambdas.size() >= limit) break;
				collectLambdas(entry, lambdas, limit);
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", lambdas.size());
		result.add("lambdas", lambdas);
		return result;
	}

	private void collectLambdas(ClassEntry classEntry, JsonArray lambdas, int limit) {
		ClassNode node = project.getClassProvider().get(classEntry.getFullName());
		if (node == null) return;

		for (MethodNode method : node.methods) {
			if (lambdas.size() >= limit) return;

			for (AbstractInsnNode insn : method.instructions) {
				if (lambdas.size() >= limit) return;

				if (insn instanceof InvokeDynamicInsnNode indy) {
					if (indy.bsmArgs != null && indy.bsmArgs.length >= 2 && indy.bsmArgs[1] instanceof Handle handle) {
						JsonObject item = new JsonObject();
						item.addProperty("class", classEntry.getFullName());
						item.addProperty("method", method.name);
						item.addProperty("methodDesc", method.desc);
						item.addProperty("functionalInterface", indy.name);
						item.addProperty("targetClass", handle.getOwner());
						item.addProperty("targetMethod", handle.getName());
						item.addProperty("targetDesc", handle.getDesc());
						item.addProperty("kind", handleKind(handle.getTag()));
						lambdas.add(item);
					}
				}
			}
		}
	}

	private static String handleKind(int tag) {
		return switch (tag) {
			case 5 -> "INVOKEVIRTUAL";
			case 6 -> "INVOKESTATIC";
			case 7 -> "INVOKESPECIAL";
			case 8 -> "NEWINVOKESPECIAL";
			case 9 -> "INVOKEINTERFACE";
			default -> "UNKNOWN(" + tag + ")";
		};
	}
}
