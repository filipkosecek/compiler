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
		for (int i = 0; i < ctx.function().size(); ++i)
			programBodyTemplate.add("programBody", visit(ctx.function(i)));
		return programBodyTemplate.render();
	}

	@Override
	public String visitFunction(cssParser.FunctionContext ctx) {
		if (globalContext.containsFunction(ctx.ID().getText()))
			globalContext.handleFatalError("Function already declared.");

		globalContext.addNewScope();

		ST functionDef = globalContext.templateGroup.getInstanceOf("functionDef");
		functionDef.add("returnType", globalContext.variableTypeToLLType(ctx.TYPE().getText()));
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

	@Override
	public String visitCodeBlock(cssParser.CodeBlockContext ctx) {
		ST template = globalContext.templateGroup.getInstanceOf("codeBlock");
		for (int i = 0; i < ctx.codeFragment().size(); ++i) {
			template.add("lines", visit(ctx.codeFragment(i)));
		}
		return template.render();
	}

	@Override
	public String visitCodeFragmentExpr(cssParser.CodeFragmentExprContext ctx) {
		Expression expression = new ExpressionVisitor(globalContext).visit(ctx.expression());
		return expression.code();
	}
}
