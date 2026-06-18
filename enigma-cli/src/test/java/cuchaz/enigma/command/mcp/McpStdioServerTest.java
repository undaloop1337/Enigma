package cuchaz.enigma.command.mcp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class McpStdioServerTest {
	@Test
	public void initializeReturnsCapabilities() throws Exception {
		JsonObject response = runServer("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n");

		assertThat(response.get("id").getAsInt(), equalTo(1));
		assertThat(response.getAsJsonObject("result").get("protocolVersion").getAsString(), equalTo("2024-11-05"));
		assertThat(response.getAsJsonObject("result").getAsJsonObject("capabilities").has("tools"), equalTo(true));
	}

	@Test
	public void toolsListReturnsRegisteredTools() throws Exception {
		JsonObject response = runServer("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n");

		assertThat(response.getAsJsonObject("result").getAsJsonArray("tools").size(), equalTo(1));
		assertThat(response.getAsJsonObject("result").getAsJsonArray("tools").get(0).getAsJsonObject().get("name").getAsString(), equalTo("test_tool"));
	}

	@Test
	public void toolsCallReturnsTextContent() throws Exception {
		JsonObject response = runServer("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"test_tool\",\"arguments\":{\"value\":\"abc\"}}}\n");
		String text = response.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();

		assertThat(text, containsString("abc"));
	}

	@Test
	public void unknownToolReturnsInvalidParams() throws Exception {
		JsonObject response = runServer("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"missing\"}}\n");

		assertThat(response.getAsJsonObject("error").get("code").getAsInt(), equalTo(-32602));
	}

	private JsonObject runServer(String input) throws Exception {
		StringWriter output = new StringWriter();
		new McpStdioServer(new StringReader(input), output, List.of(new TestTool())).run();
		return JsonParser.parseString(output.toString().trim()).getAsJsonObject();
	}

	private static class TestTool implements McpTool {
		@Override
		public String name() {
			return "test_tool";
		}

		@Override
		public String description() {
			return "Test tool";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = new JsonObject();
			schema.addProperty("type", "object");
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			JsonObject result = new JsonObject();
			result.addProperty("value", arguments.get("value").getAsString());
			return result;
		}
	}
}
