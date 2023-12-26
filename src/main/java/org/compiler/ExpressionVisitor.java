package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

import java.util.ArrayList;
import java.util.List;

public class ExpressionVisitor extends cssBaseVisitor<Expression> {
    private final GlobalContext globalContext;

    public ExpressionVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    /**
     * Visit literal expressions.
     * @param ctx the parse tree
     * @return Expression structure
     */
    @Override
    public Expression visitBaseExpr(cssParser.BaseExprContext ctx) {
        switch (ctx.base.getType()) {
            case cssParser.STRING:
                String name = globalContext.getNewGlobalStringName();
                String destReg = globalContext.getNewReg();
                ST code = globalContext.templateGroup.getInstanceOf("globalStringAccess");
                code.add("dest", destReg);
                code.add("size", String.valueOf(ctx.STRING().getText().length() - 2 + 1));
                code.add("name", name);
                StringBuilder sb = new StringBuilder(ctx.STRING().getText());
                sb.deleteCharAt(ctx.STRING().getText().length() - 1);
                sb.deleteCharAt(0);
                globalContext.globalStrings.put(name, sb.toString());
                return new Expression(code.render(), destReg, "byte",
                        1, false, 0);
            case cssParser.CHAR:
                return new Expression("", "", "byte", 0,
                        true, ctx.CHAR().getText().charAt(1));
            case cssParser.INT:
                return new Expression("", "", "int", 0,
                        true, Integer.parseInt(ctx.INT().getText()));
        }
        return null;
    }

    /**
     * Return code and Expression structure which corresponds
     * to array access.
     */
    private Expression arrayAccess(Variable var, List<Expression> expressionList, int n) {
        String destReg = var.getLlName();
        ST multiLevelIndexing = globalContext.templateGroup.getInstanceOf("arrayMultiLevelIndexing");
        for (int i = 0; i < n; ++i) {
            Expression expression = expressionList.get(i);
            String ptrReg = destReg;
            destReg = globalContext.getNewReg();
            ST arrayIndex = globalContext.templateGroup.getInstanceOf("arrayIndexing");
            arrayIndex.add("tmpReg", globalContext.getNewReg());
            arrayIndex.add("destReg", destReg);
            arrayIndex.add("ptrReg", ptrReg);
            String ptrType = globalContext.llPointer(var.getType(),
                    var.getDimensionCount() - i);
            String tmpDestType = globalContext.llPointer(var.getType(),
                    var.getDimensionCount() - i - 1);
            arrayIndex.add("destType", tmpDestType);
            arrayIndex.add("ptrType", ptrType);
            arrayIndex.add("indexType", globalContext.variableTypeToLLType(expression.type()));
            arrayIndex.add("indexReg", expression.getValue());
            arrayIndex.add("previousCode", expression.code());
            multiLevelIndexing.add("indexing", arrayIndex.render());
        }

        return new Expression(multiLevelIndexing.render(), destReg,
                var.getType(), var.getDimensionCount() - n,
                false, 0);
    }

    /**
     * Return an Expression structure containing code
     * which corresponds to a dereference and its value.
     */
    private Expression dereferenceLocalVar(Variable var) {
        ST template = globalContext.templateGroup.getInstanceOf("dereference");
        String destReg = globalContext.getNewReg();
        template.add("destReg", destReg);
        template.add("destType", globalContext.variableTypeToLLType(var.getType()));
        template.add("ptr", var.getLlName());
        template.add("ptrType",
                globalContext.llPointer(var.getType(), 1));
        return new Expression(template.render(), destReg, var.getType(),
                0, false, 0);
    }

    private boolean checkVariable(Variable variable, int expressionDimensions) {
        if (variable == null)
            globalContext.handleFatalError("undeclared variable");
        if (variable.getDimensionCount() < expressionDimensions)
            globalContext.handleFatalError("Too much indexing.");
        return true;
    }

    /**
     * Return value of a variable or array.
     */
    @Override
    public Expression visitVariable(cssParser.VariableContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        checkVariable(var, ctx.expression().size());

        if (var.getDimensionCount() == 0)
            return dereferenceLocalVar(var);

        ArrayList<Expression> subexpressions = new ArrayList<>(ctx.expression().size());
        for (int i = 0; i < ctx.expression().size(); ++i) {
            Expression subexpression = visit(ctx.expression(i));
            if (subexpression.dimensionCount() != 0)
                globalContext.handleFatalError("Index to an array must not be an array.");
            subexpressions.add(subexpression);
        }

        return arrayAccess(var, subexpressions, subexpressions.size());
    }

    @Override
    public Expression visitAssignExpr(cssParser.AssignExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        checkVariable(var, ctx.expression().size() - 1);
        Expression assignValue = visit(ctx.expression().getLast());
        if (var.getDimensionCount() - (ctx.expression().size() - 1) != assignValue.dimensionCount())
            globalContext.handleFatalError("assign type don't match");

        /* variables and references */
        if (var.getDimensionCount() == 0) {
            ST store = globalContext.templateGroup.getInstanceOf("writeExpression");
            store.add("valueType", globalContext.variableTypeToLLType(var.getType()));
            store.add("value", assignValue.getValue());
            store.add("ptrType",
                    globalContext.llPointer(var.getType(), 1));
            store.add("ptr", var.getLlName());
            store.add("expressionCode", assignValue.code());
            return new Expression(store.render(), assignValue.getValue(),
                    assignValue.type(), assignValue.dimensionCount(), assignValue.isNumericConstant(),
                    assignValue.numericConstantValue());
        }

        if (ctx.expression().size() == 1) {
            globalContext.assignNewRegister(ctx.ID().getText(), assignValue.getValue());
            return new Expression(assignValue.code(), assignValue.returnRegister(),
                    assignValue.type(), assignValue.dimensionCount(), assignValue.isNumericConstant(),
                    assignValue.numericConstantValue());
        }

        /* arrays with access*/
        ST arrayWrite = globalContext.templateGroup.getInstanceOf("arrayWrite");
        ArrayList<Expression> indices = new ArrayList<>(ctx.expression().size() - 1);
        for (int i = 0; i < ctx.expression().size() - 1; ++i) {
            indices.add(visit(ctx.expression(i)));
        }

        Expression arrayAccess = arrayAccess(var, indices, indices.size() - 1);
        Expression lastLevelIndex = indices.getLast();

        arrayWrite.add("previousCode", arrayAccess.code());
        arrayWrite.add("tmpReg", globalContext.getNewReg());
        arrayWrite.add("valueType",
                globalContext.llPointer(assignValue.type(),
                        assignValue.dimensionCount()));
        arrayWrite.add("ptrType", globalContext.llPointer(
                var.getType(),
                var.getDimensionCount() - (indices.size() - 1)
        ));
        arrayWrite.add("ptr", arrayAccess.returnRegister());
        arrayWrite.add("indexType", lastLevelIndex.type());
        arrayWrite.add("index", lastLevelIndex.getValue());
        arrayWrite.add("value", assignValue.getValue());
        arrayWrite.add("expressionCode", assignValue.code());

        return new Expression(arrayWrite.render(), assignValue.getValue(), assignValue.type(),
                assignValue.dimensionCount(), assignValue.isNumericConstant(),
                assignValue.numericConstantValue());
    }

    @Override
    public Expression visitSubExpr(cssParser.SubExprContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public Expression visitFuncCallExpr(cssParser.FuncCallExprContext ctx) {
        Function function = globalContext.getFunction(ctx.ID().getText());
        if (function == null)
            globalContext.handleFatalError("function declaration must precede its first use");

        String argList = "";
        StringBuilder code = new StringBuilder();
        if (ctx.funcParamList() != null) {
            ST argListTemplate = globalContext.templateGroup.getInstanceOf("argList");
            List<Expression> parameters = new FuncParamListVisitor(globalContext).visit(ctx.funcParamList());
            List<Variable> signature = function.getArguments();
            if (parameters.size() != function.getArgumentCount())
                globalContext.handleFatalError("Function argument count does not match.");

            for (int i = 0; i < parameters.size(); ++i) {
                Variable signatureVar = signature.get(i);
                Expression parameter = parameters.get(i);
                if (!parameter.type().equals(signatureVar.getType()) ||
                        parameter.dimensionCount() != signatureVar.getDimensionCount())
                    globalContext.handleFatalError("Signature does not match.");
                String parameterType = globalContext.llPointer(parameter.type(), parameter.dimensionCount());
                argListTemplate.add("arg", parameterType + " " + parameter.getValue());
                code.append(parameter.code());
                code.append('\n');
            }
            argList = argListTemplate.render();
        }

        ST functionCall = globalContext.templateGroup.getInstanceOf("functionCall");
        functionCall.add("returnType", globalContext.variableTypeToLLType(function.getReturnType()));
        functionCall.add("id", ctx.ID().getText());
        functionCall.add("argList", argList);
        functionCall.add("computeParameters", code.toString());
        String destReg = "";
        if (!function.getReturnType().equals("void")) {
            destReg = globalContext.getNewReg();
            functionCall.add("returnValue", true);
            functionCall.add("destReg", destReg);
        }
        return new Expression(functionCall.render(), destReg, function.getReturnType(),
                0, false, 0);
    }
}
