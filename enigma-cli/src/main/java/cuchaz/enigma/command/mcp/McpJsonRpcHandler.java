package cuchaz.enigma.command.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

public class McpJsonRpcHandler {
	private static final int PARSE_ERROR = -32700;
	private static final int INVALID_REQUEST = -32600;
	private static final int METHOD_NOT_FOUND = -32601;
	private static final int INVALID_PARAMS = -32602;
	private static final int INTERNAL_ERROR = -32603;

	private final Map<String, McpTool> tools = new LinkedHashMap<>();
	private final Gson gson = new Gson();

	public McpJsonRpcHandler(List<McpTool> tools) {
		for (McpTool tool : tools) {
			this.tools.put(tool.name(), tool);
		}
	}

	public JsonObject handle(String line) {
		JsonObject request;

		try {
			JsonElement element = JsonParser.parseString(line);

			if (!element.isJsonObject()) {
				return error(null, INVALID_REQUEST, "Request must be a JSON object");
			}

			request = element.getAsJsonObject();
		} catch (JsonParseException e) {
			return error(null, PARSE_ERROR, "Parse error");
		}

		JsonElement id = request.get("id");
		String method = getString(request, "method");

		if (method == null) {
			return error(id, INVALID_REQUEST, "Missing method");
		}

		if (id == null) {
			handleNotification(method, getObject(request, "params"));
			return null;
		}

		try {
			return result(id, handleMethod(method, getObject(request, "params")));
		} catch (McpException e) {
			return error(id, e.getCode(), e.getMessage());
		} catch (Exception e) {
			return error(id, INTERNAL_ERROR, e.getMessage() == null ? "Internal error" : e.getMessage());
		}
	}

	public String toJson(JsonObject response) {
		return gson.toJson(response);
	}

	public int getToolCount() {
		return tools.size();
	}

	private void handleNotification(String method, JsonObject params) {
	}

	private JsonObject handleMethod(String method, JsonObject params) throws Exception {
		if (method.equals("initialize")) {
			return initialize();
		} else if (method.equals("notifications/initialized") || method.equals("ping")) {
			return new JsonObject();
		} else if (method.equals("tools/list")) {
			return listTools();
		} else if (method.equals("tools/call")) {
			return callTool(params);
		} else {
			throw new McpException(METHOD_NOT_FOUND, "Method not found: " + method);
		}
	}

	private JsonObject initialize() {
		JsonObject result = new JsonObject();
		result.addProperty("protocolVersion", "2024-11-05");

		JsonObject capabilities = new JsonObject();
		capabilities.add("tools", new JsonObject());
		result.add("capabilities", capabilities);

		JsonObject serverInfo = new JsonObject();
		serverInfo.addProperty("name", "enigma");
		serverInfo.addProperty("version", "1.0.0");
		result.add("serverInfo", serverInfo);

		return result;
	}

	private JsonObject listTools() {
		JsonArray toolList = new JsonArray();

		for (McpTool tool : tools.values()) {
			JsonObject item = new JsonObject();
			item.addProperty("name", tool.name());
			item.addProperty("description", tool.description());
			item.add("inputSchema", tool.inputSchema());
			toolList.add(item);
		}

		JsonObject result = new JsonObject();
		result.add("tools", toolList);
		return result;
	}

	private JsonObject callTool(JsonObject params) throws Exception {
		if (params == null) {
			throw new McpException(INVALID_PARAMS, "Missing params");
		}

		String name = getString(params, "name");

		if (name == null) {
			throw new McpException(INVALID_PARAMS, "Missing tool name");
		}

		McpTool tool = tools.get(name);

		if (tool == null) {
			throw new McpException(INVALID_PARAMS, "Unknown tool: " + name);
		}

		JsonObject arguments = getObject(params, "arguments");
		JsonObject toolResult = tool.execute(arguments == null ? new JsonObject() : arguments);

		JsonArray content = new JsonArray();
		JsonObject text = new JsonObject();
		text.addProperty("type", "text");
		text.addProperty("text", gson.toJson(toolResult));
		content.add(text);

		JsonObject result = new JsonObject();
		result.add("content", content);
		return result;
	}

	private JsonObject result(JsonElement id, JsonObject result) {
		JsonObject response = new JsonObject();
		response.addProperty("jsonrpc", "2.0");
		response.add("id", id);
		response.add("result", result);
		return response;
	}

	private JsonObject error(JsonElement id, int code, String message) {
		JsonObject error = new JsonObject();
		error.addProperty("code", code);
		error.addProperty("message", message);

		JsonObject response = new JsonObject();
		response.addProperty("jsonrpc", "2.0");
		response.add("id", id);
		response.add("error", error);
		return response;
	}

	private static String getString(JsonObject object, String name) {
		JsonElement element = object.get(name);
		return element == null || element.isJsonNull() ? null : element.getAsString();
	}

	private static JsonObject getObject(JsonObject object, String name) {
		JsonElement element = object.get(name);
		return element == null || element.isJsonNull() ? null : element.getAsJsonObject();
	}
}
