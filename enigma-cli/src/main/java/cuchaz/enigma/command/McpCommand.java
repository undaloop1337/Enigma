package cuchaz.enigma.command;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.ProgressListener;
import cuchaz.enigma.command.mcp.EnigmaMcpTools;
import cuchaz.enigma.command.mcp.McpStdioServer;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.serde.MappingIoConverter;
import cuchaz.enigma.translation.mapping.tree.EntryTree;

public class McpCommand extends Command {
	public McpCommand() {
		super("mcp");
	}

	@Override
	public String getUsage() {
		return "<jar> [mappings] [libraries...]";
	}

	@Override
	public boolean isValidArgument(int length) {
		return length >= 1;
	}

	@Override
	public void run(String... args) throws Exception {
		Path jar = getReadablePath(args[0]);
		Path mappings = args.length >= 2 ? getReadablePath(args[1]) : null;
		List<Path> libraries = getReadablePaths(args, 2);

		Enigma enigma = Enigma.create();
		EnigmaProject project = enigma.openJar(jar, libraries, ProgressListener.none());

		if (mappings != null) {
			EntryTree<EntryMapping> mappingTree = readMappingsQuietly(mappings, project);
			project.setMappings(mappingTree);
		}

		McpStdioServer server = new McpStdioServer(
				new InputStreamReader(System.in, StandardCharsets.UTF_8),
				new OutputStreamWriter(System.out, StandardCharsets.UTF_8),
				EnigmaMcpTools.create(project)
		);
		server.run();
	}

	private static EntryTree<EntryMapping> readMappingsQuietly(Path path, EnigmaProject project) throws Exception {
		net.fabricmc.mappingio.format.MappingFormat format = MappingReader.detectFormat(path);
		if (format == null) throw new IllegalArgumentException("Unknown mapping format!");

		VisitableMappingTree tree = new MemoryMappingTree();
		MappingReader.read(path, format, tree);
		return MappingIoConverter.fromMappingIo(tree, ProgressListener.none(), project.getJarIndex());
	}
}
