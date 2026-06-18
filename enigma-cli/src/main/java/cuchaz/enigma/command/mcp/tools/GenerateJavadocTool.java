package cuchaz.enigma.command.mcp.tools;

import java.util.Comparator;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class GenerateJavadocTool extends BaseTool {
	public GenerateJavadocTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "generate_javadoc";
	}

	@Override
	public String description() {
		return "Generate Javadoc comments for a class based on analysis: method behaviors, field purposes, inheritance context.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Class to generate javadoc for."));
		require(schema, "className");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		ClassEntry entry = requireKnownClass(project, getString(arguments, "className"));
		ClassEntry deobf = project.getMapper().deobfuscate(entry);

		StringBuilder javadoc = new StringBuilder();
		javadoc.append("/**\n");
		javadoc.append(" * ").append(deobf.getSimpleName()).append("\n");
		javadoc.append(" *\n");

		// Inheritance info
		var parents = project.getJarIndex().getInheritanceIndex().getParents(entry);
		if (!parents.isEmpty()) {
			javadoc.append(" * <p>Inheritance:\n");
			for (ClassEntry parent : parents) {
				ClassEntry deobfParent = project.getMapper().deobfuscate(parent);
				javadoc.append(" * <ul><li>").append(deobfParent.getFullName()).append("</li></ul>\n");
			}
		}

		// Method summary
		long methodCount = project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry) && !m.isConstructor()).count();
		long fieldCount = project.getJarIndex().getEntryIndex().getFields().stream()
				.filter(f -> f.getParent().equals(entry)).count();

		javadoc.append(" *\n");
		javadoc.append(" * <p>Contains ").append(methodCount).append(" methods and ").append(fieldCount).append(" fields.\n");

		int refs = project.getJarIndex().getReferenceIndex().getReferencesToClass(entry).size();
		javadoc.append(" * Referenced by ").append(refs).append(" call sites.\n");
		javadoc.append(" */");

		// Generate method javadocs
		StringBuilder methodDocs = new StringBuilder();
		project.getJarIndex().getEntryIndex().getMethods().stream()
				.filter(m -> m.getParent().equals(entry) && !m.isConstructor())
				.limit(20)
				.forEach(m -> {
					MethodEntry deobfMethod = project.getMapper().deobfuscate(m);
					methodDocs.append("\n/** ");
					String desc = m.getDesc().toString();
					String ret = desc.substring(desc.lastIndexOf(')') + 1);
					if (ret.equals("V")) methodDocs.append("Performs an operation.");
					else if (ret.equals("Z")) methodDocs.append("Tests a condition.");
					else methodDocs.append("Returns a value.");

					int callCount = project.getJarIndex().getReferenceIndex().getReferencesToMethod(m).size();
					methodDocs.append(" Called from ").append(callCount).append(" sites.");
					methodDocs.append(" */\n");
					methodDocs.append("// ").append(deobfMethod.getName()).append(deobfMethod.getDesc()).append("\n");
				});

		JsonObject result = classJson(project, entry);
		result.addProperty("classJavadoc", javadoc.toString());
		result.addProperty("methodDocs", methodDocs.toString());
		return result;
	}
}
