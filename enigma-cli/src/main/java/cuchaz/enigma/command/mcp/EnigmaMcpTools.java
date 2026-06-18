package cuchaz.enigma.command.mcp;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.source.Decompiler;
import cuchaz.enigma.source.SourceSettings;
import cuchaz.enigma.source.bytecode.BytecodeDecompiler;
import cuchaz.enigma.source.cfr.CfrDecompiler;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.validation.ParameterizedMessage;
import cuchaz.enigma.utils.validation.ValidationContext;

public final class EnigmaMcpTools {
	private static final int INVALID_PARAMS = -32602;

	private EnigmaMcpTools() {
	}

	public static List<McpTool> create(EnigmaProject project) {
		return List.of(
				new ProjectInfoTool(project),
				new PackageStatsTool(project),
				new ListClassesTool(project),
				new SearchClassesTool(project),
				new SearchMembersTool(project),
				new ClassStructureTool(project),
				new ClassBytecodeSummaryTool(project),
				new ClassConstantsTool(project),
				new EntryPointsTool(project),
				new DecompileClassTool(project),
				new ReferencesTool(project),
				new MethodCallsTool(project),
				new InheritanceTool(project),
				new SuggestAnalysisTargetsTool(project),
				new GetMappingTool(project),
				new SetClassMappingTool(project),
				new SetMethodMappingTool(project),
				new SetFieldMappingTool(project)
		);
	}

	private abstract static class BaseTool implements McpTool {
		final EnigmaProject project;

		BaseTool(EnigmaProject project) {
			this.project = project;
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = new JsonObject();
			schema.addProperty("type", "object");
			schema.add("properties", new JsonObject());
			return schema;
		}

		JsonObject stringProperty(String description) {
			JsonObject property = new JsonObject();
			property.addProperty("type", "string");
			property.addProperty("description", description);
			return property;
		}

		JsonObject integerProperty(String description) {
			JsonObject property = new JsonObject();
			property.addProperty("type", "integer");
			property.addProperty("description", description);
			return property;
		}

		JsonObject booleanProperty(String description) {
			JsonObject property = new JsonObject();
			property.addProperty("type", "boolean");
			property.addProperty("description", description);
			return property;
		}
	}

	private static class ProjectInfoTool extends BaseTool {
		ProjectInfoTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "project_info";
		}

		@Override
		public String description() {
			return "Return basic information about the open Enigma project.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			JsonObject result = new JsonObject();
			result.add("jars", paths(project.getJarPaths()));
			result.add("libraries", paths(project.getLibraryPaths()));
			result.addProperty("checksum", HexFormat.of().formatHex(project.getJarChecksum()));
			result.addProperty("classes", project.getJarIndex().getEntryIndex().getClasses().size());
			result.addProperty("methods", project.getJarIndex().getEntryIndex().getMethods().size());
			result.addProperty("fields", project.getJarIndex().getEntryIndex().getFields().size());
			return result;
		}

		private JsonArray paths(List<Path> paths) {
			JsonArray result = new JsonArray();

			for (Path path : paths) {
				result.add(path.toString());
			}

			return result;
		}
	}

	private static class PackageStatsTool extends BaseTool {
		PackageStatsTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "package_stats";
		}

		@Override
		public String description() {
			return "Summarize packages by class count for triaging a jar.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			schema.getAsJsonObject("properties").add("limit", integerProperty("Maximum number of packages to return."));
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			Map<String, Integer> counts = new LinkedHashMap<>();

			for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
				String packageName = entry.getPackageName();

				if (packageName == null) {
					packageName = "";
				}

				counts.merge(packageName, 1, Integer::sum);
			}

			JsonArray packages = new JsonArray();
			counts.entrySet().stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
					.limit(limit(arguments, 100))
					.forEach(entry -> {
						JsonObject item = new JsonObject();
						item.addProperty("package", entry.getKey());
						item.addProperty("classes", entry.getValue());
						packages.add(item);
					});

			JsonObject result = new JsonObject();
			result.add("packages", packages);
			return result;
		}
	}

	private static class ListClassesTool extends SearchClassesTool {
		ListClassesTool(EnigmaProject project) {
			super(project, "list_classes", "List classes in the open Enigma project.");
		}
	}

	private static class SearchClassesTool extends BaseTool {
		private final String name;
		private final String description;

		SearchClassesTool(EnigmaProject project) {
			this(project, "search_classes", "Search classes by obfuscated or deobfuscated name.");
		}

		SearchClassesTool(EnigmaProject project, String name, String description) {
			super(project);
			this.name = name;
			this.description = description;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public String description() {
			return description;
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("query", stringProperty("Optional substring to filter class names."));
			properties.add("limit", integerProperty("Maximum number of classes to return."));
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			String query = lower(getString(arguments, "query"));
			int limit = limit(arguments, 100);
			JsonArray classes = new JsonArray();

			project.getJarIndex().getEntryIndex().getClasses().stream()
					.sorted(Comparator.comparing(ClassEntry::getFullName))
					.filter(entry -> query == null || lower(entry.getFullName()).contains(query) || lower(project.getMapper().deobfuscate(entry).getFullName()).contains(query))
					.limit(limit)
					.forEach(entry -> classes.add(classJson(project, entry)));

			JsonObject result = new JsonObject();
			result.add("classes", classes);
			return result;
		}
	}

	private static class SearchMembersTool extends BaseTool {
		SearchMembersTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "search_members";
		}

		@Override
		public String description() {
			return "Search methods and fields by name, owner, or descriptor.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("query", stringProperty("Substring to search."));
			properties.add("kind", stringProperty("method, field, or all. Defaults to all."));
			properties.add("className", stringProperty("Optional owner class filter."));
			properties.add("limit", integerProperty("Maximum number of members to return."));
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			String query = lower(getString(arguments, "query"));
			String kind = getString(arguments, "kind", "all");
			String className = getString(arguments, "className");
			int limit = limit(arguments, 100);
			JsonArray members = new JsonArray();

			if (!kind.equals("field")) {
				project.getJarIndex().getEntryIndex().getMethods().stream()
						.sorted(Comparator.comparing(MethodEntry::toString))
						.filter(entry -> matchesOwner(entry, className))
						.filter(entry -> query == null || matchesMethod(entry, query))
						.limit(limit)
						.forEach(entry -> members.add(methodJson(project, entry)));
			}

			if (!kind.equals("method") && members.size() < limit) {
				project.getJarIndex().getEntryIndex().getFields().stream()
						.sorted(Comparator.comparing(FieldEntry::toString))
						.filter(entry -> matchesOwner(entry, className))
						.filter(entry -> query == null || matchesField(entry, query))
						.limit(limit - members.size())
						.forEach(entry -> members.add(fieldJson(project, entry)));
			}

			JsonObject result = new JsonObject();
			result.add("members", members);
			return result;
		}
	}

	private static class ClassStructureTool extends BaseTool {
		ClassStructureTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "get_class_structure";
		}

		@Override
		public String description() {
			return "Return fields, methods, and inheritance for a class.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Obfuscated JVM class name."));
			properties.add("includeMethods", booleanProperty("Include methods. Defaults to true."));
			properties.add("includeFields", booleanProperty("Include fields. Defaults to true."));
			properties.add("limit", integerProperty("Maximum methods and fields per section."));
			require(schema, "className");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
			int limit = limit(arguments, 200);
			JsonObject result = classJson(project, entry);
			result.addProperty("access", project.getJarIndex().getEntryIndex().getAccess(entry));
			result.add("parents", classArray(project, project.getJarIndex().getInheritanceIndex().getParents(entry), limit));
			result.add("children", classArray(project, project.getJarIndex().getInheritanceIndex().getChildren(entry), limit));

			if (getBoolean(arguments, "includeFields", true)) {
				JsonArray fields = new JsonArray();
				project.getJarIndex().getEntryIndex().getFields().stream()
						.filter(field -> field.getParent().equals(entry))
						.sorted(Comparator.comparing(FieldEntry::toString))
						.limit(limit)
						.forEach(field -> fields.add(fieldJson(project, field)));
				result.add("fields", fields);
			}

			if (getBoolean(arguments, "includeMethods", true)) {
				JsonArray methods = new JsonArray();
				project.getJarIndex().getEntryIndex().getMethods().stream()
						.filter(method -> method.getParent().equals(entry))
						.sorted(Comparator.comparing(MethodEntry::toString))
						.limit(limit)
						.forEach(method -> methods.add(methodJson(project, method)));
				result.add("methods", methods);
			}

			return result;
		}
	}

	private static class ClassBytecodeSummaryTool extends BaseTool {
		ClassBytecodeSummaryTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "class_bytecode_summary";
		}

		@Override
		public String description() {
			return "Return raw ASM class metadata and method instruction counts.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Obfuscated JVM class name."));
			properties.add("includeMethods", booleanProperty("Include per-method instruction counts. Defaults to true."));
			require(schema, "className");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
			ClassNode node = requireClassNode(entry);
			JsonObject result = classJson(project, entry);
			result.addProperty("version", node.version);
			result.addProperty("access", node.access);
			result.addProperty("superName", node.superName);
			result.add("interfaces", strings(node.interfaces));
			result.addProperty("fieldCount", node.fields.size());
			result.addProperty("methodCount", node.methods.size());

			if (getBoolean(arguments, "includeMethods", true)) {
				JsonArray methods = new JsonArray();

				for (MethodNode method : node.methods) {
					JsonObject item = new JsonObject();
					item.addProperty("name", method.name);
					item.addProperty("desc", method.desc);
					item.addProperty("access", method.access);
					item.addProperty("instructions", method.instructions.size());
					methods.add(item);
				}

				result.add("methods", methods);
			}

			return result;
		}

		private ClassNode requireClassNode(ClassEntry entry) throws McpException {
			ClassNode node = project.getClassProvider().get(entry.getFullName());

			if (node == null) {
				throw new McpException(INVALID_PARAMS, "Class bytes not available: " + entry.getFullName());
			}

			return node;
		}
	}

	private static class ClassConstantsTool extends BaseTool {
		ClassConstantsTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "class_constants";
		}

		@Override
		public String description() {
			return "Extract LDC string and numeric constants from a class.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Obfuscated JVM class name."));
			properties.add("limit", integerProperty("Maximum number of constants to return."));
			require(schema, "className");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
			ClassNode node = project.getClassProvider().get(entry.getFullName());

			if (node == null) {
				throw new McpException(INVALID_PARAMS, "Class bytes not available: " + entry.getFullName());
			}

			JsonArray constants = new JsonArray();
			int limit = limit(arguments, 200);

			for (MethodNode method : node.methods) {
				for (AbstractInsnNode instruction : method.instructions) {
					if (instruction instanceof LdcInsnNode ldc) {
						JsonObject item = new JsonObject();
						item.addProperty("method", method.name);
						item.addProperty("desc", method.desc);
						item.addProperty("type", ldc.cst == null ? "null" : ldc.cst.getClass().getSimpleName());
						item.addProperty("value", String.valueOf(ldc.cst));
						constants.add(item);

						if (constants.size() >= limit) {
							break;
						}
					}
				}

				if (constants.size() >= limit) {
					break;
				}
			}

			JsonObject result = classJson(project, entry);
			result.add("constants", constants);
			return result;
		}
	}

	private static class EntryPointsTool extends BaseTool {
		EntryPointsTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "entry_points";
		}

		@Override
		public String description() {
			return "Find likely static entry point and lifecycle methods.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			schema.getAsJsonObject("properties").add("limit", integerProperty("Maximum methods to return."));
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			JsonArray methods = new JsonArray();
			int limit = limit(arguments, 100);

			project.getJarIndex().getEntryIndex().getMethods().stream()
					.filter(this::isEntryPointLike)
					.sorted(Comparator.comparing(MethodEntry::toString))
					.limit(limit)
					.forEach(method -> methods.add(methodJson(project, method)));

			JsonObject result = new JsonObject();
			result.add("methods", methods);
			return result;
		}

		private boolean isEntryPointLike(MethodEntry entry) {
			String name = entry.getName();
			String desc = entry.getDesc().toString();
			return name.equals("main") && desc.equals("([Ljava/lang/String;)V")
					|| name.equals("premain")
					|| name.equals("agentmain")
					|| name.equals("onInitialize")
					|| name.equals("init")
					|| name.equals("start")
					|| name.equals("run");
		}
	}

	private static class DecompileClassTool extends BaseTool {
		DecompileClassTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "decompile_class";
		}

		@Override
		public String description() {
			return "Decompile a class using bytecode or CFR output.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Obfuscated JVM class name."));
			properties.add("decompiler", stringProperty("bytecode or cfr. Defaults to bytecode."));
			properties.add("useMappings", booleanProperty("Apply current mappings. Defaults to true."));
			properties.add("maxChars", integerProperty("Maximum source characters to return."));
			require(schema, "className");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
			String decompilerName = getString(arguments, "decompiler", "bytecode");
			int maxChars = Math.max(1000, getInt(arguments, "maxChars", 50000));
			Decompiler decompiler = createDecompiler(decompilerName);
			String source = decompiler.getSource(entry.getFullName(), getBoolean(arguments, "useMappings", true) ? project.getMapper() : null).asString();
			boolean truncated = source.length() > maxChars;

			JsonObject result = classJson(project, entry);
			result.addProperty("decompiler", decompilerName);
			result.addProperty("truncated", truncated);
			result.addProperty("source", truncated ? source.substring(0, maxChars) : source);
			return result;
		}

		private Decompiler createDecompiler(String name) throws McpException {
			SourceSettings settings = new SourceSettings(false, false);

			if (name.equals("bytecode")) {
				return new BytecodeDecompiler(project.getClassProvider(), settings);
			} else if (name.equals("cfr")) {
				return new CfrDecompiler(project.getClassProvider(), settings);
			} else {
				throw new McpException(INVALID_PARAMS, "Unknown decompiler: " + name);
			}
		}
	}

	private static class ReferencesTool extends BaseTool {
		ReferencesTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "get_references";
		}

		@Override
		public String description() {
			return "Find references to a class, method, or field.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("type", stringProperty("class, method, or field."));
			properties.add("className", stringProperty("Class name or owner."));
			properties.add("name", stringProperty("Method or field name."));
			properties.add("desc", stringProperty("Method or field descriptor."));
			properties.add("limit", integerProperty("Maximum number of references to return."));
			require(schema, "type", "className");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			String type = getString(arguments, "type");
			int limit = limit(arguments, 100);
			JsonArray references = new JsonArray();

			if ("class".equals(type)) {
				ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
				addReferences(references, project.getJarIndex().getReferenceIndex().getReferencesToClass(entry), limit);
			} else if ("method".equals(type)) {
				MethodEntry entry = requireKnownMethod(project, arguments);
				addReferences(references, project.getJarIndex().getReferenceIndex().getReferencesToMethod(entry), limit);
			} else if ("field".equals(type)) {
				FieldEntry entry = requireKnownField(project, arguments);
				addReferences(references, project.getJarIndex().getReferenceIndex().getReferencesToField(entry), limit);
			} else {
				throw new McpException(INVALID_PARAMS, "Unknown reference type: " + type);
			}

			JsonObject result = new JsonObject();
			result.add("references", references);
			return result;
		}
	}

	private static class MethodCallsTool extends BaseTool {
		MethodCallsTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "get_method_calls";
		}

		@Override
		public String description() {
			return "List methods directly called by a method.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Owner class."));
			properties.add("name", stringProperty("Method name."));
			properties.add("desc", stringProperty("Method descriptor."));
			properties.add("limit", integerProperty("Maximum number of calls to return."));
			require(schema, "className", "name", "desc");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			MethodEntry entry = requireKnownMethod(project, arguments);
			JsonArray calls = new JsonArray();
			project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(entry).stream()
					.sorted(Comparator.comparing(MethodEntry::toString))
					.limit(limit(arguments, 100))
					.forEach(method -> calls.add(methodJson(project, method)));

			JsonObject result = methodJson(project, entry);
			result.add("calls", calls);
			return result;
		}
	}

	private static class InheritanceTool extends BaseTool {
		InheritanceTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "get_inheritance";
		}

		@Override
		public String description() {
			return "Inspect class parents, children, ancestors, or descendants.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Obfuscated JVM class name."));
			properties.add("direction", stringProperty("parents, children, ancestors, or descendants."));
			properties.add("limit", integerProperty("Maximum number of classes to return."));
			require(schema, "className");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
			String direction = getString(arguments, "direction", "parents");
			Collection<ClassEntry> classes;

			if (direction.equals("parents")) {
				classes = project.getJarIndex().getInheritanceIndex().getParents(entry);
			} else if (direction.equals("children")) {
				classes = project.getJarIndex().getInheritanceIndex().getChildren(entry);
			} else if (direction.equals("ancestors")) {
				classes = project.getJarIndex().getInheritanceIndex().getAncestors(entry);
			} else if (direction.equals("descendants")) {
				classes = project.getJarIndex().getInheritanceIndex().getDescendants(entry);
			} else {
				throw new McpException(INVALID_PARAMS, "Unknown direction: " + direction);
			}

			JsonObject result = classJson(project, entry);
			result.addProperty("direction", direction);
			result.add("classes", classArray(project, classes, limit(arguments, 100)));
			return result;
		}
	}

	private static class SuggestAnalysisTargetsTool extends BaseTool {
		SuggestAnalysisTargetsTool(EnigmaProject project) {
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

		private int scoreMethod(MethodEntry entry) {
			int score = project.getJarIndex().getReferenceIndex().getMethodsReferencedBy(entry).size();
			score += project.getJarIndex().getReferenceIndex().getReferencesToMethod(entry).size();

			if (entry.getName().equals("main")) {
				score += 1000;
			}

			if (entry.isConstructor()) {
				score += 10;
			}

			return score;
		}
	}

	private static class GetMappingTool extends BaseTool {
		GetMappingTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "get_mapping";
		}

		@Override
		public String description() {
			return "Get the current mapping for a class, method, or field entry.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("type", stringProperty("class, method, or field."));
			properties.add("className", stringProperty("Class name or owner."));
			properties.add("name", stringProperty("Method or field name."));
			properties.add("desc", stringProperty("Method or field descriptor."));
			require(schema, "type", "className");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			Entry<?> entry = requireEntry(project, arguments);
			return mappingResult(project, entry, project.getMapper().getDeobfMapping(entry));
		}
	}

	private static class SetClassMappingTool extends BaseTool {
		SetClassMappingTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "set_class_mapping";
		}

		@Override
		public String description() {
			return "Set an in-memory class mapping for the open project.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Obfuscated JVM class name."));
			properties.add("targetName", stringProperty("New deobfuscated class name, or empty to clear."));
			properties.add("javadoc", stringProperty("Optional javadoc text."));
			require(schema, "className", "targetName");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
			return setMapping(project, entry, arguments);
		}
	}

	private static class SetMethodMappingTool extends BaseTool {
		SetMethodMappingTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "set_method_mapping";
		}

		@Override
		public String description() {
			return "Set an in-memory method mapping for the open project.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Owner class."));
			properties.add("name", stringProperty("Obfuscated method name."));
			properties.add("desc", stringProperty("Method descriptor."));
			properties.add("targetName", stringProperty("New deobfuscated method name, or empty to clear."));
			properties.add("javadoc", stringProperty("Optional javadoc text."));
			require(schema, "className", "name", "desc", "targetName");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			return setMapping(project, requireKnownMethod(project, arguments), arguments);
		}
	}

	private static class SetFieldMappingTool extends BaseTool {
		SetFieldMappingTool(EnigmaProject project) {
			super(project);
		}

		@Override
		public String name() {
			return "set_field_mapping";
		}

		@Override
		public String description() {
			return "Set an in-memory field mapping for the open project.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject properties = schema.getAsJsonObject("properties");
			properties.add("className", stringProperty("Owner class."));
			properties.add("name", stringProperty("Obfuscated field name."));
			properties.add("desc", stringProperty("Field descriptor."));
			properties.add("targetName", stringProperty("New deobfuscated field name, or empty to clear."));
			properties.add("javadoc", stringProperty("Optional javadoc text."));
			require(schema, "className", "name", "desc", "targetName");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws McpException {
			return setMapping(project, requireKnownField(project, arguments), arguments);
		}
	}

	private static JsonObject setMapping(EnigmaProject project, Entry<?> entry, JsonObject arguments) {
		String targetName = getString(arguments, "targetName");

		if (targetName != null && targetName.isEmpty()) {
			targetName = null;
		}

		ValidationContext vc = new ValidationContext();
		EntryMapping mapping = new EntryMapping(targetName, getString(arguments, "javadoc"));
		boolean changed = project.getMapper().putMapping(vc, entry, mapping);
		JsonObject result = mappingResult(project, entry, project.getMapper().getDeobfMapping(entry));
		result.addProperty("success", vc.canProceed());
		result.addProperty("changed", changed);
		result.add("messages", messages(vc));
		return result;
	}

	private static Entry<?> requireEntry(EnigmaProject project, JsonObject arguments) throws McpException {
		String type = getString(arguments, "type");

		if ("class".equals(type)) {
			return requireKnownClass(project, getString(arguments, "className"));
		} else if ("method".equals(type)) {
			return requireKnownMethod(project, arguments);
		} else if ("field".equals(type)) {
			return requireKnownField(project, arguments);
		} else {
			throw new McpException(INVALID_PARAMS, "Unknown entry type: " + type);
		}
	}

	private static ClassEntry requireKnownClass(EnigmaProject project, String className) throws McpException {
		if (className == null) {
			throw new McpException(INVALID_PARAMS, "Missing className");
		}

		ClassEntry entry = ClassEntry.parse(className);

		if (!project.getJarIndex().getEntryIndex().hasClass(entry)) {
			throw new McpException(INVALID_PARAMS, "Unknown class: " + className);
		}

		return entry;
	}

	private static MethodEntry requireKnownMethod(EnigmaProject project, JsonObject arguments) throws McpException {
		String owner = getString(arguments, "className");
		String name = getString(arguments, "name");
		String desc = getString(arguments, "desc");

		if (owner == null || name == null || desc == null) {
			throw new McpException(INVALID_PARAMS, "className, name, and desc are required");
		}

		MethodEntry entry = MethodEntry.parse(owner, name, desc);

		if (!project.getJarIndex().getEntryIndex().hasMethod(entry)) {
			throw new McpException(INVALID_PARAMS, "Unknown method: " + entry);
		}

		return entry;
	}

	private static FieldEntry requireKnownField(EnigmaProject project, JsonObject arguments) throws McpException {
		String owner = getString(arguments, "className");
		String name = getString(arguments, "name");
		String desc = getString(arguments, "desc");

		if (owner == null || name == null || desc == null) {
			throw new McpException(INVALID_PARAMS, "className, name, and desc are required");
		}

		FieldEntry entry = FieldEntry.parse(owner, name, desc);

		if (!project.getJarIndex().getEntryIndex().hasField(entry)) {
			throw new McpException(INVALID_PARAMS, "Unknown field: " + entry);
		}

		return entry;
	}

	private static JsonObject classJson(EnigmaProject project, ClassEntry entry) {
		JsonObject result = new JsonObject();
		result.addProperty("type", "class");
		result.addProperty("obfuscated", entry.getFullName());
		result.addProperty("deobfuscated", project.getMapper().deobfuscate(entry).getFullName());
		return result;
	}

	private static JsonObject methodJson(EnigmaProject project, MethodEntry entry) {
		MethodEntry deobfuscated = project.getMapper().deobfuscate(entry);
		JsonObject result = new JsonObject();
		result.addProperty("type", "method");
		result.addProperty("owner", entry.getParent().getFullName());
		result.addProperty("name", entry.getName());
		result.addProperty("desc", entry.getDesc().toString());
		result.addProperty("deobfuscatedOwner", deobfuscated.getParent().getFullName());
		result.addProperty("deobfuscatedName", deobfuscated.getName());
		result.addProperty("deobfuscatedDesc", deobfuscated.getDesc().toString());
		result.addProperty("access", project.getJarIndex().getEntryIndex().getAccess(entry));
		return result;
	}

	private static JsonObject fieldJson(EnigmaProject project, FieldEntry entry) {
		FieldEntry deobfuscated = project.getMapper().deobfuscate(entry);
		JsonObject result = new JsonObject();
		result.addProperty("type", "field");
		result.addProperty("owner", entry.getParent().getFullName());
		result.addProperty("name", entry.getName());
		result.addProperty("desc", entry.getDesc().toString());
		result.addProperty("deobfuscatedOwner", deobfuscated.getParent().getFullName());
		result.addProperty("deobfuscatedName", deobfuscated.getName());
		result.addProperty("deobfuscatedDesc", deobfuscated.getDesc().toString());
		result.addProperty("access", project.getJarIndex().getEntryIndex().getAccess(entry));
		return result;
	}

	private static JsonObject mappingResult(EnigmaProject project, Entry<?> entry, EntryMapping mapping) {
		JsonObject result;

		if (entry instanceof ClassEntry classEntry) {
			result = classJson(project, classEntry);
		} else if (entry instanceof MethodEntry methodEntry) {
			result = methodJson(project, methodEntry);
		} else if (entry instanceof FieldEntry fieldEntry) {
			result = fieldJson(project, fieldEntry);
		} else {
			result = new JsonObject();
			result.addProperty("entry", entry.toString());
		}

		result.addProperty("targetName", mapping.targetName());
		result.addProperty("javadoc", mapping.javadoc());
		result.addProperty("accessModifier", mapping.accessModifier().name());
		return result;
	}

	private static JsonArray classArray(EnigmaProject project, Collection<ClassEntry> entries, int limit) {
		JsonArray result = new JsonArray();
		entries.stream()
				.sorted(Comparator.comparing(ClassEntry::getFullName))
				.limit(limit)
				.forEach(entry -> result.add(classJson(project, entry)));
		return result;
	}

	private static JsonArray strings(Collection<String> strings) {
		JsonArray result = new JsonArray();

		for (String string : strings) {
			result.add(string);
		}

		return result;
	}

	private static void addReferences(JsonArray target, Collection<? extends EntryReference<?, ?>> references, int limit) {
		references.stream()
				.sorted(Comparator.comparing(EntryReference::toString))
				.limit(limit)
				.forEach(reference -> target.add(referenceJson(reference)));
	}

	private static JsonObject referenceJson(EntryReference<?, ?> reference) {
		JsonObject result = new JsonObject();
		result.addProperty("entry", reference.entry.toString());
		result.addProperty("context", reference.context == null ? null : reference.context.toString());
		result.addProperty("locationClass", reference.getLocationClassEntry().getFullName());
		result.addProperty("targetType", reference.targetType.getKind().name());
		result.addProperty("declaration", reference.isDeclaration());
		result.addProperty("named", reference.isNamed());
		return result;
	}

	private static boolean matchesOwner(Entry<?> entry, String className) {
		return className == null || entry.getContainingClass().getFullName().equals(className);
	}

	private static boolean matchesMethod(MethodEntry entry, String query) {
		return lower(entry.getParent().getFullName()).contains(query) || lower(entry.getName()).contains(query) || lower(entry.getDesc().toString()).contains(query);
	}

	private static boolean matchesField(FieldEntry entry, String query) {
		return lower(entry.getParent().getFullName()).contains(query) || lower(entry.getName()).contains(query) || lower(entry.getDesc().toString()).contains(query);
	}

	private static JsonArray messages(ValidationContext vc) {
		JsonArray result = new JsonArray();

		for (ParameterizedMessage message : vc.getMessages()) {
			JsonObject item = new JsonObject();
			item.addProperty("type", message.message.type.name());
			item.addProperty("text", message.getText());
			item.addProperty("longText", message.getLongText());
			result.add(item);
		}

		return result;
	}

	private static void require(JsonObject schema, String... names) {
		JsonArray required = new JsonArray();

		for (String name : names) {
			required.add(name);
		}

		schema.add("required", required);
	}

	private static int limit(JsonObject object, int defaultValue) {
		return Math.max(1, getInt(object, "limit", defaultValue));
	}

	private static String lower(String value) {
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	private static String getString(JsonObject object, String name) {
		if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
			return null;
		}

		return object.get(name).getAsString();
	}

	private static String getString(JsonObject object, String name, String defaultValue) {
		String value = getString(object, name);
		return value == null ? defaultValue : value;
	}

	private static int getInt(JsonObject object, String name, int defaultValue) {
		if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
			return defaultValue;
		}

		return object.get(name).getAsInt();
	}

	private static boolean getBoolean(JsonObject object, String name, boolean defaultValue) {
		if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
			return defaultValue;
		}

		return object.get(name).getAsBoolean();
	}
}
