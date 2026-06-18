package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class SuggestAnalysisTargetsTool extends BaseTool {
	public SuggestAnalysisTargetsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "suggest_analysis_targets";
	}

	@Override
	public String description() {
		return "Suggest classes and methods that are useful starting points for reverse analysis.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		schema.getAsJsonObject("properties").add("limit", integerProperty("Maximum number of targets to return."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) {
		int limit = limit(arguments, 25);
		JsonArray methods = new JsonArray();

		record ScoredMethod(MethodEntry entry, int score, int outgoing, int incoming) {}

		project.getJarIndex().getEntryIndex().getMethods().stream()
				.map(entry -> {
					int outgoing = project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(entry).size();
					int incoming = project.getJarIndex().getReferenceIndex().getReferencesToMethod(entry).size();
					int score = outgoing + incoming;

					if (entry.getName().equals("main")) {
						score += 1000;
					}

					if (entry.isConstructor()) {
						score += 10;
					}

					return new ScoredMethod(entry, score, outgoing, incoming);
				})
				.sorted(Comparator.comparingInt((ScoredMethod sm) -> sm.score()).reversed())
				.limit(limit)
				.forEach(sm -> {
					JsonObject item = methodJson(project, sm.entry());
					item.addProperty("score", sm.score());
					item.addProperty("outgoingCalls", sm.outgoing());
					item.addProperty("incomingReferences", sm.incoming());
					methods.add(item);
				});

		JsonArray classes = new JsonArray();

		record ScoredClass(ClassEntry entry, int refs) {}

		project.getJarIndex().getEntryIndex().getClasses().stream()
				.map(entry -> new ScoredClass(entry, project.getJarIndex().getReferenceIndex().getReferencesToClass(entry).size()))
				.sorted(Comparator.comparingInt((ScoredClass sc) -> sc.refs()).reversed())
				.limit(limit)
				.forEach(sc -> {
					JsonObject item = classJson(project, sc.entry());
					item.addProperty("incomingReferences", sc.refs());
					classes.add(item);
				});

		JsonObject result = new JsonObject();
		result.add("methods", methods);
		result.add("classes", classes);
		return result;
	}
}
