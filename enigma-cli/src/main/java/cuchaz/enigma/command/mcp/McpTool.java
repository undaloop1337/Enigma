package cuchaz.enigma.command.mcp;

import com.google.gson.JsonObject;

public interface McpTool {
	String name();

	String description();

	JsonObject inputSchema();

	JsonObject execute(JsonObject arguments) throws Exception;
}
