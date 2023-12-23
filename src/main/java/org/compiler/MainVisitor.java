package org.compiler;
import org.gen.cssParser;
import org.stringtemplate.v4.*;
import org.gen.*;
import java.util.List;

public class MainVisitor extends cssBaseVisitor<String> {
	@Override
	public String visitProgram(cssParser.ProgramContext ctx) {
		ST programBodyTemplate = GlobalVars.templateGroup.getInstanceOf("program");
		/*
		for (int i = 0; i < ctx.varDeclBlock().size(); ++i) {
			programBodyTemplate.add("globalVars", visit(ctx.varDeclBlock(i)).code);
		}
		*/
		for (int i = 0; i < ctx.function().size(); ++i) {
			programBodyTemplate.add("programBody", visit(ctx.function(i)));
		}
		return programBodyTemplate.render();
	}

	@Override
	public String visitFunction(cssParser.FunctionContext ctx) {
		if (GlobalVars.functions.containsKey(ctx.ID().getText()))
			GlobalVars.handleFatalError("Function already declared.");

		GlobalVars.scopeStack.push(new ScopeInfo());

		ST functionDef = GlobalVars.templateGroup.getInstanceOf("functionDef");
		functionDef.add("returnType", GlobalVars.variableTypeToLLType.get(ctx.TYPE().getText()));
		functionDef.add("name", ctx.ID().getText());

		Function function;
		if (ctx.argList() != null) {
			Pair<List<Variable>, String> pair = ctx.argList().accept(GlobalVars.functionArgumentListVisitor);
			functionDef.add("argumentList", pair.p2);
			function = new Function(ctx.TYPE().getText(), pair.p1);
		} else {
			functionDef.add("argumentList", "");
			function = new Function(ctx.TYPE().getText());
		}

		functionDef.add("code", visit(ctx.codeBlock()));

		GlobalVars.functions.put(ctx.ID().getText(), function);

		GlobalVars.scopeStack.pop();
		
		return functionDef.render();
	}
}
