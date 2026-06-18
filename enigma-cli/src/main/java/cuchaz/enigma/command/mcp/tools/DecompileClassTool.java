package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.source.Decompiler;
import cuchaz.enigma.source.SourceSettings;
import cuchaz.enigma.source.bytecode.BytecodeDecompiler;
import cuchaz.enigma.source.cfr.CfrDecompiler;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class DecompileClassTool extends BaseTool {
	public DecompileClassTool(EnigmaProject project) {
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
