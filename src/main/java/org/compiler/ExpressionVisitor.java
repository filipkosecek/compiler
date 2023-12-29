package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

import java.util.List;

public class ExpressionVisitor extends cssBaseVisitor<Expression> {
    private static ExpressionVisitor instance = null;

    public static ExpressionVisitor getInstance(GlobalContext globalContext) {
        if (instance == null)
            instance = new ExpressionVisitor(globalContext);
        return instance;
    }

    private final GlobalContext globalContext;

    private ExpressionVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    /**
     * Visit literal expressions.
     * @param ctx the parse tree
     * @return Expression structure
     */
    @Override
    public Expression visitBaseExpr(cssParser.BaseExprContext ctx) {
        String destReg = globalContext.getNewReg();
        ST template;
        switch (ctx.base.getType()) {
            case cssParser.STRING:
                String name = globalContext.getNewGlobalStringName();
                ST code = globalContext.templateGroup.getInstanceOf("globalStringAccess");
                code.add("dest", destReg);
                code.add("size", String.valueOf(ctx.STRING().getText().length() - 2 + 1));
                code.add("name", name);
                StringBuilder sb = new StringBuilder(ctx.STRING().getText());
                sb.deleteCharAt(ctx.STRING().getText().length() - 1);
                sb.deleteCharAt(0);
                globalContext.globalStrings.put(name, sb.toString());
                return new Expression(code.render(), destReg, VarType.BYTE,
                        1);
            case cssParser.CHAR:
                template = globalContext.templateGroup.getInstanceOf("addition");
                template.add("destReg", destReg);
                template.add("type", "i8");
                template.add("value1", "0");
                int c = ctx.CHAR().getText().charAt(1);
                template.add("value2", String.valueOf(c));
                return new Expression(template.render(), destReg, VarType.BYTE, 0);
            case cssParser.INT:
                template = globalContext.templateGroup.getInstanceOf("addition");
                template.add("destReg", destReg);
                template.add("type", "i32");
                template.add("value1", "0");
                template.add("value2", String.valueOf(ctx.INT().getText()));
                return new Expression(template.render(), destReg, VarType.INT, 0);
        }
        return null;
    }

    @Override
    public Expression visitIdExpr(cssParser.IdExprContext ctx) {
        VariableExpression var = VariableExpressionVisitor.getInstance(globalContext).visit(ctx.variable());
        return new Expression(var.code(), var.returnRegister(), var.type(), var.dimensionCount());
    }

    @Override
    public Expression visitAssignExpr(cssParser.AssignExprContext ctx) {
        Expression assignValue = visit(ctx.expression());
        VariableExpression var = VariableExpressionVisitor.getInstance(globalContext).visit(ctx.variable());
        if (var.dimensionCount() != assignValue.dimensionCount())
            globalContext.handleFatalError("assign type don't match");

        if (var.type() != assignValue.type())
            globalContext.handleFatalError("types don't match");

        ST store = globalContext.templateGroup.getInstanceOf("store");
        if (var.getPtrRegister() == null) {
            globalContext.assignNewRegister(var.getVarName(), assignValue.returnRegister());
        } else {
            store.add("valueType", globalContext.llPointer(assignValue.type(), assignValue.dimensionCount()));
            store.add("value", assignValue.returnRegister());
            store.add("ptrType", globalContext.llPointer(var.type(), var.dimensionCount() + 1));
            store.add("ptr", var.getPtrRegister());
            store.add("emitCode", true);
        }

        ST template = globalContext.templateGroup.getInstanceOf("concat");
        template.add("code", assignValue.code());
        template.add("code", var.code());
        template.add("code", store.render());

        return new Expression(template.render(), assignValue.returnRegister(), assignValue.type(),
                assignValue.dimensionCount());
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
            List<Expression> parameters = FuncParamListVisitor.getInstance(globalContext).visit(ctx.funcParamList());
            List<Variable> signature = function.getArguments();
            if (parameters.size() != function.getArgumentCount())
                globalContext.handleFatalError("Function argument count does not match.");

            for (int i = 0; i < parameters.size(); ++i) {
                Variable signatureVar = signature.get(i);
                Expression parameter = parameters.get(i);
                if ((parameter.type() != signatureVar.getType()) ||
                        parameter.dimensionCount() != signatureVar.getDimensionCount())
                    globalContext.handleFatalError("Signature does not match.");
                String parameterType = globalContext.llPointer(parameter.type(), parameter.dimensionCount());
                argListTemplate.add("arg", parameterType + " " + parameter.returnRegister());
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
        if (function.getReturnType() != VarType.VOID) {
            destReg = globalContext.getNewReg();
            functionCall.add("returnValue", true);
            functionCall.add("destReg", destReg);
        }
        return new Expression(functionCall.render(), destReg, function.getReturnType(),
                0);
    }

    private Expression generateTypeCastExpr(String templateName, Expression value,
                                            VarType destinationType) {
        ST template = globalContext.templateGroup.getInstanceOf(templateName);
        String destReg = globalContext.getNewReg();
        template.add("destReg", destReg);
        template.add("value", value.returnRegister());
        template.add("srcType", globalContext.variableTypeToLLType(value.type()));
        template.add("destType", globalContext.variableTypeToLLType(destinationType));
        return new Expression(value.code() + "\n" + template.render(), destReg,
                destinationType, 0);
    }

    @Override
    public Expression visitTypeCastExpr(cssParser.TypeCastExprContext ctx) {
        Expression expression = visit(ctx.expression());
        VarType destinationType = TypeVisitor.getInstance().visit(ctx.type());
        VarType sourceType = expression.type();
        int destinationDimensionCount = ctx.LEFT_SQUARE().size();
        if (destinationDimensionCount != expression.dimensionCount())
            globalContext.handleFatalError("cannot type cast to another level");
        if (sourceType == VarType.VOID || destinationType == VarType.VOID)
            globalContext.handleFatalError("expressions cannot be type cast to void");

        if (sourceType == destinationType)
            return expression;

        /* array is type cast just as pointers are in C,
         * i.e. the underlying value is left untouched
         */
        if (destinationDimensionCount > 0)
            return new Expression(expression.code(), expression.returnRegister(),
                    destinationType, expression.dimensionCount());

        /* LLVM language does not differentiate between signed and unsigned types */
        if (
                (sourceType == VarType.BYTE && destinationType == VarType.UBYTE) ||
                (sourceType == VarType.UBYTE && destinationType == VarType.BYTE) ||
                (sourceType == VarType.INT && destinationType == VarType.UINT) ||
                (sourceType == VarType.UINT && destinationType == VarType.INT)
        )
            return new Expression(expression.code(), expression.returnRegister(),
                    destinationType, expression.dimensionCount());

        /* one extend */
        if (sourceType == VarType.BYTE)
            return generateTypeCastExpr("signExtend", expression, destinationType);

        /* zero extend */
        if (sourceType == VarType.UBYTE)
            return generateTypeCastExpr("zeroExtend", expression, destinationType);

        /* truncate */
        if (sourceType == VarType.INT || sourceType == VarType.UINT)
            return generateTypeCastExpr("truncate", expression, destinationType);

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
                ST logNot = globalContext.templateGroup.getInstanceOf("logicalNot");
                logNot.add("type", globalContext.variableTypeToLLType(expression.type()));
                logNot.add("value", expression.returnRegister());
                logNot.add("destReg", destReg);
                logNot.add("valueCode", expression.code());
                return new Expression(logNot.render(), destReg, expression.type(), 0);
            case cssParser.MINUS:
                ST minus = globalContext.templateGroup.getInstanceOf("subtract");
                minus.add("destReg", destReg);
                minus.add("type", globalContext.variableTypeToLLType(expression.type()));
                minus.add("value1", "0");
                minus.add("value2", expression.returnRegister());
                String resultCode = expression.code() + "\n" + minus.render();
                return new Expression(resultCode, destReg, expression.type(), 0);
        }
        return null;
    }

    private Expression genBinOpExpr(String templateName, String expressionCode,
                                    Expression first, Expression second) {
        String destReg = globalContext.getNewReg();
        ST template = globalContext.templateGroup.getInstanceOf(templateName);
        template.add("destReg", destReg);
        template.add("type", globalContext.variableTypeToLLType(first.type()));
        template.add("value1", first.returnRegister());
        template.add("value2", second.returnRegister());
        template.add("tmpReg", globalContext.getNewReg());
        return new Expression(expressionCode + "\n" + template.render(), destReg,
                first.type(), 0);
    }

    private Expression getLogicalBinop(Expression left, Expression right, boolean isLogicalAnd) {
        String destReg = globalContext.getNewReg();
        ST template;
        if (isLogicalAnd)
            template = globalContext.templateGroup.getInstanceOf("logicalAnd");
        else
            template = globalContext.templateGroup.getInstanceOf("logicalOr");
        template.add("destReg", destReg);
        template.add("type", globalContext.variableTypeToLLType(left.type()));
        template.add("value1", left.returnRegister());
        template.add("value2", right.returnRegister());
        template.add("tmp1", globalContext.getNewReg());
        template.add("tmp2", globalContext.getNewReg());
        template.add("tmp3", globalContext.getNewReg());
        template.add("expressionCode1", left.code());
        template.add("expressionCode2", right.code());
        return new Expression(template.render(), destReg, left.type(), 0);
    }

    @Override
    public Expression visitBinOpExpr(cssParser.BinOpExprContext ctx) {
        Expression first = visit(ctx.expression(0));
        Expression second = visit(ctx.expression(1));
        if ((first.type() != second.type()) ||
                first.dimensionCount() != second.dimensionCount() ||
                first.dimensionCount() != 0)
            globalContext.handleFatalError("type mismatch");

        boolean isSigned = first.type() == VarType.BYTE || first.type() == VarType.INT;
        String expressionCode = first.code() + "\n" + second.code();
        String templateName = "";

        switch (ctx.binOp.getType()) {
            case cssParser.MULT:
                templateName = "multiplication";
                break;
            case cssParser.PLUS:
                templateName = "addition";
                break;
            case cssParser.DIV:
                templateName = "division";
                break;
            case cssParser.MINUS:
                templateName = "subtract";
                break;
            case cssParser.MOD:
                if (isSigned)
                    templateName = "signedModulo";
                else
                    templateName = "unsignedModulo";
                break;
            case cssParser.EQ:
                templateName = "cmpEQ";
                break;
            case cssParser.NEQ:
                templateName = "cmpNE";
                break;
            case cssParser.GT:
                if (isSigned)
                    templateName = "cmpSGT";
                else
                    templateName = "cmpUGT";
                break;
            case cssParser.GTE:
                if (isSigned)
                    templateName = "cmpUGE";
                else
                    templateName = "cmpSGE";
                break;
            case cssParser.LT:
                if (isSigned)
                    templateName = "cmpSLT";
                else
                    templateName = "cmpULT";
                break;
            case cssParser.LTE:
                if (isSigned)
                    templateName = "cmpSLE";
                else
                    templateName = "cmpULE";
                break;
            case cssParser.LOGICAL_AND:
                return getLogicalBinop(first, second, true);
            case cssParser.LOGICAL_OR:
                return getLogicalBinop(first, second, false);
        }
        return genBinOpExpr(templateName, expressionCode, first, second);
    }
}
