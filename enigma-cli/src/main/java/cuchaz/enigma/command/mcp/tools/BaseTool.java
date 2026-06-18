package cuchaz.enigma.command.mcp.tools;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.command.mcp.McpTool;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.validation.ParameterizedMessage;
import cuchaz.enigma.utils.validation.ValidationContext;

public abstract class BaseTool implements McpTool {
	protected static final int INVALID_PARAMS = -32602;

	protected final EnigmaProject project;

	protected BaseTool(EnigmaProject project) {
		this.project = project;
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", new JsonObject());
		return schema;
	}

	// --- Schema helpers ---

	protected JsonObject stringProperty(String description) {
		JsonObject property = new JsonObject();
		property.addProperty("type", "string");
		property.addProperty("description", description);
		return property;
	}

	protected JsonObject integerProperty(String description) {
		JsonObject property = new JsonObject();
		property.addProperty("type", "integer");
		property.addProperty("description", description);
		return property;
	}

	protected JsonObject booleanProperty(String description) {
		JsonObject property = new JsonObject();
		property.addProperty("type", "boolean");
		property.addProperty("description", description);
		return property;
	}

	protected static void require(JsonObject schema, String... names) {
		JsonArray required = new JsonArray();
		for (String name : names) {
			required.add(name);
		}
		schema.add("required", required);
	}

	// --- Argument helpers ---

	protected static int limit(JsonObject object, int defaultValue) {
		return Math.max(1, getInt(object, "limit", defaultValue));
	}

	protected static String lower(String value) {
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	protected static String getString(JsonObject object, String name) {
		if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
			return null;
		}
		return object.get(name).getAsString();
	}

	protected static String getString(JsonObject object, String name, String defaultValue) {
		String value = getString(object, name);
		return value == null ? defaultValue : value;
	}

	protected static int getInt(JsonObject object, String name, int defaultValue) {
		if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
			return defaultValue;
		}
		return object.get(name).getAsInt();
	}

	protected static boolean getBoolean(JsonObject object, String name, boolean defaultValue) {
		if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
			return defaultValue;
		}
		return object.get(name).getAsBoolean();
	}

	// --- Entry resolution helpers ---

	protected static ClassEntry requireKnownClass(EnigmaProject project, String className) throws McpException {
		if (className == null) {
			throw new McpException(INVALID_PARAMS, "Missing className");
		}
		ClassEntry entry = ClassEntry.parse(className);
		if (!project.getJarIndex().getEntryIndex().hasClass(entry)) {
			throw new McpException(INVALID_PARAMS, "Unknown class: " + className);
		}
		return entry;
	}

	protected static MethodEntry requireKnownMethod(EnigmaProject project, JsonObject arguments) throws McpException {
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

	protected static FieldEntry requireKnownField(EnigmaProject project, JsonObject arguments) throws McpException {
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

	protected static Entry<?> requireEntry(EnigmaProject project, JsonObject arguments) throws McpException {
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

	// --- JSON output helpers ---

	protected static JsonObject classJson(EnigmaProject project, ClassEntry entry) {
		JsonObject result = new JsonObject();
		result.addProperty("type", "class");
		result.addProperty("obfuscated", entry.getFullName());
		result.addProperty("deobfuscated", project.getMapper().deobfuscate(entry).getFullName());
		return result;
	}

	protected static JsonObject methodJson(EnigmaProject project, MethodEntry entry) {
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

	protected static JsonObject fieldJson(EnigmaProject project, FieldEntry entry) {
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

	protected static JsonObject mappingResult(EnigmaProject project, Entry<?> entry, EntryMapping mapping) {
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

	protected static JsonArray classArray(EnigmaProject project, Collection<ClassEntry> entries, int limit) {
		JsonArray result = new JsonArray();
		entries.stream()
				.sorted(Comparator.comparing(ClassEntry::getFullName))
				.limit(limit)
				.forEach(entry -> result.add(classJson(project, entry)));
		return result;
	}

	protected static JsonArray strings(Collection<String> strings) {
		JsonArray result = new JsonArray();
		for (String string : strings) {
			result.add(string);
		}
		return result;
	}

	protected static void addReferences(JsonArray target, Collection<? extends EntryReference<?, ?>> references, int limit) {
		references.stream()
				.sorted(Comparator.comparing(EntryReference::toString))
				.limit(limit)
				.forEach(reference -> target.add(referenceJson(reference)));
	}

	protected static JsonObject referenceJson(EntryReference<?, ?> reference) {
		JsonObject result = new JsonObject();
		result.addProperty("entry", reference.entry.toString());
		result.addProperty("context", reference.context == null ? null : reference.context.toString());
		result.addProperty("locationClass", reference.getLocationClassEntry().getFullName());
		result.addProperty("targetType", reference.targetType.getKind().name());
		result.addProperty("declaration", reference.isDeclaration());
		result.addProperty("named", reference.isNamed());
		return result;
	}

	protected static JsonArray messages(ValidationContext vc) {
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

	protected static boolean matchesOwner(Entry<?> entry, String className) {
		return className == null || entry.getContainingClass().getFullName().equals(className);
	}
}
