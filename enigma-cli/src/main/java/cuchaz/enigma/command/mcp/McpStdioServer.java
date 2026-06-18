package cuchaz.enigma.command.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import com.google.gson.JsonObject;

public class McpStdioServer {
	private final BufferedReader reader;
	private final Writer writer;
	private final McpJsonRpcHandler handler;

	public McpStdioServer(Reader reader, Writer writer, List<McpTool> tools) {
		this.reader = new BufferedReader(reader);
		this.writer = writer;
		this.handler = new McpJsonRpcHandler(tools);
	}

	public void run() throws IOException {
		String line;

		while ((line = reader.readLine()) != null) {
			if (line.isBlank()) {
				continue;
			}

			handleLine(line);
		}
	}

	void handleLine(String line) throws IOException {
		JsonObject response = handler.handle(line);

		if (response != null) {
			writer.write(handler.toJson(response));
			writer.write('\n');
			writer.flush();
		}
	}
}
