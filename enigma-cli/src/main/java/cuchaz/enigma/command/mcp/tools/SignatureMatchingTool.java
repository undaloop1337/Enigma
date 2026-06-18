package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class SignatureMatchingTool extends BaseTool {
	public SignatureMatchingTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "signature_matching";
	}

	@Override
	public String description() {
		return "Find classes with a matching structural signature: same method count, field count, interface count, inheritance shape.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Reference class to match against."));
		properties.add("tolerance", integerProperty("How many differences are allowed (0=exact). Defaults to 1."));
		properties.add("limit", integerProperty("Maximum matches."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		int tolerance = getInt(arguments, "tolerance", 1);
		int limit = limit(arguments, 20);

		// Build signature for reference class
		Signature refSig = buildSignature(entry);

		record Match(ClassEntry entry, int distance) {}
		List<Match> matches = new ArrayList<>();

		for (ClassEntry candidate : project.getJarIndex().getEntryIndex().getClasses()) {
			if (candidate.equals(entry)) continue;
			Signature candSig = buildSignature(candidate);
			int dist = refSig.distanceTo(candSig);
			if (dist <= tolerance) {
				matches.add(new Match(candidate, dist));
			}
		}

		matches.sort(Comparator.comparingInt(Match::distance));

		JsonArray results = new JsonArray();
		matches.stream().limit(limit).forEach(m -> {
			JsonObject item = classJson(project, m.entry());
			item.addProperty("distance", m.distance());
			results.add(item);
		});

		JsonObject result = classJson(project, entry);
		result.addProperty("matchCount", matches.size());
		result.add("matches", results);
		return result;
	}

	private Signature buildSignature(ClassEntry entry) {
		long methodCount = project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry)).count();
		long fieldCount = project.getJarIndex().getEntryIndex().getFields().stream()
				.filter(f -> f.getParent().equals(entry)).count();
		int parentCount = project.getJarIndex().getInheritanceIndex().getParents(entry).size();
		int childCount = project.getJarIndex().getInheritanceIndex().getChildren(entry).size();
		return new Signature((int) methodCount, (int) fieldCount, parentCount, childCount);
	}

	private record Signature(int methods, int fields, int parents, int children) {
		int distanceTo(Signature other) {
			return Math.abs(methods - other.methods) + Math.abs(fields - other.fields)
					+ Math.abs(parents - other.parents) + Math.abs(children - other.children);
		}
	}
}
