package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class MethodSimilarityTool extends BaseTool {
	public MethodSimilarityTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "method_similarity";
	}

	@Override
	public String description() {
		return "Find methods with similar bytecode structure to a given method (clone detection).";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Method name."));
		properties.add("desc", stringProperty("Method descriptor."));
		properties.add("threshold", integerProperty("Minimum similarity percentage (0-100). Defaults to 70."));
		properties.add("limit", integerProperty("Maximum number of similar methods to return."));
		require(schema, "className", "name", "desc");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		MethodEntry targetEntry = requireKnownMethod(project, arguments);
		int threshold = getInt(arguments, "threshold", 70);
		int limit = limit(arguments, 20);

		ClassNode targetClass = project.getClassProvider().get(targetEntry.getParent().getFullName());
		if (targetClass == null) {
			throw new McpException(INVALID_PARAMS, "Class bytes not available");
		}

		MethodNode targetMethod = null;
		for (MethodNode mn : targetClass.methods) {
			if (mn.name.equals(targetEntry.getName()) && mn.desc.equals(targetEntry.getDesc().toString())) {
				targetMethod = mn;
				break;
			}
		}

		if (targetMethod == null) {
			throw new McpException(INVALID_PARAMS, "Method not found in bytecode");
		}

		int targetSize = targetMethod.instructions.size();
		if (targetSize == 0) {
			JsonObject result = methodJson(project, targetEntry);
			result.add("similar", new JsonArray());
			return result;
		}

		int[] targetOpcodes = getOpcodeSequence(targetMethod);

		record SimilarMethod(MethodEntry entry, int similarity) {}
		List<SimilarMethod> similar = new ArrayList<>();

		for (ClassEntry classEntry : project.getJarIndex().getEntryIndex().getClasses()) {
			ClassNode node = project.getClassProvider().get(classEntry.getFullName());
			if (node == null) continue;

			for (MethodNode mn : node.methods) {
				if (mn.name.equals(targetEntry.getName()) && mn.desc.equals(targetEntry.getDesc().toString())
						&& classEntry.equals(targetEntry.getParent())) {
					continue; // skip self
				}

				if (mn.instructions.size() == 0) continue;

				// Quick size filter
				double sizeRatio = (double) mn.instructions.size() / targetSize;
				if (sizeRatio < 0.5 || sizeRatio > 2.0) continue;

				int[] opcodes = getOpcodeSequence(mn);
				int sim = computeSimilarity(targetOpcodes, opcodes);
				if (sim >= threshold) {
					MethodEntry me = MethodEntry.parse(classEntry.getFullName(), mn.name, mn.desc);
					similar.add(new SimilarMethod(me, sim));
				}
			}
		}

		similar.sort(Comparator.comparingInt((SimilarMethod s) -> s.similarity()).reversed());

		JsonArray results = new JsonArray();
		similar.stream().limit(limit).forEach(sm -> {
			JsonObject item = methodJson(project, sm.entry());
			item.addProperty("similarity", sm.similarity());
			results.add(item);
		});

		JsonObject result = methodJson(project, targetEntry);
		result.addProperty("instructionCount", targetSize);
		result.add("similar", results);
		return result;
	}

	private static int[] getOpcodeSequence(MethodNode method) {
		int[] opcodes = new int[method.instructions.size()];
		for (int i = 0; i < opcodes.length; i++) {
			opcodes[i] = method.instructions.get(i).getOpcode();
		}
		return opcodes;
	}

	private static int computeSimilarity(int[] a, int[] b) {
		int len = Math.min(a.length, b.length);
		int matches = 0;
		for (int i = 0; i < len; i++) {
			if (a[i] == b[i]) matches++;
		}
		int maxLen = Math.max(a.length, b.length);
		return maxLen == 0 ? 100 : (matches * 100) / maxLen;
	}
}
