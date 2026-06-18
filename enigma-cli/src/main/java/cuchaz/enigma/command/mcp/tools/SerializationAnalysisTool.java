package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.Opcodes;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class SerializationAnalysisTool extends BaseTool {
	public SerializationAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "serialization_analysis";
	}

	@Override
	public String description() {
		return "Find Serializable/Externalizable classes, custom readObject/writeObject, serialVersionUID, and potential gadget chain candidates.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Optional specific class to analyze."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 50);

		if (className != null) {
			ClassEntry entry = requireKnownClass(project, className);
			return analyzeOne(entry);
		}

		JsonArray results = new JsonArray();
		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;
			if (isSerializable(entry)) {
				JsonObject item = analyzeOne(entry);
				results.add(item);
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("serializableCount", results.size());
		result.add("classes", results);
		return result;
	}

	private boolean isSerializable(ClassEntry entry) {
		var parents = project.getJarIndex().getInheritanceIndex().getAncestors(entry);
		for (ClassEntry parent : parents) {
			String name = parent.getFullName();
			if (name.equals("java/io/Serializable") || name.equals("java/io/Externalizable")) {
				return true;
			}
		}
		return false;
	}

	private JsonObject analyzeOne(ClassEntry entry) {
		JsonObject item = classJson(project, entry);
		ClassNode node = project.getClassProvider().get(entry.getFullName());
		if (node == null) return item;

		// serialVersionUID
		for (FieldNode field : node.fields) {
			if (field.name.equals("serialVersionUID") && (field.access & Opcodes.ACC_STATIC) != 0) {
				item.addProperty("serialVersionUID", field.value != null ? field.value.toString() : "dynamic");
			}
		}

		// Custom serialization methods
		JsonArray customMethods = new JsonArray();
		for (MethodNode method : node.methods) {
			if (method.name.equals("readObject") && method.desc.equals("(Ljava/io/ObjectInputStream;)V")) {
				customMethods.add("readObject");
			} else if (method.name.equals("writeObject") && method.desc.equals("(Ljava/io/ObjectOutputStream;)V")) {
				customMethods.add("writeObject");
			} else if (method.name.equals("readResolve") && method.desc.equals("()Ljava/lang/Object;")) {
				customMethods.add("readResolve");
			} else if (method.name.equals("writeReplace") && method.desc.equals("()Ljava/lang/Object;")) {
				customMethods.add("writeReplace");
			} else if (method.name.equals("readExternal")) {
				customMethods.add("readExternal");
			} else if (method.name.equals("writeExternal")) {
				customMethods.add("writeExternal");
			}
		}
		if (!customMethods.isEmpty()) {
			item.add("customMethods", customMethods);
		}

		// Transient fields
		JsonArray transientFields = new JsonArray();
		for (FieldNode field : node.fields) {
			if ((field.access & Opcodes.ACC_TRANSIENT) != 0) {
				transientFields.add(field.name + ":" + field.desc);
			}
		}
		if (!transientFields.isEmpty()) {
			item.add("transientFields", transientFields);
		}

		// Gadget hint: has readObject AND invokes dangerous methods
		item.addProperty("gadgetRisk", customMethods.toString().contains("readObject"));
		return item;
	}
}
