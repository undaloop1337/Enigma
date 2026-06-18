package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class PatternMatchTool extends BaseTool {
	public PatternMatchTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "pattern_match";
	}

	@Override
	public String description() {
		return "Find methods that call specific APIs or match instruction patterns (e.g., methods using crypto, IO, reflection).";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("callsMethod", stringProperty("Find methods calling a method matching this substring (owner or name)."));
		properties.add("usesType", stringProperty("Find methods that reference a type matching this substring."));
		properties.add("accessesField", stringProperty("Find methods that access a field matching this substring."));
		properties.add("minInstructions", integerProperty("Minimum instruction count filter."));
		properties.add("maxInstructions", integerProperty("Maximum instruction count filter."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String callsMethod = lower(getString(arguments, "callsMethod"));
		String usesType = lower(getString(arguments, "usesType"));
		String accessesField = lower(getString(arguments, "accessesField"));
		int minInsn = getInt(arguments, "minInstructions", 0);
		int maxInsn = getInt(arguments, "maxInstructions", Integer.MAX_VALUE);
		int limit = limit(arguments, 50);

		if (callsMethod == null && usesType == null && accessesField == null) {
			throw new McpException(INVALID_PARAMS, "At least one of callsMethod, usesType, or accessesField is required");
		}

		JsonArray results = new JsonArray();

		for (ClassEntry classEntry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;

			ClassNode node = project.getClassProvider().get(classEntry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if (results.size() >= limit) break;

				int size = method.instructions.size();
				if (size < minInsn || size > maxInsn) continue;

				if (matchesPattern(method, callsMethod, usesType, accessesField)) {
					MethodEntry me = MethodEntry.parse(classEntry.getFullName(), method.name, method.desc);
					JsonObject item = methodJson(project, me);
					item.addProperty("instructions", size);
					results.add(item);
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("matchCount", results.size());
		result.add("matches", results);
		return result;
	}

	private static boolean matchesPattern(MethodNode method, String callsMethod, String usesType, String accessesField) {
		boolean needsCall = callsMethod != null;
		boolean needsType = usesType != null;
		boolean needsField = accessesField != null;

		boolean foundCall = !needsCall;
		boolean foundType = !needsType;
		boolean foundField = !needsField;

		for (AbstractInsnNode insn : method.instructions) {
			if (needsCall && !foundCall && insn instanceof MethodInsnNode min) {
				String full = (min.owner + "." + min.name).toLowerCase(Locale.ROOT);
				if (full.contains(callsMethod)) foundCall = true;
			}
			if (needsType && !foundType && insn instanceof TypeInsnNode tin) {
				if (tin.desc.toLowerCase(Locale.ROOT).contains(usesType)) foundType = true;
			}
			if (needsField && !foundField && insn instanceof FieldInsnNode fin) {
				String full = (fin.owner + "." + fin.name).toLowerCase(Locale.ROOT);
				if (full.contains(accessesField)) foundField = true;
			}

			if (foundCall && foundType && foundField) return true;
		}

		return foundCall && foundType && foundField;
	}
}
