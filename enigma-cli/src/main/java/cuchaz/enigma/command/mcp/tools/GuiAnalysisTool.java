package cuchaz.enigma.command.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class GuiAnalysisTool extends BaseTool {
	public GuiAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "gui_analysis";
	}

	@Override
	public String description() {
		return "Detect GUI components: Swing/JavaFX/AWT class extensions, layout code, and event handlers.";
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
		JsonArray results = new JsonArray();

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;
			ClassNode node = project.getClassProvider().get(entry.getFullName());
			if (node == null) continue;

			String guiKind = getGuiKind(node);
			if (guiKind != null) {
				JsonObject item = classJson(project, entry);
				item.addProperty("guiKind", guiKind);
				item.addProperty("superClass", node.superName);
				results.add(item);
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", results.size());
		result.add("components", results);
		return result;
	}

	private static String getGuiKind(ClassNode node) {
		if (node.superName == null) return null;
		String sup = node.superName;
		// Swing
		if (sup.startsWith("javax/swing/J") || sup.equals("javax/swing/JFrame") || sup.equals("javax/swing/JPanel")
				|| sup.equals("javax/swing/JDialog") || sup.equals("javax/swing/JComponent")
				|| sup.equals("javax/swing/AbstractAction")) return "Swing:" + simpleName(sup);
		// AWT
		if (sup.startsWith("java/awt/") && (sup.contains("Frame") || sup.contains("Panel")
				|| sup.contains("Canvas") || sup.contains("Dialog") || sup.contains("Window")))
			return "AWT:" + simpleName(sup);
		// JavaFX
		if (sup.startsWith("javafx/") && (sup.contains("Application") || sup.contains("Stage")
				|| sup.contains("Scene") || sup.contains("Pane") || sup.contains("Control")))
			return "JavaFX:" + simpleName(sup);
		// Listener interfaces
		if (node.interfaces != null) {
			for (String iface : node.interfaces) {
				if (iface.contains("Listener") || iface.contains("ActionListener") || iface.contains("MouseListener")
						|| iface.contains("KeyListener") || iface.contains("EventHandler"))
					return "Listener:" + simpleName(iface);
			}
		}
		return null;
	}

	private static String simpleName(String name) {
		int slash = name.lastIndexOf('/');
		return slash >= 0 ? name.substring(slash + 1) : name;
	}
}
