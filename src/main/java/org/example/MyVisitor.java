package org.example;
import org.gen.cssParser;
import org.stringtemplate.v4.*;

import java.util.LinkedList;
import org.gen.*;

public class MyVisitor  extends cssBaseVisitor<CodeFragment> {
	private STGroup templateGroup = new STGroupFile("../../../resources/templates.stg");
	private VariableType inheritedType;
	/* scope information */
	private LinkedList<ScopeInfo> scopeStack = new LinkedList<>();
	int idCounter = 1;

	private void handleErrors(String message) {
		System.err.println(message);
		System.exit(1);
	}

	private String getNewReg() {
		return String.format("reg%d", idCounter++);
	}

	private String genNewLabel() {
		return String.format("label%d", idCounter++);
	}

	@Override
	public CodeFragment visitProgram(cssParser.ProgramContext ctx) {
		ST programBodyTemplate = templateGroup.getInstanceOf("program");
		/*
		for (int i = 0; i < ctx.varDeclBlock().size(); ++i) {
			programBodyTemplate.add("globalVars", visit(ctx.varDeclBlock(i)).code);
		}
		*/
		for (int i = 0; i < ctx.function().size(); ++i) {
			programBodyTemplate.add("function", visit(ctx.function(i)).code);
		}
		return new CodeFragment(programBodyTemplate.render());
	}

	@Override
	public CodeFragment visitVarDeclBlock(cssParser.VarDeclBlockContext ctx) {
		switch (ctx.TYPE().getText()) {
			case "BYTE":
				inheritedType = VariableType.BYTE;
				break;
			case "UBYTE":
				inheritedType = VariableType.UBYTE;
				break;
			case "INT":
				inheritedType = VariableType.INT;
				break;
			case "UINT":
				inheritedType = VariableType.UINT;
				break;
			case "VOID":
				handleErrors("A variable cannot be of type 'void'");
				break;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ctx.declAssign().size(); ++i) {
			sb.append(visit(ctx.declAssign(i)).code);
		}
		return new CodeFragment(sb.toString());
	}

	@Override
	public CodeFragment visitDeclTypeArray(cssParser.DeclTypeArrayContext ctx) {
		if (ctx.expression() == null)
			return new CodeFragment();
		return visit(ctx.expression());
	}
}
