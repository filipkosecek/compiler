package org.compiler;

import org.gen.cssParser;
import org.stringtemplate.v4.*;
import org.gen.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Visits various constructs. Returns LLVM code.
 */
public class MainVisitor extends cssBaseVisitor<String> {
	/* this integer value are used in allocateArrayLevels
	 * to generate unique LLVM registers for loops
	 * which are generated when allocating memory for arrays
	 */
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
	 * Adds string library functions to global context.
	 * Warning: if changing the format string sizes, don't
	 * forget to change them in templates.stg.
	 * Warning: if changing the names of format strings,
	 * don't forget to change them in StatementVisitor.java
	 * in input and output handler function as well.
	 */
	private void addStringLibFunctions() {
		globalContext.addFunctionToGlobalContext("strlen", new Function(
				VarType.INT, List.of(new Variable("", VarType.BYTE, 1))
		));
		globalContext.addFunctionToGlobalContext("strcmp", new Function(
				VarType.INT, List.of(
						new Variable("", VarType.BYTE, 1),
						new Variable("", VarType.BYTE, 1)
		)
		));
		globalContext.addFunctionToGlobalContext("strcpy", new Function(
				VarType.VOID, List.of(
				new Variable("", VarType.BYTE, 1),
				new Variable("", VarType.BYTE, 1)
		)
		));
		globalContext.addFunctionToGlobalContext("strcat", new Function(
				VarType.VOID, List.of(
				new Variable("", VarType.BYTE, 1),
				new Variable("", VarType.BYTE, 1)
		)
		));
	}

	/**
	 * Visit initial non-terminal.
	 */
	@Override
	public String visitProgram(cssParser.ProgramContext ctx) {
		ST programBodyTemplate = globalContext.templateGroup.getInstanceOf("program");
		/* declare string library functions if import was specified */
		if (ctx.IMPORT_STRING_LIB() != null) {
			addStringLibFunctions();
			programBodyTemplate.add("importStringFunctions", true);
		}
		/* visit functions (at least one function must be defined) */
		for (int i = 0; i < ctx.function().size(); ++i)
			programBodyTemplate.add("programBody", visit(ctx.function(i)));
		/* fill in the template */
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
	 * Generates function code and adds function to the map of declared functions.
	 */
	@Override
	public String visitFunction(cssParser.FunctionContext ctx) {
		globalContext.currentFunctionName = ctx.ID().getText();
		if (globalContext.containsFunction(ctx.ID().getText()))
			globalContext.handleFatalError("function already declared");

		globalContext.addNewScope();
		VarType returnType = TypeVisitor.getInstance().visit(ctx.type());

		/* visit function argument list */
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
		globalContext.addFunctionToGlobalContext(ctx.ID().getText(), function);
		/* set current function */
		globalContext.currentFunction = function;
		ST functionDef = globalContext.templateGroup.getInstanceOf("functionDef");

		/*
		 * This section allocates memory on stack for local variables which
		 * are arguments for the function and copies the value passed as argument
		 * to the memory address. This enables users to assign to variables that
		 * were passed as arguments.
		 */
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

		/* fill in the template */
		Statement statement = StatementVisitor.getInstance(globalContext).visit(ctx.codeBlock());
		functionDef.add("returnType", globalContext.variableTypeToLLType(returnType));
		functionDef.add("name", ctx.ID().getText());
		functionDef.add("argumentList", argListCode);
		functionDef.add("code", statement.code());
		functionDef.add("firstLabel", statement.firstLabel());
		if(returnType == VarType.VOID)
			functionDef.add("voidFunction", true);

		globalContext.popScope();

		return functionDef.render();
	}

	/**
	 * Sets inherited attribute currentDeclarationType and visits
	 * children (declAssign) which perform the allocation itself.
	 * @param ctx the parse tree
	 */
	@Override
	public String visitVarDeclBlock(cssParser.VarDeclBlockContext ctx) {
		VarType type = TypeVisitor.getInstance().visit(ctx.type());
		/* set the inherited attribute */
		globalContext.setCurrentDeclarationType(type);
		ST template = globalContext.templateGroup.getInstanceOf("declarationBlock");
		for (int i = 0; i < ctx.declAssign().size(); ++i) {
			template.add("code", visit(ctx.declAssign(i)));
		}
		return template.render();
	}

	/**
	 * This recursive function generates loops which allocate memory
	 * for arrays. Memory is allocated in a C-like way. Each level contains
	 * pointers which point to a memory address base, where lower level
	 * pointers are allocated, these point to the next level and so on.
	 * Every memory is allocated on stack and the pointers are assigned properly.
	 * The loops are then 'concatenated' so that parent loop body contains
	 * child loop. The function is pretty ugly, but I am afraid there
	 * was no other option.
	 * @param sizes list of expressions representing dimension level size
	 * @param iterationVars list of loop iteration variables generated beforehand
	 * @param previousIncLabel label of the parent level loop
	 * @param previousAllocReg parent pointer which points to the current memory base
	 *                         which contains pointers of the current dimension level
	 * @param index current index in sizes list
	 * @param type actual type of the elements of the array being allocated
	 * @return code of the current loop and return its beginning label
	 * to the parent loop
	 */
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
		/* type of value representing amount of pointers in the current level allocated by parent */
		template.add("allocAmountParentType", globalContext.variableTypeToLLType(sizes.get(index - 1).type()));
		/* type of iteration variable */
		template.add("iType", globalContext.llPointer(sizes.get(index - 1).type(), 1));
		/* loop iteration variable */
		template.add("i", iterationVars.get(index));
		/* header label of current loop */
		template.add("loopHeaderLabel", loopHeaderLabel);
		/* compare for the loop header expression */
		template.add("cmp", globalContext.getNewReg());
		/* how many pointers are in current level */
		template.add("allocAmountParentReg", sizes.get(index - 1).returnRegister());
		/* temporary registers for template */
		template.add("reg1", globalContext.getNewReg());
		template.add("reg2", globalContext.getNewReg());
		/* label of current loop body */
		template.add("bodyLabel", globalContext.genNewLabel());
		/* end label of current loop */
		template.add("endLabel", previousIncLabel);
		template.add("reg4", reg4);
		/* type of lower level pointers, i.e. one star less */
		template.add("allocPtrType", globalContext.llPointer(type, level - 1));
		/* how many items to allocate - type */
		template.add("allocAmountCurrentType", globalContext.variableTypeToLLType(sizes.get(index).type()));
		/* how many items to allocate */
		template.add("allocAmountCurrentReg", sizes.get(index).returnRegister());
		/* type of parent pointer */
		template.add("parentPtrType", globalContext.llPointer(type, level + 1));
		/* parent pointer value */
		template.add("parentPtr", previousAllocReg);
		/* label of the loop beginning */
		template.add("begLoopLabel", begLoopLabel);
		template.add("reg5", globalContext.getNewReg());
		/* type of current pointer level */
		template.add("currentPtrType", globalContext.llPointer(type, level));
		/* label where iteration variable is incremented */
		template.add("incLabel", incLabel);

		/* base case for the lowest level pointer */
		if (level > 1) {
			template.add("addSubLoop", true);
			Pair<String, String> p = generateAllocLoops(sizes, iterationVars,
					incLabel, reg4, index + 1, type);
			template.add("subLoop", p.p1);
			template.add("subLoopLabel", p.p2);
		}
		return new Pair<>(template.render(), begLoopLabel);
	}

	/**
	 * Allocate the highest level array of pointers and save pointer to them.
	 * As opposed to lower levels, the top level allocation
	 * is not be translated to a loop.
	 * @param var variable to allocate
	 * @param sizes sizes which are passed to generateAllocLoops
	 * @return code
	 */
	private String allocateArrayLevels(Variable var, ArrayList<Expression> sizes) {
		ArrayList<String> iRegisters = new ArrayList<>(var.getDimensionCount());
		for (int i = 0; i < var.getDimensionCount(); ++i) {
			iRegisters.add(String.format("%%i%d_%d", i, iRegisterCounter++));
		}
		ST allocInit = globalContext.templateGroup.getInstanceOf("allocInit");
		/* generate code for iteration variables initialization (the highest does not need one) */
		for (int i = 0; i < var.getDimensionCount(); ++i) {
			if (i > 0) {
				ST alloca = globalContext.templateGroup.getInstanceOf("alloca");
				alloca.add("dest", iRegisters.get(i));
				alloca.add("type", globalContext.variableTypeToLLType(sizes.get(i - 1).type()));
				allocInit.add("indicesInitCode", alloca.render());
			}
			allocInit.add("exprCode", sizes.get(i).code());
		}

		/* generate code for highest level allocation */
		String endLabel = globalContext.genNewLabel();
		ST firstLoop = globalContext.templateGroup.getInstanceOf("firstAllocLoop");
		firstLoop.add("init", allocInit.render());
		String resultReg = var.getLlName();
		firstLoop.add("resultReg", resultReg);
		firstLoop.add("allocPtrType", globalContext.llPointer(var.getType(), sizes.size() - 1));
		firstLoop.add("allocAmountType", globalContext.variableTypeToLLType(sizes.get(0).type()));
		firstLoop.add("allocAmountReg", sizes.get(0).returnRegister());
		/* loops don't need to be generated if only one level is to be allocated */
		if (sizes.size() > 1) {
			firstLoop.add("addSubLoop", true);
			firstLoop.add("endLabel", endLabel);
			Pair<String, String> p = generateAllocLoops(sizes, iRegisters, endLabel, resultReg, 1, var.getType());
			firstLoop.add("subLoop", p.p1);
			firstLoop.add("subLoopLabel", p.p2);
		}
		return firstLoop.render();
	}

	/**
	 * Visits children expression for array level/dimension
	 * sizes and creates a list of them. It is then passed to above functions
	 * to generate code for array allocation.
	 */
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
		/* every array must either contain all sizes of dimensions or none */
		if (containsNull && containsSome) {
			globalContext.handleFatalError("when declaring an array, either" +
					"every dimension must have a specified size or none");
		}

		Variable var = new Variable(globalContext.getNewReg(), type, ctx.declTypeArray().size());
		Expression assignValue = null;
		if (ctx.expression() != null)
			assignValue = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());

		String code;
		/* an array whose size is to be allocated cannot be assigned another array */
		if (!containsSome) {
			if (assignValue != null) {
				code = assignValue.code();
				var.setLlName(assignValue.returnRegister());
				if (assignValue.type() != var.getType() || assignValue.dimensionCount() != var.getDimensionCount()) {
					globalContext.handleFatalError("type mismatch at declaration of '" +
							ctx.ID().getText() +
							"'");
				}
			} else {
				code = "";
			}
		} else {
			code = allocateArrayLevels(var, sizes);
			if (assignValue != null) {
				globalContext.handleFatalError("cannot assign to an array with specified sizes at declaration");
			}
		}
		globalContext.addToLastScope(ctx.ID().getText(), var);
		return code;
	}

	/**
	 * Generate code for variable allocation.
	 * @param ctx the parse tree
	 */
	@Override
	public String visitDeclAssign(cssParser.DeclAssignContext ctx) {
		Variable var = globalContext.getVariable(ctx.ID().getText());
		VarType type = globalContext.getCurrentDeclarationType();
		if (type == VarType.VOID)
			globalContext.handleFatalError("cannot declare a variable '" +
					ctx.ID().getText() +
					"' of type void");

		if (var != null) {
			globalContext.handleFatalError("variable '" +
					ctx.ID().getText() +
					"' declared more than once");
		}

		/* use above functions to handle arrays */
		if (!ctx.declTypeArray().isEmpty())
			return visitDeclAssignArray(ctx, type);

		String register = globalContext.getNewReg();
		ST template = globalContext.templateGroup.getInstanceOf("simpleVarDeclaration");
		template.add("reg", register);
		template.add("type", globalContext.variableTypeToLLType(type));

		/* assign an expression to the newly allocated variable */
		if (ctx.expression() != null) {
			Expression assignValue = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
			if (assignValue.type() != type) {
				globalContext.handleFatalError("type mismatch at declaration of '" +
						ctx.ID().getText() +
						"'");
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
