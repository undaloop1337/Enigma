package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.source.Decompiler;
import cuchaz.enigma.source.SourceSettings;
import cuchaz.enigma.source.cfr.CfrDecompiler;
import cuchaz.enigma.translation.representation.entry.MethodEntry;

public class DecompileMethodTool extends BaseTool {
	public DecompileMethodTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "decompile_method";
	}

	@Override
	public String description() {
		return "Decompile a specific method, returning only its source code.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Owner class."));
		properties.add("name", stringProperty("Method name."));
		properties.add("desc", stringProperty("Method descriptor."));
		properties.add("useMappings", booleanProperty("Apply current mappings. Defaults to true."));
		require(schema, "className", "name", "desc");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		MethodEntry entry = requireKnownMethod(project, arguments);
		boolean useMappings = getBoolean(arguments, "useMappings", true);

		// Decompile the whole class and extract the method
		SourceSettings settings = new SourceSettings(false, false);
		Decompiler decompiler = new CfrDecompiler(project.getClassProvider(), settings);
		String classSource = decompiler.getSource(entry.getParent().getFullName(), useMappings ? project.getMapper() : null).asString();

		// Try to extract the method from the source
		String methodName = useMappings ? project.getMapper().deobfuscate(entry).getName() : entry.getName();
		String methodSource = extractMethod(classSource, methodName);

		JsonObject result = methodJson(project, entry);
		if (methodSource != null) {
			result.addProperty("source", methodSource);
			result.addProperty("extracted", true);
		} else {
			result.addProperty("source", classSource);
			result.addProperty("extracted", false);
		}
		return result;
	}

	private static String extractMethod(String source, String methodName) {
		// Simple heuristic: find the method signature line and extract until matching brace
		String[] lines = source.split("\n");
		int start = -1;
		int braceDepth = 0;
		boolean inMethod = false;

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();

			if (!inMethod && line.contains(methodName + "(")) {
				// Check if this looks like a method declaration
				if (line.contains("{") || (i + 1 < lines.length && lines[i + 1].trim().startsWith("{"))) {
					start = i;
					inMethod = true;
					braceDepth = 0;
				}
			}

			if (inMethod) {
				for (char c : lines[i].toCharArray()) {
					if (c == '{') braceDepth++;
					else if (c == '}') braceDepth--;
				}

				if (braceDepth <= 0 && start != i) {
					StringBuilder sb = new StringBuilder();
					for (int j = start; j <= i; j++) {
						sb.append(lines[j]).append("\n");
					}
					return sb.toString();
				}
			}
		}

		return null;
	}
}
