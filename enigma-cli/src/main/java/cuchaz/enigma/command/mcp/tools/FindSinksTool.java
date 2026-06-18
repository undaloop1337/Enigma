package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class FindSinksTool extends BaseTool {
	private static final List<String[]> DANGEROUS_APIS = List.of(
			new String[]{"java/lang/Runtime", "exec", "command_execution"},
			new String[]{"java/lang/ProcessBuilder", "<init>", "command_execution"},
			new String[]{"java/io/ObjectInputStream", "readObject", "deserialization"},
			new String[]{"java/io/ObjectInputStream", "readUnshared", "deserialization"},
			new String[]{"java/lang/reflect/Method", "invoke", "reflection"},
			new String[]{"java/lang/Class", "forName", "reflection"},
			new String[]{"java/lang/Class", "newInstance", "reflection"},
			new String[]{"java/lang/ClassLoader", "loadClass", "classloading"},
			new String[]{"java/net/URL", "openConnection", "network"},
			new String[]{"java/net/Socket", "<init>", "network"},
			new String[]{"java/sql/Statement", "execute", "sql"},
			new String[]{"java/sql/Statement", "executeQuery", "sql"},
			new String[]{"java/sql/Statement", "executeUpdate", "sql"},
			new String[]{"java/sql/Connection", "prepareStatement", "sql"},
			new String[]{"javax/naming/Context", "lookup", "jndi"},
			new String[]{"javax/naming/InitialContext", "lookup", "jndi"},
			new String[]{"javax/script/ScriptEngine", "eval", "script_eval"},
			new String[]{"java/io/FileOutputStream", "<init>", "file_write"},
			new String[]{"java/io/FileInputStream", "<init>", "file_read"},
			new String[]{"java/nio/file/Files", "delete", "file_delete"},
			new String[]{"java/lang/System", "exit", "system_exit"},
			new String[]{"java/security/AccessController", "doPrivileged", "privilege_escalation"}
	);

	public FindSinksTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "find_sinks";
	}

	@Override
	public String description() {
		return "Find dangerous API call sites: command execution, deserialization, reflection, SQL, JNDI, file I/O, network.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("category", stringProperty("Filter by category: command_execution, deserialization, reflection, network, sql, jndi, file_write, file_read, all. Defaults to all."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String category = getString(arguments, "category", "all");
		int limit = limit(arguments, 100);
		JsonArray results = new JsonArray();

		for (ClassEntry classEntry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;

			ClassNode node = project.getClassProvider().get(classEntry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if (results.size() >= limit) break;

				for (AbstractInsnNode insn : method.instructions) {
					if (results.size() >= limit) break;

					if (insn instanceof MethodInsnNode min) {
						for (String[] api : DANGEROUS_APIS) {
							if (!category.equals("all") && !api[2].equals(category)) continue;
							if (min.owner.equals(api[0]) && min.name.equals(api[1])) {
								JsonObject item = new JsonObject();
								item.addProperty("class", classEntry.getFullName());
								item.addProperty("method", method.name);
								item.addProperty("methodDesc", method.desc);
								item.addProperty("sinkClass", min.owner);
								item.addProperty("sinkMethod", min.name);
								item.addProperty("category", api[2]);
								results.add(item);
								break;
							}
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", results.size());
		result.add("sinks", results);
		return result;
	}
}
