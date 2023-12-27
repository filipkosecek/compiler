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

		List<Variable> argList;
		String argListCode;
		if (ctx.argList() != null) {
			Pair<List<Variable>, String> pair = functionArgumentListVisitor.visit(ctx.argList());
			argList = pair.p1;
			argListCode = pair.p2;
		} else {
			argList = List.of();
			argListCode = "";
		}

		ST functionDef = globalContext.templateGroup.getInstanceOf("functionDef");

		for (Variable arg : argList) {
			if (arg.isReference() || arg.getDimensionCount() > 0)
				continue;
			ST paramInit = globalContext.templateGroup.getInstanceOf("paramInit");
			String destReg = globalContext.getNewReg();
			paramInit.add("destReg", destReg);
			paramInit.add("type", globalContext.variableTypeToLLType(arg.getType()));
			paramInit.add("ptrType",
					globalContext.llPointer(arg.getType(), 1));
			paramInit.add("initValue", arg.getLlName());
			arg.setLlName(destReg);
			functionDef.add("paramInit", paramInit.render());
		}

		functionDef.add("returnType", globalContext.variableTypeToLLType(ctx.TYPE().getText()));
		functionDef.add("name", ctx.ID().getText());
		functionDef.add("argumentList", argListCode);
		functionDef.add("code", visit(ctx.codeBlock()));
		if(ctx.TYPE().getText().equals("void"))
			functionDef.add("voidFunction", true);

		globalContext.addFunctionToGlobalContext(ctx.ID().getText(),
				new Function(ctx.TYPE().getText(), argList));

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

	@Override
	public String visitVarDeclBlock(cssParser.VarDeclBlockContext ctx) {
		globalContext.setCurrentDeclarationType(ctx.TYPE().getText());
		ST template = globalContext.templateGroup.getInstanceOf("declarationBlock");
		for (int i = 0; i < ctx.declAssign().size(); ++i) {
			template.add("code", visit(ctx.declAssign(i)));
		}
		return template.render();
	}

	//TODO array declaration
	@Override
	public String visitDeclAssign(cssParser.DeclAssignContext ctx) {
		Variable var = globalContext.getVariable(ctx.ID().getText());
		String type = globalContext.getCurrentDeclarationType();
		if (var != null) {
			globalContext.handleFatalError("variable declared twice");
			throw new RuntimeException("gjhj");
		}

		/*
		 * TODO: add case for an array
		 *  also check if numbers of dimensions equal
		 */
		String register = globalContext.getNewReg();
		ST template = globalContext.templateGroup.getInstanceOf("simpleVarDeclaration");
		template.add("reg", register);
		template.add("type", globalContext.variableTypeToLLType(type));

		if (ctx.expression() != null) {
			Expression assignValue = new ExpressionVisitor(globalContext).visit(ctx.expression());
			if (!assignValue.type().equals(type)) {
				globalContext.handleFatalError("types don't match");
				throw new RuntimeException("ghji");
			}
			template.add("init", true);
			template.add("expressionCode", assignValue.code());
			template.add("value", assignValue.returnRegister());
			template.add("ptrType", globalContext.llPointer(type, 1));
		}
		var = new Variable(register, type, 0, false);
		globalContext.addToLastScope(ctx.ID().getText(), var);
		return template.render();
	}
}
