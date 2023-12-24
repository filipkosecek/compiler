package org.compiler;
import org.gen.cssParser;
import org.stringtemplate.v4.*;
import org.gen.*;
import java.util.List;

public class MainVisitor extends cssBaseVisitor<String> {
	private final GlobalContext globalContext;
	private final FunctionArgumentListVisitor functionArgumentListVisitor;

	public MainVisitor(GlobalContext globalContext) {
		this.globalContext = globalContext;
		functionArgumentListVisitor = new FunctionArgumentListVisitor(globalContext);
	}
	@Override
	public String visitProgram(cssParser.ProgramContext ctx) {
		ST programBodyTemplate = globalContext.templateGroup.getInstanceOf("program");
		StringBuilder sb = new StringBuilder();
		/*
		for (int i = 0; i < ctx.varDeclBlock().size(); ++i) {
			programBodyTemplate.add("globalContext", visit(ctx.varDeclBlock(i)).code);
		}
		*/
		for (int i = 0; i < ctx.function().size(); ++i) {
			sb.append(visit(ctx.function(i)));
			sb.append('\n');
		}
		programBodyTemplate.add("programBody", sb.toString());
		return programBodyTemplate.render();
	}

	@Override
	public String visitFunction(cssParser.FunctionContext ctx) {
		if (globalContext.containsFunction(ctx.ID().getText()))
			globalContext.handleFatalError("Function already declared.");

		globalContext.addNewScope();

		ST functionDef = globalContext.templateGroup.getInstanceOf("functionDef");
		functionDef.add("returnType", globalContext.variableTypeToLLType.get(ctx.TYPE().getText()));
		functionDef.add("name", ctx.ID().getText());

		Function function;
		if (ctx.argList() != null) {
			Pair<List<Variable>, String> pair = functionArgumentListVisitor.visit(ctx.argList());
			functionDef.add("argumentList", pair.p2);
			function = new Function(ctx.TYPE().getText(), pair.p1);
		} else {
			functionDef.add("argumentList", "");
			function = new Function(ctx.TYPE().getText());
		}

		functionDef.add("code", visit(ctx.codeBlock()));

		globalContext.addFunctionToGlobalContext(ctx.ID().getText(), function);

		globalContext.popScope();

		return functionDef.render();
	}
}
