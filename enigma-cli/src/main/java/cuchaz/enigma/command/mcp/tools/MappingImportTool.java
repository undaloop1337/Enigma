package cuchaz.enigma.command.mcp.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.serde.MappingFormat;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.utils.validation.ValidationContext;

public class MappingImportTool extends BaseTool {
	public MappingImportTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "mapping_import";
	}

	@Override
	public String description() {
		return "Import mappings from an external file (Enigma, Tiny v2, SRG, Proguard format) and merge into the current project.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("path", stringProperty("Path to the mapping file or directory."));
		properties.add("format", stringProperty("Format: enigma_file, enigma_directory, tiny_v2, srg_file, proguard. Defaults to auto-detect."));
		properties.add("overwrite", booleanProperty("Overwrite existing mappings on conflict. Defaults to false."));
		require(schema, "path");
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String pathStr = getString(arguments, "path");
		String formatStr = getString(arguments, "format");
		boolean overwrite = getBoolean(arguments, "overwrite", false);

		if (pathStr == null || pathStr.isEmpty()) {
			throw new McpException(INVALID_PARAMS, "path is required");
		}

		Path path = Paths.get(pathStr);
		if (!Files.exists(path)) {
			throw new McpException(INVALID_PARAMS, "File not found: " + pathStr);
		}

		MappingFormat format;
		if (formatStr != null && !formatStr.isEmpty()) {
			try {
				format = MappingFormat.valueOf(formatStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new McpException(INVALID_PARAMS, "Unknown format: " + formatStr);
			}
		} else {
			// Auto-detect
			if (Files.isDirectory(path)) {
				format = MappingFormat.ENIGMA_DIRECTORY;
			} else {
				String name = path.getFileName().toString();
				if (name.endsWith(".tiny")) {
					format = MappingFormat.TINY_V2;
				} else if (name.endsWith(".srg") || name.endsWith(".tsrg")) {
					format = MappingFormat.SRG_FILE;
				} else {
					format = MappingFormat.ENIGMA_FILE;
				}
			}
		}

		EntryTree<EntryMapping> imported;
		try {
			imported = format.read(path, ProgressListener.none(), project.getEnigma().getProfile().getMappingSaveParameters(), project.getJarIndex());
		} catch (Exception e) {
			throw new McpException(-32000, "Failed to read mappings: " + e.getMessage());
		}

		int applied = 0;
		int skipped = 0;
		int conflicts = 0;

		for (Entry<?> entry : imported.getAllEntries().toList()) {
			EntryMapping mapping = imported.get(entry);
			if (mapping == null || mapping.targetName() == null) continue;

			// Check if entry exists in our jar
			if (!project.getJarIndex().getEntryIndex().hasEntry(entry)) {
				skipped++;
				continue;
			}

			// Check for existing mapping
			EntryMapping existing = project.getMapper().getDeobfMapping(entry);
			if (existing.targetName() != null && !overwrite) {
				conflicts++;
				continue;
			}

			ValidationContext vc = new ValidationContext();
			project.getMapper().putMapping(vc, entry, mapping);
			if (vc.canProceed()) {
				applied++;
			} else {
				skipped++;
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("success", true);
		result.addProperty("format", format.name());
		result.addProperty("applied", applied);
		result.addProperty("skipped", skipped);
		result.addProperty("conflicts", conflicts);
		return result;
	}
}
