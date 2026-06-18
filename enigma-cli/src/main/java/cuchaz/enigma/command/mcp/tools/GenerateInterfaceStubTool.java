package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class GenerateInterfaceStubTool extends BaseTool {
	public GenerateInterfaceStubTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "generate_interface_stub";
	}

	@Override
	public String description() {
		return "Generate a clean Java interface/abstract class stub from deobfuscated mappings, suitable for API documentation.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Class to generate stub for."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		ClassEntry deobf = project.getMapper().deobfuscate(entry);

		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(getPackage(deobf.getFullName())).append(";\n\n");

		// Class declaration
		int access = project.getJarIndex().getEntryIndex().getAccess(entry);
		if ((access & 0x0200) != 0) {
			sb.append("public interface ");
		} else if ((access & 0x0400) != 0) {
			sb.append("public abstract class ");
		} else {
			sb.append("public class ");
		}
		sb.append(deobf.getSimpleName());

		// Extends
		var parents = project.getJarIndex().getInheritanceIndex().getParents(entry);
		boolean first = true;
		for (ClassEntry parent : parents) {
			if (parent.getFullName().equals("java/lang/Object")) continue;
			ClassEntry deobfParent = project.getMapper().deobfuscate(parent);
			if (first) {
				if ((access & 0x0200) != 0) sb.append(" extends ");
				else sb.append(" extends ");
				first = false;
			} else {
				sb.append(", ");
			}
			sb.append(deobfParent.getSimpleName());
		}
		sb.append(" {\n\n");

		// Fields
		project.getJarIndex().getEntryIndex().getFields().stream()
				.filter(f -> f.getParent().equals(entry))
				.sorted(Comparator.comparing(FieldEntry::getName))
				.forEach(f -> {
					FieldEntry deobfField = project.getMapper().deobfuscate(f);
					sb.append("    ").append(descToJava(f.getDesc().toString())).append(" ")
							.append(deobfField.getName()).append(";\n");
				});

		if (project.getJarIndex().getEntryIndex().getFields().stream().anyMatch(f -> f.getParent().equals(entry))) {
			sb.append("\n");
		}

		// Methods
		project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry) && !m.isConstructor())
				.sorted(Comparator.comparing(MethodEntry::getName))
				.forEach(m -> {
					MethodEntry deobfMethod = project.getMapper().deobfuscate(m);
					String desc = m.getDesc().toString();
					String returnType = descToJava(desc.substring(desc.lastIndexOf(')') + 1));
					sb.append("    ").append(returnType).append(" ").append(deobfMethod.getName()).append("(");
					// Parameters (simplified)
					String params = desc.substring(1, desc.lastIndexOf(')'));
					if (!params.isEmpty()) sb.append("...");
					sb.append(");\n\n");
				});

		sb.append("}\n");

		JsonObject result = classJson(project, entry);
		result.addProperty("stub", sb.toString());
		return result;
	}

	private static String getPackage(String fullName) {
		int slash = fullName.lastIndexOf('/');
		return slash >= 0 ? fullName.substring(0, slash).replace('/', '.') : "";
	}

	private static String descToJava(String desc) {
		if (desc.startsWith("[")) return descToJava(desc.substring(1)) + "[]";
		return switch (desc) {
			case "V" -> "void";
			case "I" -> "int";
			case "J" -> "long";
			case "Z" -> "boolean";
			case "F" -> "float";
			case "D" -> "double";
			case "B" -> "byte";
			case "S" -> "short";
			case "C" -> "char";
			default -> {
				if (desc.startsWith("L") && desc.endsWith(";")) {
					String full = desc.substring(1, desc.length() - 1);
					int slash = full.lastIndexOf('/');
					yield slash >= 0 ? full.substring(slash + 1) : full;
				}
				yield "Object";
			}
		};
	}
}
