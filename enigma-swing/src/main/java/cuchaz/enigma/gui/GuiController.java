/*******************************************************************************
* Copyright (c) 2015 Jeff Martin.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Lesser General Public
* License v3.0 which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/lgpl.html
*
* <p>Contributors:
* Jeff Martin - initial API and implementation
******************************************************************************/

package cuchaz.enigma.gui;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.analysis.ClassImplementationsTreeNode;
import cuchaz.enigma.analysis.ClassInheritanceTreeNode;
import cuchaz.enigma.analysis.ClassReferenceTreeNode;
import cuchaz.enigma.analysis.EntryReference;
import cuchaz.enigma.analysis.FieldReferenceTreeNode;
import cuchaz.enigma.analysis.IndexTreeBuilder;
import cuchaz.enigma.analysis.MethodImplementationsTreeNode;
import cuchaz.enigma.analysis.MethodInheritanceTreeNode;
import cuchaz.enigma.analysis.MethodReferenceTreeNode;
import cuchaz.enigma.analysis.StructureTreeNode;
import cuchaz.enigma.analysis.StructureTreeOptions;
import cuchaz.enigma.api.DataInvalidationEvent;
import cuchaz.enigma.api.DataInvalidationListener;
import cuchaz.enigma.api.service.ObfuscationTestService;
import cuchaz.enigma.api.service.ProjectService;
import cuchaz.enigma.api.view.GuiView;
import cuchaz.enigma.api.view.entry.EntryReferenceView;
import cuchaz.enigma.api.view.entry.EntryView;
import cuchaz.enigma.classhandle.ClassHandle;
import cuchaz.enigma.classhandle.ClassHandleProvider;
import cuchaz.enigma.command.mcp.DynamicEnigmaMcpTools;
import cuchaz.enigma.command.mcp.McpHttpServer;
import cuchaz.enigma.command.mcp.McpTool;
import cuchaz.enigma.gui.config.LookAndFeel;
import cuchaz.enigma.gui.config.NetConfig;
import cuchaz.enigma.gui.config.UiConfig;
import cuchaz.enigma.gui.dialog.ProgressDialog;
import cuchaz.enigma.gui.newabstraction.EntryValidation;
import cuchaz.enigma.gui.panels.EditorPanel;
import cuchaz.enigma.gui.stats.StatsGenerator;
import cuchaz.enigma.gui.stats.StatsMember;
import cuchaz.enigma.gui.util.History;
import cuchaz.enigma.network.ClientPacketHandler;
import cuchaz.enigma.network.EnigmaClient;
import cuchaz.enigma.network.EnigmaServer;
import cuchaz.enigma.network.IntegratedEnigmaServer;
import cuchaz.enigma.network.Message;
import cuchaz.enigma.network.ServerPacketHandler;
import cuchaz.enigma.network.packet.EntryChangeC2SPacket;
import cuchaz.enigma.network.packet.LoginC2SPacket;
import cuchaz.enigma.network.packet.Packet;
import cuchaz.enigma.source.DecompiledClassSource;
import cuchaz.enigma.source.DecompilerService;
import cuchaz.enigma.source.SourceIndex;
import cuchaz.enigma.source.Token;
import cuchaz.enigma.translation.TranslateResult;
import cuchaz.enigma.translation.Translator;
import cuchaz.enigma.translation.mapping.EntryChange;
import cuchaz.enigma.translation.mapping.EntryMapping;
import cuchaz.enigma.translation.mapping.EntryRemapper;
import cuchaz.enigma.translation.mapping.EntryUtil;
import cuchaz.enigma.translation.mapping.MappingDelta;
import cuchaz.enigma.translation.mapping.ResolutionStrategy;
import cuchaz.enigma.translation.mapping.serde.MappingFormat;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.translation.mapping.serde.MappingSaveParameters;
import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.HashEntryTree;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.I18n;
import cuchaz.enigma.utils.Utils;
import cuchaz.enigma.utils.validation.PrintValidatable;
import cuchaz.enigma.utils.validation.ValidationContext;

public class GuiController implements ClientPacketHandler, GuiView, DataInvalidationListener {
	private final Gui gui;
	public final Enigma enigma;

	public EnigmaProject project;
	private IndexTreeBuilder indexTreeBuilder;

	private Path loadedMappingPath;
	private MappingFormat loadedMappingFormat = MappingFormat.ENIGMA_DIRECTORY;

	private ClassHandleProvider chp;

	private ClassHandle tokenHandle;

	private EnigmaClient client;
	private EnigmaServer server;
	private McpHttpServer mcpServer;

	private History<EntryReference<Entry<?>, Entry<?>>> referenceHistory;

	public GuiController(Gui gui, Enigma enigma) {
		this.gui = gui;
		this.enigma = enigma;
		startMcpServer();
	}

	@Override
	public EnigmaProject getProject() {
		return project;
	}

	@Override
	public JFrame getFrame() {
		return gui.getFrame();
	}

	@Override
	public float getScale() {
		return UiConfig.getActiveScaleFactor();
	}

	@Override
	public boolean isDarkTheme() {
		return LookAndFeel.isDarkLaf();
	}

	@Override
	public JEditorPane createEditorPane() {
		JEditorPane editor = new JEditorPane();
		EditorPanel.customizeEditor(editor);
		return editor;
	}

	public String getMcpHttpUrl() {
		return mcpServer == null ? null : mcpServer.getStreamableHttpUrl();
	}

	public String getMcpSseUrl() {
		return mcpServer == null ? null : mcpServer.getSseUrl();
	}

	public void startMcpServer() {
		if (mcpServer != null) {
			return;
		}

		List<McpTool> tools = DynamicEnigmaMcpTools.create(() -> project, List.of(
				new ProjectStatusTool(),
				new CloseJarTool(),
				new CloseMappingsTool(),
				new ReloadMappingsTool(),
				new ReloadAllTool(),
				new DropMappingsTool(),
				new SaveMappingsTool(),
				new ExportJarTool(),
				new ExportSourceTool()
		));

		int port = 37627;
		int maxAttempts = 10;

		for (int attempt = 0; attempt < maxAttempts; attempt++) {
			mcpServer = new McpHttpServer("127.0.0.1", port + attempt, tools);

			try {
				mcpServer.start();
				System.out.println("MCP server started on port " + (port + attempt));
				return;
			} catch (IOException e) {
				mcpServer = null;

				if (attempt == maxAttempts - 1) {
					System.err.println("Failed to start MCP server after " + maxAttempts + " attempts: " + e.getMessage());
				}
			}
		}
	}

	public void stopMcpServer() {
		if (mcpServer != null) {
			mcpServer.stop();
			mcpServer = null;
		}
	}

	private abstract class GuiActionTool implements McpTool {
		@Override
		public JsonObject inputSchema() {
			JsonObject schema = new JsonObject();
			schema.addProperty("type", "object");
			schema.add("properties", new JsonObject());
			return schema;
		}

		JsonObject success() {
			JsonObject result = new JsonObject();
			result.addProperty("success", true);
			return result;
		}

		Path getPath(JsonObject arguments) {
			return Path.of(arguments.get("path").getAsString());
		}

		void requireProject() {
			if (project == null) {
				throw new IllegalStateException("No project is open in Enigma");
			}
		}
	}

	private class CloseJarTool extends GuiActionTool {
		@Override
		public String name() {
			return "close_jar";
		}

		@Override
		public String description() {
			return "Close the current GUI project jar.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			requireProject();
			SwingUtilities.invokeLater(GuiController.this::closeJar);
			return success();
		}
	}

	private class CloseMappingsTool extends GuiActionTool {
		@Override
		public String name() {
			return "close_mappings";
		}

		@Override
		public String description() {
			return "Close mappings in the current GUI project.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			requireProject();
			SwingUtilities.invokeLater(GuiController.this::closeMappings);
			return success();
		}
	}

	private class ReloadMappingsTool extends GuiActionTool {
		@Override
		public String name() {
			return "reload_mappings";
		}

		@Override
		public String description() {
			return "Reload mappings from the currently loaded mapping path.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			requireProject();
			reloadMappings();
			return success();
		}
	}

	private class ReloadAllTool extends GuiActionTool {
		@Override
		public String name() {
			return "reload_all";
		}

		@Override
		public String description() {
			return "Reload the current jar and mappings.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			requireProject();
			reloadAll();
			return success();
		}
	}

	private class DropMappingsTool extends GuiActionTool {
		@Override
		public String name() {
			return "drop_invalid_mappings";
		}

		@Override
		public String description() {
			return "Drop mappings that do not match the currently open jar.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			requireProject();
			dropMappings();
			return success();
		}
	}

	private abstract class PathTool extends GuiActionTool {
		@Override
		public JsonObject inputSchema() {
			JsonObject schema = super.inputSchema();
			JsonObject path = new JsonObject();
			path.addProperty("type", "string");
			path.addProperty("description", "Output file or directory path.");
			schema.getAsJsonObject("properties").add("path", path);
			return schema;
		}
	}

	private class SaveMappingsTool extends PathTool {
		@Override
		public String name() {
			return "save_mappings_as";
		}

		@Override
		public String description() {
			return "Save mappings to a path using the currently selected mapping format.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			requireProject();
			saveMappings(getPath(arguments));
			return success();
		}
	}

	private class ExportJarTool extends PathTool {
		@Override
		public String name() {
			return "export_remapped_jar";
		}

		@Override
		public String description() {
			return "Export the current project as a remapped jar.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			requireProject();
			exportJar(getPath(arguments));
			return success();
		}
	}

	private class ExportSourceTool extends PathTool {
		@Override
		public String name() {
			return "export_source";
		}

		@Override
		public String description() {
			return "Export decompiled source for the current project to a directory.";
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			requireProject();
			exportSource(getPath(arguments));
			return success();
		}
	}

	private class ProjectStatusTool implements McpTool {
		@Override
		public String name() {
			return "project_status";
		}

		@Override
		public String description() {
			return "Return Enigma GUI and MCP server project status.";
		}

		@Override
		public JsonObject inputSchema() {
			JsonObject schema = new JsonObject();
			schema.addProperty("type", "object");
			schema.add("properties", new JsonObject());
			return schema;
		}

		@Override
		public JsonObject execute(JsonObject arguments) {
			JsonObject result = new JsonObject();
			result.addProperty("projectOpen", project != null);
			result.addProperty("dirty", isDirty());
			result.addProperty("mappingsPath", loadedMappingPath == null ? null : loadedMappingPath.toString());
			result.addProperty("httpUrl", getMcpHttpUrl());
			result.addProperty("sseUrl", getMcpSseUrl());

			if (project != null) {
				result.addProperty("classes", project.getJarIndex().getEntryIndex().getClasses().size());
				result.addProperty("methods", project.getJarIndex().getEntryIndex().getMethods().size());
				result.addProperty("fields", project.getJarIndex().getEntryIndex().getFields().size());
			}

			return result;
		}
	}

	public boolean isDirty() {
		return project != null && project.getMapper().isDirty();
	}

	public CompletableFuture<Void> openJar(final List<Path> jarPaths, final List<Path> libraries) {
		this.gui.onStartOpenJar();

		return ProgressDialog.runOffThread(gui.getFrame(), progress -> {
			project = enigma.openJars(jarPaths, libraries, progress, false);
			project.addDataInvalidationListener(this);
			indexTreeBuilder = new IndexTreeBuilder(project.getJarIndex());
			chp = new ClassHandleProvider(project, UiConfig.getDecompiler().service);
			SwingUtilities.invokeLater(() -> {
				for (ProjectService projectService : enigma.getServices().get(ProjectService.TYPE)) {
					projectService.onProjectOpen(project);
				}

				gui.onFinishOpenJar(getFileNames(jarPaths));
				refreshClasses();
			});
		});
	}

	private static String getFileNames(List<Path> jarPaths) {
		return jarPaths.stream()
				.map(Path::getFileName)
				.map(Object::toString)
				.collect(Collectors.joining(", "));
	}

	public void closeJar() {
		for (ProjectService projectService : enigma.getServices().get(ProjectService.TYPE)) {
			projectService.onProjectClose(project);
		}

		this.chp.destroy();
		this.chp = null;
		this.project = null;
		this.gui.onCloseJar();
	}

	public CompletableFuture<Void> openMappings(MappingFormat format, Path path) {
		if (project == null) {
			return CompletableFuture.completedFuture(null);
		}

		gui.setMappingsFile(path);

		return ProgressDialog.runOffThread(gui.getFrame(), progress -> {
			try {
				MappingSaveParameters saveParameters = enigma.getProfile().getMappingSaveParameters();
				project.setMappings(format.read(path, progress, saveParameters, project.getJarIndex()));

				loadedMappingFormat = format;
				loadedMappingPath = path;

				refreshClasses();
				project.invalidateData(DataInvalidationEvent.InvalidationType.JAVADOC);
			} catch (MappingParseException e) {
				JOptionPane.showMessageDialog(gui.getFrame(), e.getMessage());
			}
		});
	}

	@Override
	public void openMappings(EntryTree<EntryMapping> mappings) {
		if (project == null) {
			return;
		}

		project.setMappings(mappings);
		refreshClasses();
		project.invalidateData(DataInvalidationEvent.InvalidationType.JAVADOC);
	}

	public MappingFormat getLoadedMappingFormat() {
		return loadedMappingFormat;
	}

	public CompletableFuture<Void> saveMappings(Path path) {
		return saveMappings(path, loadedMappingFormat);
	}

	/**
	 * Saves the mappings, with a dialog popping up, showing the progress.
	 *
	 * <p>Notice the returned completable future has to be completed by
	 * {@link SwingUtilities#invokeLater(Runnable)}. Hence, do not try to
	 * join on the future in gui, but rather call {@code thenXxx} methods.
	 *
	 * @param path the path of the save
	 * @param format the format of the save
	 * @return the future of saving
	 */
	public CompletableFuture<Void> saveMappings(Path path, MappingFormat format) {
		if (project == null) {
			return CompletableFuture.completedFuture(null);
		}

		return ProgressDialog.runOffThread(this.gui.getFrame(), progress -> {
			EntryRemapper mapper = project.getMapper();
			MappingSaveParameters saveParameters = enigma.getProfile().getMappingSaveParameters();

			MappingDelta<EntryMapping> delta = mapper.takeMappingDelta();
			boolean saveAll = !path.equals(loadedMappingPath);

			loadedMappingFormat = format;
			loadedMappingPath = path;

			if (saveAll) {
				format.write(mapper.getObfToDeobf(), path, progress, saveParameters);
			} else {
				format.write(mapper.getObfToDeobf(), delta, path, progress, saveParameters);
			}
		});
	}

	public void closeMappings() {
		if (project == null) {
			return;
		}

		project.setMappings(null);

		this.gui.setMappingsFile(null);
		refreshClasses();
		project.invalidateData(DataInvalidationEvent.InvalidationType.JAVADOC);
	}

	public void reloadAll() {
		List<Path> jarPaths = this.project.getJarPaths();
		List<Path> libraryPaths = this.project.getLibraryPaths();
		MappingFormat loadedMappingFormat = this.loadedMappingFormat;
		Path loadedMappingPath = this.loadedMappingPath;

		this.closeJar();
		CompletableFuture<Void> f = this.openJar(jarPaths, libraryPaths);

		if (loadedMappingFormat != null && loadedMappingPath != null) {
			f.whenComplete((v, t) -> this.openMappings(loadedMappingFormat, loadedMappingPath));
		}
	}

	public void reloadMappings() {
		MappingFormat loadedMappingFormat = this.loadedMappingFormat;
		Path loadedMappingPath = this.loadedMappingPath;

		if (loadedMappingFormat != null && loadedMappingPath != null) {
			this.closeMappings();
			this.openMappings(loadedMappingFormat, loadedMappingPath);
		}
	}

	public CompletableFuture<Void> dropMappings() {
		if (project == null) {
			return CompletableFuture.completedFuture(null);
		}

		return ProgressDialog.runOffThread(this.gui.getFrame(), progress -> project.dropMappings(progress));
	}

	public CompletableFuture<Void> exportSource(final Path path) {
		if (project == null) {
			return CompletableFuture.completedFuture(null);
		}

		return ProgressDialog.runOffThread(this.gui.getFrame(), progress -> {
			EnigmaProject.JarExport jar = project.exportRemappedJar(progress);
			jar.decompileStream(project, progress, chp.getDecompilerService(), EnigmaProject.DecompileErrorStrategy.TRACE_AS_SOURCE).forEach(source -> {
				try {
					source.writeTo(source.resolvePath(path));
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		});
	}

	public CompletableFuture<Void> exportJar(final Path path) {
		if (project == null) {
			return CompletableFuture.completedFuture(null);
		}

		return ProgressDialog.runOffThread(this.gui.getFrame(), progress -> {
			EnigmaProject.JarExport jar = project.exportRemappedJar(progress);
			jar.write(path, progress);
		});
	}

	public void setTokenHandle(ClassHandle handle) {
		if (tokenHandle != null) {
			tokenHandle.close();
		}

		tokenHandle = handle;
	}

	public ClassHandle getTokenHandle() {
		return tokenHandle;
	}

	public ReadableToken getReadableToken(Token token) {
		if (tokenHandle == null) {
			return null;
		}

		try {
			return tokenHandle.getSource().get().map(DecompiledClassSource::getIndex).map(index -> new ReadableToken(index.getLineNumber(token.start), index.getColumnNumber(token.start), index.getColumnNumber(token.end))).unwrapOr(null);
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	@Nullable
	public EntryReferenceView getCursorReference() {
		return gui.getCursorReference();
	}

	@Override
	@Nullable
	public EntryView getCursorDeclaration() {
		return gui.getCursorDeclaration();
	}

	/**
	 * Navigates to the declaration with respect to navigation history.
	 *
	 * @param entry the entry whose declaration will be navigated to
	 */
	public void openDeclaration(Entry<?> entry) {
		if (entry == null) {
			throw new IllegalArgumentException("Entry cannot be null!");
		}

		openReference(EntryReference.declaration(entry, entry.getName()));
	}

	/**
	 * Navigates to the reference with respect to navigation history.
	 *
	 * @param reference the reference
	 */
	public void openReference(EntryReference<Entry<?>, Entry<?>> reference) {
		if (reference == null) {
			throw new IllegalArgumentException("Reference cannot be null!");
		}

		if (this.referenceHistory == null) {
			this.referenceHistory = new History<>(reference);
		} else {
			if (!reference.equals(this.referenceHistory.getCurrent())) {
				this.referenceHistory.push(reference);
			}
		}

		this.gui.showReference(reference);
	}

	public List<Token> getTokensForReference(DecompiledClassSource source, EntryReference<Entry<?>, Entry<?>> reference) {
		EntryRemapper mapper = this.project.getMapper();

		SourceIndex index = source.getIndex();
		return mapper.getObfResolver().resolveReference(reference, ResolutionStrategy.RESOLVE_CLOSEST).stream().flatMap(r -> index.getReferenceTokens(r).stream()).sorted().toList();
	}

	public void openPreviousReference() {
		if (hasPreviousReference()) {
			this.gui.showReference(referenceHistory.goBack());
		}
	}

	public boolean hasPreviousReference() {
		return referenceHistory != null && referenceHistory.canGoBack();
	}

	public void openNextReference() {
		if (hasNextReference()) {
			this.gui.showReference(referenceHistory.goForward());
		}
	}

	public boolean hasNextReference() {
		return referenceHistory != null && referenceHistory.canGoForward();
	}

	public void navigateTo(Entry<?> entry) {
		if (!project.isRenamable(entry)) {
			// entry is not in the jar. Ignore it
			return;
		}

		openDeclaration(entry);
	}

	public void navigateTo(EntryReference<Entry<?>, Entry<?>> reference) {
		if (!project.isRenamable(reference.getLocationClassEntry())) {
			return;
		}

		openReference(reference);
	}

	public void refreshClasses() {
		if (project == null) {
			return;
		}

		List<ClassEntry> obfClasses = new ArrayList<>();
		List<ClassEntry> deobfClasses = new ArrayList<>();
		this.addSeparatedClasses(obfClasses, deobfClasses);
		this.gui.setObfClasses(obfClasses);
		this.gui.setDeobfClasses(deobfClasses);
	}

	public void addSeparatedClasses(List<ClassEntry> obfClasses, List<ClassEntry> deobfClasses) {
		EntryRemapper mapper = project.getMapper();

		Collection<ClassEntry> classes = project.getJarIndex().getEntryIndex().getClasses();
		Stream<ClassEntry> visibleClasses = classes.stream().filter(entry -> !entry.isInnerClass());

		visibleClasses.forEach(entry -> {
			if (gui.isSingleClassTree()) {
				deobfClasses.add(entry);
				return;
			}

			TranslateResult<ClassEntry> result = mapper.extendedDeobfuscate(entry);
			ClassEntry deobfEntry = result.getValue();

			List<ObfuscationTestService> obfService = enigma.getServices().get(ObfuscationTestService.TYPE);
			boolean obfuscated = result.isObfuscated() && deobfEntry.equals(entry);

			if (obfuscated && !obfService.isEmpty()) {
				if (obfService.stream().anyMatch(service -> service.testDeobfuscated(entry))) {
					obfuscated = false;
				}
			}

			if (obfuscated) {
				obfClasses.add(entry);
			} else {
				deobfClasses.add(entry);
			}
		});
	}

	public StructureTreeNode getClassStructure(ClassEntry entry, StructureTreeOptions options) {
		StructureTreeNode rootNode = new StructureTreeNode(this.project, entry, entry);
		rootNode.load(this.project, options);
		return rootNode;
	}

	public ClassInheritanceTreeNode getClassInheritance(ClassEntry entry) {
		Translator translator = project.getMapper().getDeobfuscator();
		ClassInheritanceTreeNode rootNode = indexTreeBuilder.buildClassInheritance(translator, entry);
		return ClassInheritanceTreeNode.findNode(rootNode, entry);
	}

	public ClassImplementationsTreeNode getClassImplementations(ClassEntry entry) {
		Translator translator = project.getMapper().getDeobfuscator();
		return this.indexTreeBuilder.buildClassImplementations(translator, entry);
	}

	public MethodInheritanceTreeNode getMethodInheritance(MethodEntry entry) {
		Translator translator = project.getMapper().getDeobfuscator();
		MethodInheritanceTreeNode rootNode = indexTreeBuilder.buildMethodInheritance(translator, entry);
		return MethodInheritanceTreeNode.findNode(rootNode, entry);
	}

	public MethodImplementationsTreeNode getMethodImplementations(MethodEntry entry) {
		Translator translator = project.getMapper().getDeobfuscator();
		List<MethodImplementationsTreeNode> rootNodes = indexTreeBuilder.buildMethodImplementations(translator, entry);

		if (rootNodes.isEmpty()) {
			return null;
		}

		if (rootNodes.size() > 1) {
			System.err.println("WARNING: Method " + entry + " implements multiple interfaces. Only showing first one.");
		}

		return MethodImplementationsTreeNode.findNode(rootNodes.get(0), entry);
	}

	public ClassReferenceTreeNode getClassReferences(ClassEntry entry) {
		Translator deobfuscator = project.getMapper().getDeobfuscator();
		ClassReferenceTreeNode rootNode = new ClassReferenceTreeNode(deobfuscator, entry);
		rootNode.load(project.getJarIndex(), true);
		return rootNode;
	}

	public FieldReferenceTreeNode getFieldReferences(FieldEntry entry) {
		Translator translator = project.getMapper().getDeobfuscator();
		FieldReferenceTreeNode rootNode = new FieldReferenceTreeNode(translator, entry);
		rootNode.load(project.getJarIndex(), true);
		return rootNode;
	}

	public MethodReferenceTreeNode getMethodReferences(MethodEntry entry, boolean recursive) {
		Translator translator = project.getMapper().getDeobfuscator();
		MethodReferenceTreeNode rootNode = new MethodReferenceTreeNode(translator, entry);
		rootNode.load(project.getJarIndex(), true, recursive);
		return rootNode;
	}

	@Override
	public boolean applyChangeFromServer(EntryChange<?> change) {
		ValidationContext vc = new ValidationContext();
		vc.setActiveElement(PrintValidatable.INSTANCE);
		this.applyChange0(vc, change);
		gui.showStructure(gui.getActiveEditor());

		return vc.canProceed();
	}

	public void validateChange(ValidationContext vc, EntryChange<?> change) {
		if (change.getDeobfName().isSet()) {
			EntryValidation.validateRename(vc, this.project, change.getTarget(), change.getDeobfName().getNewValue());
		}

		if (change.getJavadoc().isSet()) {
			EntryValidation.validateJavadoc(vc, change.getJavadoc().getNewValue());
		}
	}

	public void applyChange(ValidationContext vc, EntryChange<?> change) {
		this.applyChange0(vc, change);
		gui.showStructure(gui.getActiveEditor());

		if (!vc.canProceed()) {
			return;
		}

		this.sendPacket(new EntryChangeC2SPacket(change));
	}

	private void applyChange0(ValidationContext vc, EntryChange<?> change) {
		validateChange(vc, change);

		if (!vc.canProceed()) {
			return;
		}

		Entry<?> target = change.getTarget();
		EntryMapping prev = this.project.getMapper().getDeobfMapping(target);
		EntryMapping mapping = EntryUtil.applyChange(vc, this.project, this.project.getMapper(), change);

		boolean renamed = !change.getDeobfName().isUnchanged();

		if (renamed && target instanceof ClassEntry && !((ClassEntry) target).isInnerClass()) {
			this.gui.moveClassTree(target, prev.targetName() == null, mapping.targetName() == null);
		}

		if (!Objects.equals(prev.javadoc(), mapping.javadoc())) {
			project.invalidateData(target.getTopLevelClass().getFullName(), DataInvalidationEvent.InvalidationType.JAVADOC);
			// invalidateJavadoc implies invalidateMapped, so no need to check for that too
		} else if (!Objects.equals(prev.targetName(), mapping.targetName())) {
			project.invalidateData(DataInvalidationEvent.InvalidationType.MAPPINGS);
		}

		gui.showStructure(gui.getActiveEditor());
	}

	public void openStats(Set<StatsMember> includedMembers, String topLevelPackage, boolean includeSynthetic) {
		ProgressDialog.runOffThread(gui.getFrame(), progress -> {
			String data = new StatsGenerator(project).generate(progress, includedMembers, topLevelPackage, includeSynthetic).getTreeJson();

			try {
				File statsFile = File.createTempFile("stats", ".html");

				try (FileWriter w = new FileWriter(statsFile, StandardCharsets.UTF_8)) {
					w.write(Utils.readResourceToString("/stats.html").replace("/*data*/", data));
				}

				Desktop.getDesktop().open(statsFile);
			} catch (IOException e) {
				throw new Error(e);
			}
		});
	}

	public void setDecompiler(DecompilerService service) {
		if (chp != null) {
			chp.setDecompilerService(service);
		}
	}

	public ClassHandleProvider getClassHandleProvider() {
		return chp;
	}

	public EnigmaClient getClient() {
		return client;
	}

	public EnigmaServer getServer() {
		return server;
	}

	public void createClient(String username, String ip, int port, char[] password) throws IOException {
		client = new EnigmaClient(this, ip, port);
		client.connect();
		client.sendPacket(new LoginC2SPacket(project.getJarChecksum(), password, username));
		gui.setConnectionState(ConnectionState.CONNECTED);
	}

	public void createServer(int port, char[] password) throws IOException {
		server = new IntegratedEnigmaServer(project.getJarChecksum(), password, EntryRemapper.mapped(project.getJarIndex(), new HashEntryTree<>(project.getMapper().getObfToDeobf())), port);
		server.start();
		client = new EnigmaClient(this, "127.0.0.1", port);
		client.connect();
		client.sendPacket(new LoginC2SPacket(project.getJarChecksum(), password, NetConfig.getUsername()));
		gui.setConnectionState(ConnectionState.HOSTING);
	}

	@Override
	public synchronized void disconnectIfConnected(String reason) {
		if (client == null && server == null) {
			return;
		}

		if (client != null) {
			client.disconnect();
		}

		if (server != null) {
			server.stop();
		}

		client = null;
		server = null;
		SwingUtilities.invokeLater(() -> {
			if (reason != null) {
				JOptionPane.showMessageDialog(gui.getFrame(), I18n.translate(reason), I18n.translate("disconnect.disconnected"), JOptionPane.INFORMATION_MESSAGE);
			}

			gui.setConnectionState(ConnectionState.NOT_CONNECTED);
		});
	}

	@Override
	public void sendPacket(Packet<ServerPacketHandler> packet) {
		if (client != null) {
			client.sendPacket(packet);
		}
	}

	@Override
	public void addMessage(Message message) {
		gui.addMessage(message);
	}

	@Override
	public void updateUserList(List<String> users) {
		gui.setUserList(users);
	}

	@Override
	public void onDataInvalidated(DataInvalidationEvent event) {
		Objects.requireNonNull(project, "Invalidating data when no project is open");

		if (event.getClasses() == null) {
			switch (event.getType()) {
			case MAPPINGS -> chp.invalidateMapped();
			case JAVADOC -> chp.invalidateJavadoc();
			case DECOMPILE -> chp.invalidate();
			}
		} else {
			switch (event.getType()) {
			case MAPPINGS -> {
				for (String clazz : event.getClasses()) {
					chp.invalidateMapped(new ClassEntry(clazz));
				}
			}
			case JAVADOC -> {
				for (String clazz : event.getClasses()) {
					chp.invalidateJavadoc(new ClassEntry(clazz));
				}
			}
			case DECOMPILE -> {
				for (String clazz : event.getClasses()) {
					chp.invalidate(new ClassEntry(clazz));
				}
			}
			}
		}
	}
}
