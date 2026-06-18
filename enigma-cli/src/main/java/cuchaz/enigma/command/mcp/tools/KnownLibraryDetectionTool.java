package cuchaz.enigma.command.mcp.tools;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class KnownLibraryDetectionTool extends BaseTool {
	// Fingerprints: package prefix -> library name
	private static final Map<String, String> LIBRARY_FINGERPRINTS = new HashMap<>();
	static {
		LIBRARY_FINGERPRINTS.put("com/google/gson", "GSON");
		LIBRARY_FINGERPRINTS.put("com/google/common", "Guava");
		LIBRARY_FINGERPRINTS.put("org/apache/commons/lang", "Apache Commons Lang");
		LIBRARY_FINGERPRINTS.put("org/apache/commons/io", "Apache Commons IO");
		LIBRARY_FINGERPRINTS.put("org/apache/commons/codec", "Apache Commons Codec");
		LIBRARY_FINGERPRINTS.put("org/apache/logging/log4j", "Log4j");
		LIBRARY_FINGERPRINTS.put("org/slf4j", "SLF4J");
		LIBRARY_FINGERPRINTS.put("io/netty", "Netty");
		LIBRARY_FINGERPRINTS.put("org/objectweb/asm", "ASM");
		LIBRARY_FINGERPRINTS.put("com/mojang", "Mojang Libraries");
		LIBRARY_FINGERPRINTS.put("it/unimi/dsi/fastutil", "fastutil");
		LIBRARY_FINGERPRINTS.put("org/lwjgl", "LWJGL");
		LIBRARY_FINGERPRINTS.put("com/fasterxml/jackson", "Jackson");
		LIBRARY_FINGERPRINTS.put("org/yaml/snakeyaml", "SnakeYAML");
		LIBRARY_FINGERPRINTS.put("javax/inject", "javax.inject");
		LIBRARY_FINGERPRINTS.put("com/google/inject", "Guice");
		LIBRARY_FINGERPRINTS.put("org/jetbrains/annotations", "JetBrains Annotations");
		LIBRARY_FINGERPRINTS.put("kotlin/", "Kotlin Stdlib");
		LIBRARY_FINGERPRINTS.put("scala/", "Scala Stdlib");
		LIBRARY_FINGERPRINTS.put("org/spongepowered/asm", "SpongePowered Mixin");
	}

	public KnownLibraryDetectionTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "known_library_detection";
	}

	@Override
	public String description() {
		return "Identify embedded/shaded libraries by matching class package patterns against known library fingerprints.";
	}

	@Override
	public JsonObject inputSchema() {
		return super.inputSchema();
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		Map<String, Integer> detected = new HashMap<>();
		int totalClasses = 0;
		int libraryClasses = 0;

		for (ClassEntry entry : project.getJarIndex().getEntryIndex().getClasses()) {
			totalClasses++;
			String name = entry.getFullName();
			for (Map.Entry<String, String> fp : LIBRARY_FINGERPRINTS.entrySet()) {
				if (name.startsWith(fp.getKey())) {
					detected.merge(fp.getValue(), 1, Integer::sum);
					libraryClasses++;
					break;
				}
			}
		}

		JsonArray libraries = new JsonArray();
		detected.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.forEach(e -> {
					JsonObject lib = new JsonObject();
					lib.addProperty("library", e.getKey());
					lib.addProperty("classes", e.getValue());
					libraries.add(lib);
				});

		JsonObject result = new JsonObject();
		result.addProperty("totalClasses", totalClasses);
		result.addProperty("libraryClasses", libraryClasses);
		result.addProperty("applicationClasses", totalClasses - libraryClasses);
		result.add("detectedLibraries", libraries);
		return result;
	}
}
