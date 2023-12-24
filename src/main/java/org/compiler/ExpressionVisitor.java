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

    @Override
    public Expression visitBaseExpr(cssParser.BaseExprContext ctx) {
        ST template;
        String reg;
        switch (ctx.base.getType()) {
            case cssParser.STRING:
                //TODO
                break;
            case cssParser.CHAR:
                template = globalContext.templateGroup.getInstanceOf("assign");
                reg = globalContext.getNewReg();
                template.add("reg", reg);
                template.add("value", ctx.CHAR().getText().charAt(1));
                return new Expression(template.render(), reg, "byte", 0,
                        true, ctx.CHAR().getText().charAt(1));
            case cssParser.INT:
                template = globalContext.templateGroup.getInstanceOf("assign");
                reg = globalContext.getNewReg();
                return new Expression("%" + reg, reg, "int", 0,
                        true, Integer.parseInt(ctx.INT().getText()));
        }
        return null;
    }

    private Expression dereference(Variable var, List<Expression> expressionList, int n) {
        String destReg = var.getLlName();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; ++i) {
            Expression expression = expressionList.get(i);
            String ptrReg = destReg;
            destReg = globalContext.getNewReg();
            ST arrayIndex = globalContext.templateGroup.getInstanceOf("arrayIndexing");
            arrayIndex.add("tmpReg", globalContext.getNewReg());
            arrayIndex.add("destReg", destReg);
            arrayIndex.add("ptrReg", ptrReg);
            String ptrType = globalContext.pointer(globalContext.variableTypeToLLType(var.getType()),
                    var.getDimensionCount() - i);
            String tmpDestType = globalContext.pointer(globalContext.variableTypeToLLType(var.getType()),
                    var.getDimensionCount() - i - 1);
            arrayIndex.add("destType", tmpDestType);
            arrayIndex.add("ptrType", ptrType);
            arrayIndex.add("indexType", globalContext.variableTypeToLLType(expression.type()));
            arrayIndex.add("indexReg", expression.returnRegister());
            sb.append(arrayIndex.render());
        }

        return new Expression(sb.toString(), destReg,
                globalContext.pointer(globalContext.variableTypeToLLType(var.getType()), var.getDimensionCount() - n),
                var.getDimensionCount() - n,
                false, 0);
    }

    private Expression returnReference(Variable var) {
        ST template = globalContext.templateGroup.getInstanceOf("dereference");
        String destReg = globalContext.getNewReg();
        template.add("destReg", destReg);
        template.add("destType", globalContext.variableTypeToLLType(var.getType()));
        template.add("ptrReg", var.getLlName());
        template.add("ptrType", globalContext.variableTypeToLLType(var.getType()) + "*");
        return new Expression(template.render(), destReg, var.getType(),
                0, false, 0);
    }

    @Override
    public Expression visitIdExpr(cssParser.IdExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        if (var == null) {
            globalContext.handleFatalError("undeclared variable");
            System.exit(1);
        }

        if (var.getDimensionCount() < ctx.expression().size())
            globalContext.handleFatalError("Too much indexing.");

        if (var.isReference())
            return returnReference(var);

        ArrayList<Expression> subexpressions = new ArrayList<>(ctx.expression().size());
        for (int i = 0; i < ctx.expression().size(); ++i) {
            Expression subexpression = visit(ctx.expression(i));
            if (subexpression.dimensionCount() != 0)
                globalContext.handleFatalError("Index to an array must not be an array.");
            subexpressions.add(subexpression);
        }

        return dereference(var, subexpressions, subexpressions.size());
    }

    private Expression assignToReference(Variable var, Expression expression) {
        ST template = globalContext.templateGroup.getInstanceOf("assign");
        template.add("valueType", globalContext.variableTypeToLLType(expression.type()));
        template.add("valueReg", expression.returnRegister());
        template.add("ptrType", globalContext.pointer(globalContext.variableTypeToLLType(var.getType()), 1));
        template.add("ptrReg", var.getLlName());
        return new Expression(template.render(), expression.returnRegister(),
                expression.type(), 0, false, 0);
    }

    @Override
    public Expression visitAssignIdExpr(cssParser.AssignIdExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        if (var == null)
            globalContext.handleFatalError("undeclared variable");
        Expression value = visit(ctx.expression());
        if (var.getDimensionCount() != value.dimensionCount()) {
            globalContext.handleFatalError("cannot assign, types don't match");
        }
        if (!var.getType().equals(value.type())) {
            globalContext.handleFatalError("cannot assign, types don't match");
        }

        if (var.isReference())
            return assignToReference(var, value);

        String newReg = globalContext.getNewReg();
        ST template = globalContext.templateGroup.getInstanceOf("assign");
        template.add("reg", newReg);
        template.add("value", value.returnRegister());
        globalContext.assignNewRegister(ctx.ID().getText(), newReg);
        String returnCode = value.code() + template.render();
        return new Expression(returnCode, newReg, var.getType(),
                var.getDimensionCount(), false, 0);
    }
}
