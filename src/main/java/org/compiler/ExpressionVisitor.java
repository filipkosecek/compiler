package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

import java.util.List;

/**
 * Visits expressions and returns instances of Expression.
 * Uses singleton design.
 */
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
                /* length without quatation marks and with a null terminating byte */
                code.add("size", String.valueOf(ctx.STRING().getText().length() - 2 + 1));
                code.add("name", name);
                StringBuilder sb = new StringBuilder(ctx.STRING().getText());
                sb.deleteCharAt(ctx.STRING().getText().length() - 1);
                sb.deleteCharAt(0);
                /* add the string to the map of strings,
                 * so that visitProgram function can define
                 * them in the output file
                 */
                globalContext.globalStrings.put(name, sb.toString());
                return new Expression(code.render(), destReg, VarType.BYTE,
                        1);
            case cssParser.CHAR:
                /*
                 * constant propagation is not supported, always use add
                 * to save the literal value
                 */
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

    /**
     * Evaluate expression representing a value of a variable.
     * Call function in VariableExpressionVisitor and convert
     * VariableExpression instance to Expression.
     * @param ctx the parse tree
     */
    @Override
    public Expression visitIdExpr(cssParser.IdExprContext ctx) {
        VariableExpression var = VariableExpressionVisitor.getInstance(globalContext).visit(ctx.variable());
        return new Expression(var.code(), var.returnRegister(), var.type(), var.dimensionCount());
    }

    /**
     * Assign a value to the variable. A memory address needs
     * to be written, therefore make use of VariableExpression
     * member ptrRegister. Only arrays on top level (without dereference)
     * are assigned directly to their registers. This means that
     * a = b does not change any memory pointed to by a, assuming a and b
     * are both arrays.
     * @param ctx the parse tree
     */
    @Override
    public Expression visitAssignExpr(cssParser.AssignExprContext ctx) {
        Expression assignValue = visit(ctx.expression());
        VariableExpression var = VariableExpressionVisitor.getInstance(globalContext).visit(ctx.variable());
        if (var.dimensionCount() != assignValue.dimensionCount())
            globalContext.handleFatalError("dimension counts of the value " +
                    "and the variable to be assigned don't match");

        if (var.type() != assignValue.type())
            globalContext.handleFatalError("types of the value and the variable " +
                    "to be assigned don't match");

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

    /**
     * Visit function call and check if the argument count
     * matches the signature of the called function.
     * Also perform type checking of argument expressions.
     * @param ctx the parse tree
     */
    @Override
    public Expression visitFuncCallExpr(cssParser.FuncCallExprContext ctx) {
        /* check if the function is declared */
        Function function = globalContext.getFunction(ctx.ID().getText());
        if (function == null) {
            globalContext.handleFatalError("declaration of function '" + ctx.ID().getText() +
                    "' must precede its first use");
            throw new RuntimeException("this never executes, just to suppress warnings");
        }

        String argList = "";
        StringBuilder code = new StringBuilder();
        /* argument list might be empty */
        if (ctx.funcParamList() != null) {
            ST argListTemplate = globalContext.templateGroup.getInstanceOf("argList");
            List<Expression> parameters = FuncParamListVisitor.getInstance(globalContext).visit(ctx.funcParamList());
            List<Variable> signature = function.getArguments();
            if (parameters.size() != function.getArgumentCount())
                globalContext.handleFatalError("function argument count does not match signature of function '" +
                        ctx.ID().getText() + "'");

            for (int i = 0; i < parameters.size(); ++i) {
                Variable signatureVar = signature.get(i);
                Expression parameter = parameters.get(i);
                if ((parameter.type() != signatureVar.getType()) ||
                        parameter.dimensionCount() != signatureVar.getDimensionCount())
                    globalContext.handleFatalError("argument list does not match signature of function '" +
                            ctx.ID().getText() + "'");
                String parameterType = globalContext.llPointer(parameter.type(), parameter.dimensionCount());
                /* add expression registers to the arguments */
                argListTemplate.add("arg", parameterType + " " + parameter.returnRegister());
                /* append code which evaluates argument expressions */
                code.append(parameter.code());
                code.append('\n');
            }
            argList = argListTemplate.render();
        }

        /* generate code for the function call itself */
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

    /**
     * Generic function which fills the template
     * for type cast expression, since all
     * templates have the same signature.
     */
    private Expression generateTypeCastExpr(String templateName, Expression value,
                                            VarType destinationType) {
        ST template = globalContext.templateGroup.getInstanceOf(templateName);
        String destReg = globalContext.getNewReg();
        template.add("destReg", destReg);
        template.add("value", value.returnRegister());
        template.add("srcType", globalContext.llPointer(value.type(), value.dimensionCount()));
        template.add("destType", globalContext.llPointer(destinationType, value.dimensionCount()));
        return new Expression(value.code() + "\n" + template.render(), destReg,
                destinationType, value.dimensionCount());
    }

    /**
     * Visit type cast expression.
     */
    @Override
    public Expression visitTypeCastExpr(cssParser.TypeCastExprContext ctx) {
        /* check types of both expressions, i.e. dimension count must match */
        Expression expression = visit(ctx.expression());
        VarType destinationType = TypeVisitor.getInstance().visit(ctx.type());
        VarType sourceType = expression.type();
        int destinationDimensionCount = ctx.LEFT_SQUARE().size();
        if (destinationDimensionCount != expression.dimensionCount())
            globalContext.handleFatalError("expression cannot be cast to a different dimension");
        if (sourceType == VarType.VOID || destinationType == VarType.VOID)
            globalContext.handleFatalError("expressions cannot be cast to void type");

        /* nothing to do */
        if (sourceType == destinationType)
            return expression;

        /* array is type cast just as pointers are in C,
         * i.e. the underlying value is left untouched
         */
        if (destinationDimensionCount > 0) {
            String destReg = globalContext.getNewReg();
            ST bitcast = globalContext.templateGroup.getInstanceOf("bitcast");
            bitcast.add("destReg", destReg);
            bitcast.add("srcType", globalContext.llPointer(expression.type(), expression.dimensionCount()));
            bitcast.add("destType", globalContext.llPointer(destinationType, expression.dimensionCount()));
            bitcast.add("value", expression.returnRegister());
            return generateTypeCastExpr("bitcast", expression, destinationType);
        }

        /* sign extend */
        if (sourceType == VarType.BYTE)
            return generateTypeCastExpr("signExtend", expression, destinationType);

        /* truncate */
        if (sourceType == VarType.INT)
            return generateTypeCastExpr("truncate", expression, destinationType);

        /* all cases should be covered */
        return null;
    }

    /**
     * Visit unary operation expression.
     * @param ctx the parse tree
     */
    @Override
    public Expression visitUnOpExpr(cssParser.UnOpExprContext ctx) {
        /* expression must not be of void type or have dimension count other than 0 */
        Expression expression = visit(ctx.expression());
        if (expression.type() == VarType.VOID)
            globalContext.handleFatalError("cannot apply unary operations on void type");
        if (expression.dimensionCount() != 0)
            globalContext.handleFatalError("unary operators can only be applied on non-array expressions.");

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

    /**
     * This function fills the template for binary operation template.
     * Warning: all binary operation templates must have the same
     * signature
     */
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

    /**
     * This function fills the template for logical binary
     * operation template. Template name is received as an argument.
     * Warning: all logical binary operation templates
     * must have the same signature
     */
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

    /**
     * Visit binary operation expression.
     * Warning: binary operation templates must
     * share their template signatures
     * Warning: binary logical operation templates
     * must share their template signatures.
     * @param ctx the parse tree
     */
    @Override
    public Expression visitBinOpExpr(cssParser.BinOpExprContext ctx) {
        /* recursively evaluate both expressions */
        Expression first = visit(ctx.expression(0));
        Expression second = visit(ctx.expression(1));
        /* check for void type expressions, for example void function call */
        if (first.type() == VarType.VOID || second.type() == VarType.VOID)
            globalContext.handleFatalError("binary operation operand cannot be of type void");
        /* perform type check */
        if ((first.type() != second.type()) ||
                first.dimensionCount() != second.dimensionCount() ||
                first.dimensionCount() != 0)
            globalContext.handleFatalError("type mismatch on binary operation");

        String expressionCode = first.code() + "\n" + second.code();
        String templateName = "";

        switch (ctx.binOp.getType()) {
            /* binary operation expressions */
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
                templateName = "signedModulo";
                break;
            case cssParser.EQ:
                templateName = "cmpEQ";
                break;
            case cssParser.NEQ:
                templateName = "cmpNE";
                break;
            case cssParser.GT:
                templateName = "cmpSGT";
                break;
            case cssParser.GTE:
                templateName = "cmpSGE";
                break;
            case cssParser.LT:
                templateName = "cmpSLT";
                break;
            case cssParser.LTE:
                templateName = "cmpSLE";
                break;
            /* logical binary operation expressions */
            case cssParser.LOGICAL_AND:
                return getLogicalBinop(first, second, true);
            case cssParser.LOGICAL_OR:
                return getLogicalBinop(first, second, false);
        }
        /* all cases should be covered */
        /* fill in the template */
        return genBinOpExpr(templateName, expressionCode, first, second);
    }

    /**
     * Return instance of Expression representing
     * size of an array dimension.
     * @param ctx the parse tree
     */
    @Override
    public Expression visitDeclTypeArray(cssParser.DeclTypeArrayContext ctx) {
        if (ctx.expression() == null)
            return null;
        Expression size = visit(ctx.expression());
        /* check for void type */
        if (size.type() == VarType.VOID)
            globalContext.handleFatalError("size of an array dimension cannot of type 'void'");
        if (size.dimensionCount() != 0) {
            globalContext.handleFatalError("size of an array dimension must not be an array");
        }
        return size;
    }
}
