package cuchaz.enigma.command.mcp;

public class McpException extends Exception {
	private final int code;

	public McpException(int code, String message) {
		super(message);
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
