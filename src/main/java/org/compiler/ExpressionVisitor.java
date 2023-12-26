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
            arrayIndex.add("expressionCode", expression.code());
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

        if (!var.getType().equals(assignValue.type()))
            globalContext.handleFatalError("types don't match");

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

        /* assigning array to array */
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

    @Override
    public Expression visitTypeCastExpr(cssParser.TypeCastExprContext ctx) {
        Expression variable = visit(ctx.variable());
        String destinationType = ctx.TYPE().getText();
        String sourceType = variable.type();
        int destinationDimensionCount = ctx.LEFT_SQUARE().size();
        if (destinationDimensionCount != variable.dimensionCount())
            globalContext.handleFatalError("cannot type cast to another level");

        if (sourceType.equals(destinationType))
            return variable;

        /* array is type cast just as pointers are in C,
         * i.e. the underlying value is left untouched
         */
        if (destinationDimensionCount > 0)
            return new Expression(variable.code(), variable.returnRegister(),
                    destinationType, variable.dimensionCount(),
                    false, 0);

        if (
                (sourceType.equals("byte") && destinationType.equals("ubyte")) ||
                (sourceType.equals("ubyte") && destinationType.equals("byte")) ||
                (sourceType.equals("int") && destinationType.equals("uint"))   ||
                (sourceType.equals("uint") && destinationType.equals("int"))
        )
            return new Expression(variable.code(), variable.returnRegister(),
                    destinationType, variable.dimensionCount(),
                    variable.isNumericConstant(), variable.numericConstantValue());

        if ((sourceType.equals("byte") || sourceType.equals("ubyte")) && destinationType.equals("int")) {
            ST sext = globalContext.templateGroup.getInstanceOf("signExtend");
            String destReg = globalContext.getNewReg();
            sext.add("destReg", destReg);
            sext.add("value", variable.getValue());
            sext.add("srcType", globalContext.variableTypeToLLType(sourceType));
            sext.add("destType", globalContext.variableTypeToLLType(destinationType));
            return new Expression(variable.code() + "\n" + sext.render(), destReg,
                    destinationType, 0,
                    variable.isNumericConstant(), variable.numericConstantValue());
        }

        if ((sourceType.equals("byte") || sourceType.equals("ubyte")) && destinationType.equals("uint")) {
            ST zext = globalContext.templateGroup.getInstanceOf("zeroExtend");
            String destReg = globalContext.getNewReg();
            zext.add("destReg", destReg);
            zext.add("value", variable.getValue());
            zext.add("srcType", globalContext.variableTypeToLLType(sourceType));
            zext.add("destType", globalContext.variableTypeToLLType(destinationType));
            return new Expression(variable.code() + "\n" + zext.render(), destReg,
                    destinationType, 0,
                    variable.isNumericConstant(), variable.numericConstantValue());
        }

        if ((sourceType.equals("int") || sourceType.equals("uint")) &&
                destinationType.equals("byte") || destinationType.equals("ubyte")) {
            ST trunc = globalContext.templateGroup.getInstanceOf("truncate");
            String destReg = globalContext.getNewReg();
            trunc.add("destReg", destReg);
            trunc.add("srcType", globalContext.variableTypeToLLType(sourceType));
            trunc.add("destType", globalContext.variableTypeToLLType(destinationType));
            trunc.add("value", variable.getValue());
            return new Expression(variable.code() + "\n" + trunc.render(), destReg,
                    destinationType, 0,
                    variable.isNumericConstant(), variable.numericConstantValue());
        }
        /* all cases should be covered */
        return null;
    }

    @Override
    public Expression visitUnOpExpr(cssParser.UnOpExprContext ctx) {
        Expression expression = visit(ctx.expression());
        if (expression.dimensionCount() != 0)
            globalContext.handleFatalError("Unary operators can only be applied on non-array expressions.");

        String destReg = globalContext.getNewReg();
        switch (ctx.unOp.getType()) {
            case cssParser.LOGICAL_NOT:
                if (expression.isNumericConstant())
                    return new Expression(expression.code(), expression.returnRegister(),
                            expression.type(), expression.dimensionCount(),
                            expression.isNumericConstant(), expression.numericConstantValue() != 0 ? 1 : 0);

                ST logNot = globalContext.templateGroup.getInstanceOf("logicalNot");
                logNot.add("type", globalContext.variableTypeToLLType(expression.type()));
                logNot.add("value", expression.getValue());
                logNot.add("destReg", destReg);
                logNot.add("valueCode", expression.code());
                return new Expression(logNot.render(), destReg, expression.type(),
                        0, false, 0);
            case cssParser.MINUS:
                //TODO unsigned type shouldn't become negative
                if (expression.isNumericConstant())
                    return new Expression(expression.code(), expression.returnRegister(),
                            expression.type(), expression.dimensionCount(),
                            expression.isNumericConstant(), -expression.numericConstantValue());

                ST minus = globalContext.templateGroup.getInstanceOf("subtract");
                minus.add("destReg", destReg);
                minus.add("type", globalContext.variableTypeToLLType(expression.type()));
                minus.add("value1", "0");
                minus.add("value2", expression.getValue());
                String resultCode = expression.code() + "\n" + minus.render();
                return new Expression(resultCode, destReg, expression.type(),
                        0, false, 0);
        }
        return null;
    }

    private Expression genBinOpExpr(String templateName, String expressionCode,
                                    Expression first, Expression second) {
        String destReg = globalContext.getNewReg();
        ST template = globalContext.templateGroup.getInstanceOf(templateName);
        template.add("destReg", destReg);
        template.add("type", globalContext.variableTypeToLLType(first.type()));
        template.add("value1", first.getValue());
        template.add("value2", second.getValue());
        return new Expression(expressionCode + template.render(), destReg,
                first.type(), 0, false, 0);
    }

    @Override
    public Expression visitBinOpExpr(cssParser.BinOpExprContext ctx) {
        Expression first = visit(ctx.expression(0));
        Expression second = visit(ctx.expression(1));
        if (!first.type().equals(second.type()) ||
                first.dimensionCount() != second.dimensionCount() ||
                first.dimensionCount() != 0)
            globalContext.handleFatalError("type mismatch");

        String expressionCode = first + "\n" + second;
        String templateName = "";

        switch (ctx.binOp.getType()) {
            case cssParser.MULT:
                if (first.isNumericConstant() && second.isNumericConstant())
                    return new Expression(expressionCode, "", first.type(),
                            0, true,
                            first.numericConstantValue() * second.numericConstantValue());
                templateName = "multiplication";
                break;
            case cssParser.PLUS:
                if (first.isNumericConstant() && second.isNumericConstant())
                    return new Expression(expressionCode, "", first.type(),
                            0, true,
                            first.numericConstantValue() + second.numericConstantValue());
                templateName = "addition";
                break;
            case cssParser.DIV:
                if (first.isNumericConstant() && second.isNumericConstant()) {
                    if (second.numericConstantValue() == 0)
                        throw new RuntimeException("bad things happen");
                    return new Expression(expressionCode, "", first.type(),
                            0, true,
                            first.numericConstantValue() / second.numericConstantValue());
                }
                templateName = "division";
                break;
            case cssParser.MINUS:
                if (first.isNumericConstant() && second.isNumericConstant())
                    return new Expression(expressionCode, "", first.type(),
                            0, true,
                            first.numericConstantValue() - second.numericConstantValue());
                templateName = "subtract";
                break;
        }
        return genBinOpExpr(templateName, expressionCode, first, second);
    }
}
