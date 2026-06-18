package cuchaz.enigma.command.mcp.tools;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;

public class ProjectInfoTool extends BaseTool {
	public ProjectInfoTool(EnigmaProject project) {
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
