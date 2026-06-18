package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class SearchAnnotationsTool extends BaseTool {
	public SearchAnnotationsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "search_annotations";
	}

	@Override
	public String description() {
		return "Find classes, methods, or fields annotated with a specific annotation.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("annotation", stringProperty("Annotation class name or substring (e.g. 'Override', 'Deprecated')."));
		properties.add("limit", integerProperty("Maximum number of results."));
		require(schema, "annotation");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String annotation = getString(arguments, "annotation");
		if (annotation == null || annotation.isEmpty()) {
			throw new McpException(INVALID_PARAMS, "annotation is required");
		}
		String query = annotation.toLowerCase(Locale.ROOT);
		int limit = limit(arguments, 100);
		JsonArray results = new JsonArray();

		for (ClassEntry classEntry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;

			ClassNode node = project.getClassProvider().get(classEntry.getFullName());
			if (node == null) continue;

			// Check class annotations
			if (hasAnnotation(node.visibleAnnotations, query) || hasAnnotation(node.invisibleAnnotations, query)) {
				JsonObject item = classJson(project, classEntry);
				item.addProperty("target", "class");
				results.add(item);
			}

			// Check method annotations
			for (MethodNode method : node.methods) {
				if (results.size() >= limit) break;
				if (hasAnnotation(method.visibleAnnotations, query) || hasAnnotation(method.invisibleAnnotations, query)) {
					JsonObject item = new JsonObject();
					item.addProperty("target", "method");
					item.addProperty("class", classEntry.getFullName());
					item.addProperty("method", method.name);
					item.addProperty("desc", method.desc);
					results.add(item);
				}
			}

			// Check field annotations
			for (FieldNode field : node.fields) {
				if (results.size() >= limit) break;
				if (hasAnnotation(field.visibleAnnotations, query) || hasAnnotation(field.invisibleAnnotations, query)) {
					JsonObject item = new JsonObject();
					item.addProperty("target", "field");
					item.addProperty("class", classEntry.getFullName());
					item.addProperty("field", field.name);
					item.addProperty("desc", field.desc);
					results.add(item);
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("query", annotation);
		result.addProperty("matchCount", results.size());
		result.add("matches", results);
		return result;
	}

	private static boolean hasAnnotation(java.util.List<AnnotationNode> annotations, String query) {
		if (annotations == null) return false;
		for (AnnotationNode ann : annotations) {
			if (ann.desc != null && ann.desc.toLowerCase(Locale.ROOT).contains(query)) {
				return true;
			}
		}
		return false;
	}
}
