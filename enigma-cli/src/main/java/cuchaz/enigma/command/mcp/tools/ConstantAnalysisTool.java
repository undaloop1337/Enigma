package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.Opcodes;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class ConstantAnalysisTool extends BaseTool {
	public ConstantAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "constant_analysis";
	}

	@Override
	public String description() {
		return "Analyze numeric constants for semantic meaning: port numbers, protocol versions, bit flags, well-known values.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Optional class to analyze."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 100);
		JsonArray results = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;
			if (className != null && !entry.getFullName().equals(className)) continue;

			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if (results.size() >= limit) break;
				for (AbstractInsnNode insn : method.instructions) {
					if (results.size() >= limit) break;

					if (insn instanceof LdcInsnNode ldc) {
						if (ldc.cst instanceof Integer i) {
							String meaning = interpretInt(i);
							if (meaning != null) {
								addConstant(results, entry, method, "int", String.valueOf(i), meaning);
							}
						} else if (ldc.cst instanceof Long l) {
							String meaning = interpretLong(l);
							if (meaning != null) {
								addConstant(results, entry, method, "long", String.valueOf(l), meaning);
							}
						}
					} else if (insn instanceof IntInsnNode iinsn && insn.getOpcode() == Opcodes.SIPUSH) {
						String meaning = interpretInt(iinsn.operand);
						if (meaning != null) {
							addConstant(results, entry, method, "int", String.valueOf(iinsn.operand), meaning);
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", results.size());
		result.add("constants", results);
		return result;
	}

	private static void addConstant(JsonArray results, ClassEntry entry, MethodNode method, String type, String value, String meaning) {
		JsonObject item = new JsonObject();
		item.addProperty("class", entry.getFullName());
		item.addProperty("method", method.name);
		item.addProperty("type", type);
		item.addProperty("value", value);
		item.addProperty("meaning", meaning);
		results.add(item);
	}

	private static String interpretInt(int value) {
		return switch (value) {
			case 80 -> "HTTP port";
			case 443 -> "HTTPS port";
			case 8080 -> "HTTP alt port";
			case 25565 -> "Minecraft port";
			case 3306 -> "MySQL port";
			case 5432 -> "PostgreSQL port";
			case 6379 -> "Redis port";
			case 27017 -> "MongoDB port";
			case 0xCAFEBABE -> "Java class magic (as int)";
			case 0x504B0304 -> "ZIP magic";
			case 65535 -> "Max unsigned short";
			case 65536 -> "2^16";
			case 0x7F -> "Max ASCII";
			case 1024 -> "1KB or privileged port boundary";
			case 4096 -> "4KB page size";
			case 8192 -> "8KB buffer size";
			default -> {
				if (Integer.bitCount(value) == 1 && value > 0) yield "Power of 2: 2^" + Integer.numberOfTrailingZeros(value);
				yield null;
			}
		};
	}

	private static String interpretLong(long value) {
		if (value == 0xCAFEBABEL) return "Java class magic";
		if (value == Long.MAX_VALUE) return "Long.MAX_VALUE";
		if (value == Long.MIN_VALUE) return "Long.MIN_VALUE";
		return null;
	}
}
