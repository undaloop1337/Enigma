package cuchaz.enigma.command.mcp;

import java.util.List;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.tools.CallGraphTool;
import cuchaz.enigma.command.mcp.tools.ClassBytecodeSummaryTool;
import cuchaz.enigma.command.mcp.tools.ClassConstantsTool;
import cuchaz.enigma.command.mcp.tools.ClassDependenciesTool;
import cuchaz.enigma.command.mcp.tools.ClassStructureTool;
import cuchaz.enigma.command.mcp.tools.DecompileClassTool;
import cuchaz.enigma.command.mcp.tools.EntryPointsTool;
import cuchaz.enigma.command.mcp.tools.FieldAccessTool;
import cuchaz.enigma.command.mcp.tools.GetMappingTool;
import cuchaz.enigma.command.mcp.tools.ImplementorsTool;
import cuchaz.enigma.command.mcp.tools.InheritanceTool;
import cuchaz.enigma.command.mcp.tools.ListClassesTool;
import cuchaz.enigma.command.mcp.tools.MappingStatsTool;
import cuchaz.enigma.command.mcp.tools.MethodBodyTool;
import cuchaz.enigma.command.mcp.tools.MethodCallsTool;
import cuchaz.enigma.command.mcp.tools.PackageStatsTool;
import cuchaz.enigma.command.mcp.tools.ProjectInfoTool;
import cuchaz.enigma.command.mcp.tools.ReferencesTool;
import cuchaz.enigma.command.mcp.tools.SearchClassesTool;
import cuchaz.enigma.command.mcp.tools.SearchMembersTool;
import cuchaz.enigma.command.mcp.tools.SearchStringConstantsTool;
import cuchaz.enigma.command.mcp.tools.SetClassMappingTool;
import cuchaz.enigma.command.mcp.tools.SetFieldMappingTool;
import cuchaz.enigma.command.mcp.tools.SetMethodMappingTool;
import cuchaz.enigma.command.mcp.tools.SuggestAnalysisTargetsTool;
import cuchaz.enigma.command.mcp.tools.UnmappedEntriesTool;

public final class EnigmaMcpTools {
	private EnigmaMcpTools() {
	}

	public static List<McpTool> create(EnigmaProject project) {
		return List.of(
				new ProjectInfoTool(project),
				new PackageStatsTool(project),
				new MappingStatsTool(project),
				new ListClassesTool(project),
				new SearchClassesTool(project),
				new SearchMembersTool(project),
				new SearchStringConstantsTool(project),
				new ClassStructureTool(project),
				new ClassBytecodeSummaryTool(project),
				new ClassConstantsTool(project),
				new ClassDependenciesTool(project),
				new EntryPointsTool(project),
				new DecompileClassTool(project),
				new MethodBodyTool(project),
				new ReferencesTool(project),
				new MethodCallsTool(project),
				new CallGraphTool(project),
				new FieldAccessTool(project),
				new InheritanceTool(project),
				new ImplementorsTool(project),
				new SuggestAnalysisTargetsTool(project),
				new UnmappedEntriesTool(project),
				new GetMappingTool(project),
				new SetClassMappingTool(project),
				new SetMethodMappingTool(project),
				new SetFieldMappingTool(project)
		);
	}
}
