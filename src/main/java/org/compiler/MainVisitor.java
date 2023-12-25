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

	/**
	 * Visit initial non-terminal.
	 */
	@Override
	public String visitProgram(cssParser.ProgramContext ctx) {
		ST programBodyTemplate = globalContext.templateGroup.getInstanceOf("program");
		for (int i = 0; i < ctx.function().size(); ++i)
			programBodyTemplate.add("programBody", visit(ctx.function(i)));
		for (String s : globalContext.globalStrings.keySet()) {
			ST globString = globalContext.templateGroup.getInstanceOf("globalString");
			globString.add("name", s);
			String body = globalContext.globalStrings.get(s);
			globString.add("size", String.valueOf(body.length() + 1));
			globString.add("body", body);
			programBodyTemplate.add("globalVariables", globString.render());
		}
		return programBodyTemplate.render();
	}

	/**
	 * Generate function code and add function to the map of declared functions.
	 */
	@Override
	public String visitFunction(cssParser.FunctionContext ctx) {
		if (globalContext.containsFunction(ctx.ID().getText()))
			globalContext.handleFatalError("Function already declared.");

		globalContext.addNewScope();

		Pair<List<Variable>, String> pair = functionArgumentListVisitor.visit(ctx.argList());
		ST functionDef = globalContext.templateGroup.getInstanceOf("functionDef");
		functionDef.add("returnType", globalContext.variableTypeToLLType(ctx.TYPE().getText()));
		functionDef.add("name", ctx.ID().getText());
		functionDef.add("label", globalContext.genNewLabel());
		functionDef.add("argumentList", pair.p2);
		functionDef.add("code", visit(ctx.codeBlock()));

		List<Variable> argList = pair.p1;
		for (Variable arg : argList) {
			if (arg.isReference() || arg.getDimensionCount() > 0)
				continue;
			ST paramInit = globalContext.templateGroup.getInstanceOf("paramInit");
			String destReg = globalContext.getNewReg();
			paramInit.add("destReg", destReg);
			paramInit.add("type", arg.getType());
			paramInit.add("ptrType",
					globalContext.pointer(globalContext.variableTypeToLLType(arg.getType()), 1));
			paramInit.add("initValue", arg.getLlName());
			arg.setLlName(destReg);
			functionDef.add("paramInit", paramInit.render());
		}

		globalContext.addFunctionToGlobalContext(ctx.ID().getText(),
				new Function(globalContext.variableTypeToLLType(ctx.TYPE().getText())));

		globalContext.popScope();

		return functionDef.render();
	}

	/**
	 * Generate code for code block.
	 */
	@Override
	public String visitCodeBlock(cssParser.CodeBlockContext ctx) {
		ST template = globalContext.templateGroup.getInstanceOf("codeBlock");
		for (int i = 0; i < ctx.codeFragment().size(); ++i) {
			template.add("lines", visit(ctx.codeFragment(i)));
		}
		return template.render();
	}

	/**
	 * Generate code for a line of code.
	 */
	@Override
	public String visitCodeFragmentExpr(cssParser.CodeFragmentExprContext ctx) {
		Expression expression = new ExpressionVisitor(globalContext).visit(ctx.expression());
		return expression.code();
	}
}
