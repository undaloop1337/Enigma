package cuchaz.enigma.command.mcp;

import java.util.List;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.tools.*;

public final class EnigmaMcpTools {
	private EnigmaMcpTools() {
	}

	public static List<McpTool> create(EnigmaProject project) {
		return List.of(
				// Project overview
				new ProjectInfoTool(project),
				new PackageStatsTool(project),
				new MappingStatsTool(project),
				// Search & navigation
				new ListClassesTool(project),
				new SearchClassesTool(project),
				new SearchMembersTool(project),
				new SearchStringConstantsTool(project),
				new SearchAnnotationsTool(project),
				// Class analysis
				new ClassStructureTool(project),
				new ClassBytecodeSummaryTool(project),
				new ClassConstantsTool(project),
				new ClassDependenciesTool(project),
				new InnerClassesTool(project),
				new EnumAnalysisTool(project),
				new LambdaAnalysisTool(project),
				new DiffClassesTool(project),
				new SignatureMatchingTool(project),
				// Method analysis
				new EntryPointsTool(project),
				new DecompileClassTool(project),
				new DecompileMethodTool(project),
				new MethodBodyTool(project),
				new ControlFlowGraphTool(project),
				new MethodSimilarityTool(project),
				new ControlFlowDeobfuscationTool(project),
				// Data flow & references
				new DataFlowTraceTool(project),
				new ReferencesTool(project),
				new MethodCallsTool(project),
				new CallGraphTool(project),
				new FieldAccessTool(project),
				new TypeUsageTool(project),
				new PatternMatchTool(project),
				// Inheritance & structure
				new InheritanceTool(project),
				new ImplementorsTool(project),
				new FindOverridesTool(project),
				new PackageDependenciesTool(project),
				// Security analysis
				new FindSinksTool(project),
				new SerializationAnalysisTool(project),
				new ReflectionUsageTool(project),
				new CryptoAnalysisTool(project),
				new StringDecryptionCandidatesTool(project),
				// Structural detection
				new DesignPatternDetectionTool(project),
				new NetworkProtocolAnalysisTool(project),
				new EventSystemAnalysisTool(project),
				new NativeMethodsTool(project),
				new ResourceReferencesTool(project),
				new KnownLibraryDetectionTool(project),
				new PluginSystemDetectionTool(project),
				new GuiAnalysisTool(project),
				new ThreadAnalysisTool(project),
				// Metrics & intelligence
				new ComplexityMetricsTool(project),
				new NamingSuggestionsTool(project),
				new NamingConventionAnalysisTool(project),
				new AnalysisContextTool(project),
				new ConstantAnalysisTool(project),
				new ExceptionFlowTool(project),
				new FieldValueInferenceTool(project),
				new ClassTimelineTool(project),
				new ApiSurfaceTool(project),
				// Discovery
				new SuggestAnalysisTargetsTool(project),
				new UnmappedEntriesTool(project),
				new UnmappedDependenciesTool(project),
				new DeadCodeTool(project),
				new ClassClusteringTool(project),
				// Mapping operations
				new GetMappingTool(project),
				new SetClassMappingTool(project),
				new SetMethodMappingTool(project),
				new SetFieldMappingTool(project),
				new BatchSetMappingsTool(project),
				new ValidateMappingsTool(project),
				new MappingConflictsTool(project),
				new MappingReviewTool(project),
				new MappingCoverageReportTool(project),
				new MappingImportTool(project),
				// Export & generation
				new ExportMappingsTool(project),
				new ExportMarkdownTool(project),
				new GenerateJavadocTool(project),
				new GenerateInterfaceStubTool(project)
		);
	}
}
