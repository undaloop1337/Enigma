package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class NamingSuggestionsTool extends BaseTool {
	public NamingSuggestionsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "naming_suggestions";
	}

	@Override
	public String description() {
		return "Suggest meaningful names for a class/method/field based on context: string constants, type patterns, inheritance, and API usage.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("type", stringProperty("class, method, or field."));
		properties.add("className", stringProperty("Class name or owner."));
		properties.add("name", stringProperty("Method/field name."));
		properties.add("desc", stringProperty("Method/field descriptor."));
		require(schema, "type", "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String type = getString(arguments, "type");
		JsonArray suggestions = new JsonArray();

		if ("class".equals(type)) {
			ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
			suggestClassName(entry, suggestions);
		} else if ("method".equals(type)) {
			MethodEntry entry = requireKnownMethod(project, arguments);
			suggestMethodName(entry, suggestions);
		} else if ("field".equals(type)) {
			FieldEntry entry = requireKnownField(project, arguments);
			suggestFieldName(entry, suggestions);
		} else {
			throw new McpException(INVALID_PARAMS, "Unknown type: " + type);
		}

		JsonObject result = new JsonObject();
		result.add("suggestions", suggestions);
		return result;
	}

	private void suggestClassName(ClassEntry entry, JsonArray suggestions) {
		// From interfaces implemented
		for (ClassEntry parent : project.getJarIndex().getInheritanceIndex().getParents(entry)) {
			EntryMapping mapping = project.getMapper().getDeobfMapping(parent);
			if (mapping.targetName() != null) {
				addSuggestion(suggestions, mapping.targetName() + "Impl", "implements " + mapping.targetName(), 60);
			}
		}

		// From string constants in clinit or constructors
		ClassNode node = project.getClassProvider().get(entry.getFullName());
		if (node != null) {
			for (MethodNode mn : node.methods) {
				if (!mn.name.equals("<clinit>") && !mn.name.equals("<init>")) continue;
				for (AbstractInsnNode insn : mn.instructions) {
					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
						if (s.length() >= 3 && s.length() <= 30 && s.matches("[A-Za-z][A-Za-z0-9_]*")) {
							addSuggestion(suggestions, toPascalCase(s), "string constant in init: \"" + s + "\"", 40);
						}
					}
				}
			}
		}

		// From field types (if has many fields of same type, might be a container)
		long fieldCount = project.getJarIndex().getEntryIndex().getFields().stream()
				.filter(f -> f.getParent().equals(entry)).count();
		if (fieldCount == 0) {
			addSuggestion(suggestions, "Utility", "no instance fields (utility class pattern)", 20);
		}
	}

	private void suggestMethodName(MethodEntry entry, JsonArray suggestions) {
		String desc = entry.getDesc().toString();
		String returnType = desc.substring(desc.lastIndexOf(')') + 1);

		// Getter pattern: ()Lx; or ()I etc, no params
		if (desc.startsWith("()") && !returnType.equals("V")) {
			String typeName = extractSimpleType(returnType);
			if (typeName != null) {
				addSuggestion(suggestions, "get" + toPascalCase(typeName), "getter pattern (no params, returns " + typeName + ")", 70);
			}
		}

		// Setter pattern: (X)V
		if (returnType.equals("V") && !desc.equals("()V")) {
			addSuggestion(suggestions, "set" + toPascalCase(extractParamType(desc)), "setter pattern (single param, returns void)", 60);
		}

		// Boolean return
		if (returnType.equals("Z")) {
			addSuggestion(suggestions, "is" + toPascalCase(entry.getName()), "boolean return suggests predicate", 50);
		}
	}

	private void suggestFieldName(FieldEntry entry, JsonArray suggestions) {
		String desc = entry.getDesc().toString();
		String typeName = extractSimpleType(desc);
		if (typeName != null) {
			addSuggestion(suggestions, toCamelCase(typeName), "from field type: " + typeName, 50);
		}

		// Check if it's a list/map/set
		if (desc.contains("List")) addSuggestion(suggestions, "list", "collection type", 30);
		if (desc.contains("Map")) addSuggestion(suggestions, "map", "collection type", 30);
		if (desc.contains("Set")) addSuggestion(suggestions, "set", "collection type", 30);
	}

	private static void addSuggestion(JsonArray arr, String name, String reason, int confidence) {
		JsonObject s = new JsonObject();
		s.addProperty("name", name);
		s.addProperty("reason", reason);
		s.addProperty("confidence", confidence);
		arr.add(s);
	}

	private static String extractSimpleType(String desc) {
		if (desc.startsWith("L") && desc.endsWith(";")) {
			String full = desc.substring(1, desc.length() - 1);
			int slash = full.lastIndexOf('/');
			return slash >= 0 ? full.substring(slash + 1) : full;
		}
		return switch (desc) {
			case "I" -> "int";
			case "J" -> "long";
			case "Z" -> "boolean";
			case "F" -> "float";
			case "D" -> "double";
			case "B" -> "byte";
			case "S" -> "short";
			case "C" -> "char";
			default -> null;
		};
	}

	private static String extractParamType(String desc) {
		String params = desc.substring(1, desc.indexOf(')'));
		return extractSimpleType(params.isEmpty() ? "V" : params);
	}

	private static String toPascalCase(String s) {
		if (s == null || s.isEmpty()) return "Unknown";
		return Character.toUpperCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
	}

	private static String toCamelCase(String s) {
		if (s == null || s.isEmpty()) return "unknown";
		return Character.toLowerCase(s.charAt(0)) + (s.length() > 1 ? s.substring(1) : "");
	}
}
