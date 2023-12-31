package org.compiler;

import org.gen.cssParser;
import org.stringtemplate.v4.*;
import org.gen.*;

import java.util.ArrayList;
import java.util.List;

public class MainVisitor extends cssBaseVisitor<String> {
	/* these three integer values are used in allocateArrayLevels */
	private int iRegisterCounter = 1;

	private static MainVisitor instance = null;
	public static MainVisitor getInstance(GlobalContext globalContext) {
		if (instance == null)
			instance = new MainVisitor(globalContext);
		return instance;
	}

	private final GlobalContext globalContext;
	private final FunctionArgumentListVisitor functionArgumentListVisitor;

	private MainVisitor(GlobalContext globalContext) {
		this.globalContext = globalContext;
		functionArgumentListVisitor = FunctionArgumentListVisitor.getInstance(globalContext);
	}

	/**
	 * Add format strings to the map of global strings.
	 * String names must match with those
	 * used in functions handling input and output.
	 */
	private void addFormatStrings() {
		globalContext.globalStrings.put("@formatByte", "%c");
		globalContext.globalStrings.put("@formatUbyte", "%u");
		globalContext.globalStrings.put("@formatInt", "%d");
		globalContext.globalStrings.put("@formatUint", "%u");
		globalContext.globalStrings.put("@formatStr", "%s");
		globalContext.globalStrings.put("@formatEndLine", "\n");
	}

	/**
	 * Visit initial non-terminal.
	 */
	@Override
	public String visitProgram(cssParser.ProgramContext ctx) {
		addFormatStrings();
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
		VarType returnType = TypeVisitor.getInstance().visit(ctx.type());

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

		Function function = new Function(returnType, argList);
		globalContext.currentFunction = function;
		ST functionDef = globalContext.templateGroup.getInstanceOf("functionDef");

		for (Variable arg : argList) {
			if (arg.getDimensionCount() > 0)
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

		Statement statement = StatementVisitor.getInstance(globalContext).visit(ctx.codeBlock());
		functionDef.add("returnType", globalContext.variableTypeToLLType(returnType));
		functionDef.add("name", ctx.ID().getText());
		functionDef.add("argumentList", argListCode);
		functionDef.add("code", statement.code());
		functionDef.add("firstLabel", statement.firstLabel());
		if(returnType == VarType.VOID)
			functionDef.add("voidFunction", true);

		globalContext.addFunctionToGlobalContext(ctx.ID().getText(), function);

		globalContext.popScope();

		return functionDef.render();
	}

	@Override
	public String visitVarDeclBlock(cssParser.VarDeclBlockContext ctx) {
		VarType type = TypeVisitor.getInstance().visit(ctx.type());
		globalContext.setCurrentDeclarationType(type);
		ST template = globalContext.templateGroup.getInstanceOf("declarationBlock");
		for (int i = 0; i < ctx.declAssign().size(); ++i) {
			template.add("code", visit(ctx.declAssign(i)));
		}
		return template.render();
	}

	private Pair<String, String> generateAllocLoops(ArrayList<Expression> sizes,
													  ArrayList<String> iterationVars,
									  				String previousIncLabel,
													String previousAllocReg,
													int index,
													VarType type) {
		int level = sizes.size() - index;
		String incLabel = globalContext.genNewLabel();
		String begLoopLabel = globalContext.genNewLabel();
		String loopHeaderLabel = globalContext.genNewLabel();
		String reg4 = globalContext.getNewReg();
		ST template = globalContext.templateGroup.getInstanceOf("allocLoop");
		template.add("allocAmountParentType", globalContext.variableTypeToLLType(sizes.get(index - 1).type()));
		template.add("iType", globalContext.llPointer(sizes.get(index - 1).type(), 1));
		template.add("i", iterationVars.get(index));
		template.add("loopHeaderLabel", loopHeaderLabel);
		template.add("cmp", globalContext.getNewReg());
		template.add("allocAmountParentReg", sizes.get(index - 1).returnRegister());
		template.add("reg1", globalContext.getNewReg());
		template.add("reg2", globalContext.getNewReg());
		template.add("bodyLabel", globalContext.genNewLabel());
		template.add("endLabel", previousIncLabel);
		template.add("reg4", reg4);
		template.add("allocPtrType", globalContext.llPointer(type, level - 1));
		template.add("allocAmountCurrentType", globalContext.variableTypeToLLType(sizes.get(index).type()));
		template.add("allocAmountCurrentReg", sizes.get(index).returnRegister());
		template.add("parentPtrType", globalContext.llPointer(type, level + 1));
		template.add("parentPtr", previousAllocReg);
		template.add("begLoopLabel", begLoopLabel);
		template.add("reg5", globalContext.getNewReg());
		template.add("currentPtrType", globalContext.llPointer(type, level));
		template.add("incLabel", incLabel);

		if (level > 1) {
			template.add("addSubLoop", true);
			Pair<String, String> p = generateAllocLoops(sizes, iterationVars,
					incLabel, reg4, index + 1, type);
			template.add("subLoop", p.p1);
			template.add("subLoopLabel", p.p2);
		}
		return new Pair<>(template.render(), begLoopLabel);
	}

	private String allocateArrayLevels(Variable var, ArrayList<Expression> sizes) {
		ArrayList<String> iRegisters = new ArrayList<>(var.getDimensionCount());
		for (int i = 0; i < var.getDimensionCount(); ++i) {
			iRegisters.add(String.format("%%i%d_%d", i, iRegisterCounter++));
		}
		ST allocInit = globalContext.templateGroup.getInstanceOf("allocInit");
		for (int i = 0; i < var.getDimensionCount(); ++i) {
			if (i > 0) {
				ST alloca = globalContext.templateGroup.getInstanceOf("alloca");
				alloca.add("dest", iRegisters.get(i));
				alloca.add("type", globalContext.variableTypeToLLType(sizes.get(i - 1).type()));
				allocInit.add("indicesInitCode", alloca.render());
			}
			allocInit.add("exprCode", sizes.get(i).code());
		}

		String endLabel = globalContext.genNewLabel();
		ST firstLoop = globalContext.templateGroup.getInstanceOf("firstAllocLoop");
		firstLoop.add("init", allocInit.render());
		String resultReg = var.getLlName();
		firstLoop.add("resultReg", resultReg);
		firstLoop.add("allocPtrType", globalContext.llPointer(var.getType(), sizes.size() - 1));
		firstLoop.add("allocAmountType", globalContext.variableTypeToLLType(sizes.getFirst().type()));
		firstLoop.add("allocAmountReg", sizes.getFirst().returnRegister());
		if (sizes.size() > 1) {
			firstLoop.add("addSubLoop", true);
			firstLoop.add("endLabel", endLabel);
			Pair<String, String> p = generateAllocLoops(sizes, iRegisters, endLabel, resultReg, 1, var.getType());
			firstLoop.add("subLoop", p.p1);
			firstLoop.add("subLoopLabel", p.p2);
		}
		return firstLoop.render();
	}

	private String visitDeclAssignArray(cssParser.DeclAssignContext ctx, VarType type) {
		ArrayList<Expression> sizes = new ArrayList<>(ctx.declTypeArray().size());
		boolean containsNull = false, containsSome = false;
		for (int i = 0; i < ctx.declTypeArray().size(); ++i) {
			Expression size = ExpressionVisitor.getInstance(globalContext).visit(ctx.declTypeArray(i));
			if (size == null) {
				containsNull = true;
			} else {
				containsSome = true;
				sizes.add(size);
			}
		}
		if (containsNull && containsSome) {
			throw new RuntimeException("Array declaration must either have all levels empty or full.");
		}

		Variable var = new Variable(globalContext.getNewReg(), type, ctx.declTypeArray().size());
		Expression assignValue = null;
		if (ctx.expression() != null)
			assignValue = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());

		String code;
		if (!containsSome) {
			if (assignValue != null) {
				code = assignValue.code();
				var.setLlName(assignValue.returnRegister());
				if (assignValue.type() != var.getType() || assignValue.dimensionCount() != var.getDimensionCount()) {
					throw new RuntimeException("Types don't match.");
				}
			} else {
				code = "";
			}
		} else {
			code = allocateArrayLevels(var, sizes);
			if (assignValue != null) {
				throw new RuntimeException("Cannot assign during variable length array allocation.");
			}
		}
		globalContext.addToLastScope(ctx.ID().getText(), var);
		return code;
	}

	@Override
	public String visitDeclAssign(cssParser.DeclAssignContext ctx) {
		Variable var = globalContext.getVariable(ctx.ID().getText());
		VarType type = globalContext.getCurrentDeclarationType();
		if (type == VarType.VOID)
			globalContext.handleFatalError("cannot declare a variable of type void");

		if (var != null) {
			globalContext.handleFatalError("variable declared twice");
			throw new RuntimeException("gjhj");
		}

		if (!ctx.declTypeArray().isEmpty())
			return visitDeclAssignArray(ctx, type);

		String register = globalContext.getNewReg();
		ST template = globalContext.templateGroup.getInstanceOf("simpleVarDeclaration");
		template.add("reg", register);
		template.add("type", globalContext.variableTypeToLLType(type));

		if (ctx.expression() != null) {
			Expression assignValue = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
			if (assignValue.type() != type) {
				globalContext.handleFatalError("types don't match");
				throw new RuntimeException("ghji");
			}
			template.add("init", true);
			template.add("expressionCode", assignValue.code());
			template.add("value", assignValue.returnRegister());
			template.add("ptrType", globalContext.llPointer(type, 1));
		}
		var = new Variable(register, type, 0);
		globalContext.addToLastScope(ctx.ID().getText(), var);
		return template.render();
	}
}
