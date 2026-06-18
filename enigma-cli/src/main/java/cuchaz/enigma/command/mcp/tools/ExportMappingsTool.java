package cuchaz.enigma.command.mcp.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.serde.MappingFormat;

public class ExportMappingsTool extends BaseTool {
	public ExportMappingsTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "export_mappings";
	}

	@Override
	public String description() {
		return "Export current mappings to a file or directory in a specified format.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("path", stringProperty("Output file or directory path."));
		properties.add("format", stringProperty("Format: enigma_file, enigma_directory, tiny_v2, srg_file. Defaults to enigma_directory."));
		require(schema, "path");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String pathStr = getString(arguments, "path");
		String formatStr = getString(arguments, "format", "enigma_directory");

		if (pathStr == null || pathStr.isEmpty()) {
			throw new McpException(INVALID_PARAMS, "path is required");
		}

		MappingFormat format;
		try {
			format = MappingFormat.valueOf(formatStr.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new McpException(INVALID_PARAMS, "Unknown format: " + formatStr + ". Valid: enigma_file, enigma_directory, tiny_v2, srg_file");
		}

		Path outputPath = Paths.get(pathStr);

		try {
			format.write(project.getMapper().getObfToDeobf(), project.getMapper().takeMappingDelta(), outputPath, ProgressListener.none(), project.getEnigma().getProfile().getMappingSaveParameters());
		} catch (Exception e) {
			throw new McpException(-32000, "Failed to export mappings: " + e.getMessage());
		}

		JsonObject result = new JsonObject();
		result.addProperty("success", true);
		result.addProperty("path", outputPath.toAbsolutePath().toString());
		result.addProperty("format", formatStr);
		return result;
	}
}
