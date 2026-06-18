package cuchaz.enigma.command.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.JsonObject;

import cuchaz.enigma.EnigmaProject;

public final class DynamicEnigmaMcpTools {
	private DynamicEnigmaMcpTools() {
	}

	public static List<McpTool> create(Supplier<EnigmaProject> projectSupplier, List<McpTool> extraTools) {
		List<McpTool> tools = new ArrayList<>(extraTools);
		List<McpTool> templateTools = EnigmaMcpTools.create(null);

		for (McpTool tool : templateTools) {
			tools.add(new DynamicProjectTool(projectSupplier, tool));
		}

		return tools;
	}

	private static class DynamicProjectTool implements McpTool {
		private final Supplier<EnigmaProject> projectSupplier;
		private final McpTool template;
		private EnigmaProject cachedProject;
		private Map<String, McpTool> cachedTools;

		DynamicProjectTool(Supplier<EnigmaProject> projectSupplier, McpTool template) {
			this.projectSupplier = projectSupplier;
			this.template = template;
		}

		@Override
		public String name() {
			return template.name();
		}

		@Override
		public String description() {
			return template.description();
		}

		@Override
		public JsonObject inputSchema() {
			return template.inputSchema();
		}

		@Override
		public JsonObject execute(JsonObject arguments) throws Exception {
			EnigmaProject project = projectSupplier.get();

			if (project == null) {
				throw new McpException(-32002, "No project is open in Enigma");
			}

			McpTool tool = getToolForProject(project);
			return tool.execute(arguments);
		}

		private McpTool getToolForProject(EnigmaProject project) throws McpException {
			if (cachedProject != project) {
				Map<String, McpTool> toolMap = new HashMap<>();

				for (McpTool t : EnigmaMcpTools.create(project)) {
					toolMap.put(t.name(), t);
				}

				cachedProject = project;
				cachedTools = toolMap;
			}

			McpTool tool = cachedTools.get(template.name());

			if (tool == null) {
				throw new McpException(-32602, "Unknown tool: " + template.name());
			}

			return tool;
		}
	}
}
