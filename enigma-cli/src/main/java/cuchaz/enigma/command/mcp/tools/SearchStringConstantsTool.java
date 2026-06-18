package cuchaz.enigma.command.mcp.tools;

import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class SearchStringConstantsTool extends BaseTool {
	public SearchStringConstantsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "search_string_constants";
	}

	@Override
	public String description() {
		return "Search all classes for string constants matching a substring or pattern.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("query", stringProperty("Substring to search for in string constants."));
		properties.add("caseSensitive", booleanProperty("Case sensitive search. Defaults to false."));
		properties.add("limit", integerProperty("Maximum number of results."));
		require(schema, "query");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String query = getString(arguments, "query");
		if (query == null || query.isEmpty()) {
			throw new McpException(INVALID_PARAMS, "query is required");
		}

		boolean caseSensitive = getBoolean(arguments, "caseSensitive", false);
		int limit = limit(arguments, 100);
		String searchQuery = caseSensitive ? query : query.toLowerCase(Locale.ROOT);
		JsonArray results = new JsonArray();

		for (ClassEntry classEntry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) {
				break;
			}

			ClassNode node = project.getClassProvider().get(classEntry.getFullName());
			if (node == null) {
				continue;
			}

			for (MethodNode method : node.methods) {
				if (results.size() >= limit) {
					break;
				}

				for (AbstractInsnNode insn : method.instructions) {
					if (results.size() >= limit) {
						break;
					}
					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String str) {
						String toMatch = caseSensitive ? str : str.toLowerCase(Locale.ROOT);
						if (toMatch.contains(searchQuery)) {
							JsonObject item = new JsonObject();
							item.addProperty("class", classEntry.getFullName());
							item.addProperty("deobfuscatedClass", project.getMapper().deobfuscate(classEntry).getFullName());
							item.addProperty("method", method.name);
							item.addProperty("methodDesc", method.desc);
							item.addProperty("value", str);
							results.add(item);
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("query", query);
		result.addProperty("matchCount", results.size());
		result.add("matches", results);
		return result;
	}
}
