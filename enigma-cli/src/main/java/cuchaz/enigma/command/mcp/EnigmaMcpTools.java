package cuchaz.enigma.command.mcp;

import java.util.List;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.tools.BatchSetMappingsTool;
import cuchaz.enigma.command.mcp.tools.CallGraphTool;
import cuchaz.enigma.command.mcp.tools.ClassBytecodeSummaryTool;
import cuchaz.enigma.command.mcp.tools.ClassConstantsTool;
import cuchaz.enigma.command.mcp.tools.ClassDependenciesTool;
import cuchaz.enigma.command.mcp.tools.ClassStructureTool;
import cuchaz.enigma.command.mcp.tools.ControlFlowGraphTool;
import cuchaz.enigma.command.mcp.tools.DeadCodeTool;
import cuchaz.enigma.command.mcp.tools.DecompileClassTool;
import cuchaz.enigma.command.mcp.tools.DecompileMethodTool;
import cuchaz.enigma.command.mcp.tools.EnumAnalysisTool;
import cuchaz.enigma.command.mcp.tools.EntryPointsTool;
import cuchaz.enigma.command.mcp.tools.ExportMappingsTool;
import cuchaz.enigma.command.mcp.tools.ExportMarkdownTool;
import cuchaz.enigma.command.mcp.tools.FieldAccessTool;
import cuchaz.enigma.command.mcp.tools.FindOverridesTool;
import cuchaz.enigma.command.mcp.tools.GetMappingTool;
import cuchaz.enigma.command.mcp.tools.ImplementorsTool;
import cuchaz.enigma.command.mcp.tools.InheritanceTool;
import cuchaz.enigma.command.mcp.tools.InnerClassesTool;
import cuchaz.enigma.command.mcp.tools.LambdaAnalysisTool;
import cuchaz.enigma.command.mcp.tools.ListClassesTool;
import cuchaz.enigma.command.mcp.tools.MappingStatsTool;
import cuchaz.enigma.command.mcp.tools.MethodBodyTool;
import cuchaz.enigma.command.mcp.tools.MethodCallsTool;
import cuchaz.enigma.command.mcp.tools.MethodSimilarityTool;
import cuchaz.enigma.command.mcp.tools.PackageDependenciesTool;
import cuchaz.enigma.command.mcp.tools.PackageStatsTool;
import cuchaz.enigma.command.mcp.tools.PatternMatchTool;
import cuchaz.enigma.command.mcp.tools.ProjectInfoTool;
import cuchaz.enigma.command.mcp.tools.ReferencesTool;
import cuchaz.enigma.command.mcp.tools.SearchAnnotationsTool;
import cuchaz.enigma.command.mcp.tools.SearchClassesTool;
import cuchaz.enigma.command.mcp.tools.SearchMembersTool;
import cuchaz.enigma.command.mcp.tools.SearchStringConstantsTool;
import cuchaz.enigma.command.mcp.tools.SetClassMappingTool;
import cuchaz.enigma.command.mcp.tools.SetFieldMappingTool;
import cuchaz.enigma.command.mcp.tools.SetMethodMappingTool;
import cuchaz.enigma.command.mcp.tools.SuggestAnalysisTargetsTool;
import cuchaz.enigma.command.mcp.tools.TypeUsageTool;
import cuchaz.enigma.command.mcp.tools.UnmappedEntriesTool;
import cuchaz.enigma.command.mcp.tools.ValidateMappingsTool;

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
				// Method analysis
				new EntryPointsTool(project),
				new DecompileClassTool(project),
				new DecompileMethodTool(project),
				new MethodBodyTool(project),
				new ControlFlowGraphTool(project),
				new MethodSimilarityTool(project),
				// Reference analysis
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
				// Discovery
				new SuggestAnalysisTargetsTool(project),
				new UnmappedEntriesTool(project),
				new DeadCodeTool(project),
				// Mapping operations
				new GetMappingTool(project),
				new SetClassMappingTool(project),
				new SetMethodMappingTool(project),
				new SetFieldMappingTool(project),
				new BatchSetMappingsTool(project),
				new ValidateMappingsTool(project),
				// Export
				new ExportMappingsTool(project),
				new ExportMarkdownTool(project)
		);
	}
}
