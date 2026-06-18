package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class NetworkProtocolAnalysisTool extends BaseTool {
	public NetworkProtocolAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "network_protocol_analysis";
	}

	@Override
	public String description() {
		return "Find packet/message classes: classes with sequential read/write of primitives to streams, Netty handlers, channel initializers.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		int limit = limit(arguments, 50);
		JsonArray packets = new JsonArray();
		JsonArray handlers = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (packets.size() + handlers.size() >= limit) break;
			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			// Check for Netty handler pattern
			if (extendsNetty(node)) {
				JsonObject item = classJson(project, entry);
				item.addProperty("nettyType", getNettyType(node));
				handlers.add(item);
				continue;
			}

			// Check for packet pattern: has both encode/decode or read/write method pairs with DataInput/DataOutput
			boolean hasReadLike = false, hasWriteLike = false;
			int readWriteOps = 0;

			for (MethodNode method : node.methods) {
				boolean readsFromStream = false, writesToStream = false;
				for (AbstractInsnNode insn : method.instructions) {
					if (insn instanceof MethodInsnNode min) {
						String mOwner = min.owner;
						if (isStreamReader(mOwner, min.name)) { readsFromStream = true; readWriteOps++; }
						if (isStreamWriter(mOwner, min.name)) { writesToStream = true; readWriteOps++; }
					}
				}
				if (readsFromStream) hasReadLike = true;
				if (writesToStream) hasWriteLike = true;
			}

			if (hasReadLike && hasWriteLike && readWriteOps >= 4) {
				JsonObject item = classJson(project, entry);
				item.addProperty("readWriteOps", readWriteOps);
				item.addProperty("kind", "packet");
				packets.add(item);
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("packetCount", packets.size());
		result.addProperty("handlerCount", handlers.size());
		result.add("packets", packets);
		result.add("handlers", handlers);
		return result;
	}

	private static boolean extendsNetty(ClassNode node) {
		if (node.superName == null) return false;
		return node.superName.contains("netty") || node.superName.contains("ChannelHandler")
				|| node.superName.contains("ChannelInboundHandler") || node.superName.contains("SimpleChannelInboundHandler")
				|| node.superName.contains("ChannelInitializer") || node.superName.contains("ByteToMessageDecoder")
				|| node.superName.contains("MessageToByteEncoder");
	}

	private static String getNettyType(ClassNode node) {
		if (node.superName.contains("Decoder") || node.superName.contains("ByteToMessage")) return "decoder";
		if (node.superName.contains("Encoder") || node.superName.contains("MessageToByte")) return "encoder";
		if (node.superName.contains("Initializer")) return "initializer";
		return "handler";
	}

	private static boolean isStreamReader(String owner, String name) {
		return (owner.contains("DataInput") || owner.contains("ByteBuf") || owner.contains("InputStream"))
				&& (name.startsWith("read") || name.equals("get"));
	}

	private static boolean isStreamWriter(String owner, String name) {
		return (owner.contains("DataOutput") || owner.contains("ByteBuf") || owner.contains("OutputStream"))
				&& (name.startsWith("write") || name.equals("put"));
	}
}
