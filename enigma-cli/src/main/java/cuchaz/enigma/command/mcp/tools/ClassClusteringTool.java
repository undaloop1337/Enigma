package cuchaz.enigma.command.mcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class ClassClusteringTool extends BaseTool {
	public ClassClusteringTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "class_clustering";
	}

	@Override
	public String description() {
		return "Group classes into logical clusters based on mutual references, shared types, and inheritance. Helps identify subsystems in obfuscated jars.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("seedClass", stringProperty("Optional seed class to find its cluster."));
		properties.add("maxClusterSize", integerProperty("Maximum classes per cluster. Defaults to 20."));
		properties.add("limit", integerProperty("Maximum clusters to return. Defaults to 10."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String seedClass = getString(arguments, "seedClass");
		int maxClusterSize = getInt(arguments, "maxClusterSize", 20);
		int limit = limit(arguments, 10);

		if (seedClass != null) {
			ClassEntry seed = requireKnownClass(project, seedClass);
			return clusterAround(seed, maxClusterSize);
		}

		// Build affinity graph
		Map<ClassEntry, Set<ClassEntry>> affinities = new HashMap<>();
		for (MethodEntry method : project.getJarIndex().getEntryIndex().getMethods()) {
			ClassEntry owner = method.getParent();
			for (MethodEntry called : project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(method)) {
				ClassEntry target = called.getParent();
				if (!owner.equals(target) && project.getJarIndex().getEntryIndex().hasClass(target)) {
					affinities.computeIfAbsent(owner, k -> new HashSet<>()).add(target);
					affinities.computeIfAbsent(target, k -> new HashSet<>()).add(owner);
				}
			}
		}

		// Simple greedy clustering
		Set<ClassEntry> assigned = new HashSet<>();
		List<Set<ClassEntry>> clusters = new ArrayList<>();

		// Sort by connectivity (most connected first)
		List<ClassEntry> sorted = new ArrayList<>(affinities.keySet());
		sorted.sort(Comparator.comparingInt((ClassEntry c) -> affinities.getOrDefault(c, Set.of()).size()).reversed());

		for (ClassEntry seed : sorted) {
			if (assigned.contains(seed)) continue;
			if (clusters.size() >= limit) break;

			Set<ClassEntry> cluster = new HashSet<>();
			cluster.add(seed);
			assigned.add(seed);

			// Add most-connected neighbors
			Set<ClassEntry> neighbors = affinities.getOrDefault(seed, Set.of());
			neighbors.stream()
					.filter(n -> !assigned.contains(n))
					.sorted(Comparator.comparingInt((ClassEntry n) -> {
						// Score: how many cluster members does this neighbor connect to
						Set<ClassEntry> nNeighbors = affinities.getOrDefault(n, Set.of());
						return (int) nNeighbors.stream().filter(cluster::contains).count();
					}).reversed())
					.limit(maxClusterSize - 1)
					.forEach(n -> {
						cluster.add(n);
						assigned.add(n);
					});

			if (cluster.size() >= 2) {
				clusters.add(cluster);
			}
		}

		JsonArray result = new JsonArray();
		for (Set<ClassEntry> cluster : clusters) {
			JsonObject clusterObj = new JsonObject();
			JsonArray members = new JsonArray();
			cluster.stream()
					.sorted(Comparator.comparing(ClassEntry::getFullName))
					.forEach(c -> members.add(classJson(project, c)));
			clusterObj.addProperty("size", cluster.size());
			clusterObj.add("members", members);
			result.add(clusterObj);
		}

		JsonObject output = new JsonObject();
		output.addProperty("clusterCount", clusters.size());
		output.add("clusters", result);
		return output;
	}

	private JsonObject clusterAround(ClassEntry seed, int maxSize) {
		Set<ClassEntry> cluster = new HashSet<>();
		cluster.add(seed);

		// Add classes referenced by seed
		for (MethodEntry method : project.getJarIndex().getEntryIndex().getMethods()) {
			if (!method.getParent().equals(seed)) continue;
			for (MethodEntry called : project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(method)) {
				ClassEntry target = called.getParent();
				if (!target.equals(seed) && project.getJarIndex().getEntryIndex().hasClass(target)) {
					cluster.add(target);
				}
			}
		}

		// Add classes that reference seed
		for (var ref : project.getJarIndex().getReferenceIndex().getReferencesToClass(seed)) {
			if (ref.context != null) {
				ClassEntry caller = ref.context.getParent();
				if (!caller.equals(seed)) {
					cluster.add(caller);
				}
			}
		}

		// Add inheritance
		cluster.addAll(project.getJarIndex().getInheritanceIndex().getParents(seed));
		cluster.addAll(project.getJarIndex().getInheritanceIndex().getChildren(seed));

		// Trim to max size - keep most connected
		if (cluster.size() > maxSize) {
			List<ClassEntry> sorted = new ArrayList<>(cluster);
			sorted.remove(seed);
			sorted.sort(Comparator.comparingInt((ClassEntry c) ->
					project.getJarIndex().getReferenceIndex().getReferencesToClass(c).size()).reversed());
			cluster.clear();
			cluster.add(seed);
			sorted.stream().limit(maxSize - 1).forEach(cluster::add);
		}

		JsonObject result = classJson(project, seed);
		JsonArray members = new JsonArray();
		cluster.stream()
				.sorted(Comparator.comparing(ClassEntry::getFullName))
				.forEach(c -> members.add(classJson(project, c)));
		result.addProperty("clusterSize", cluster.size());
		result.add("cluster", members);
		return result;
	}
}
