package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

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

    @Override
    public Expression visitIdExpr(cssParser.IdExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        if (var == null) {
            globalContext.handleFatalError("undeclared variable");
            System.exit(1);
        }

        if (var.getDimensionCount() < ctx.expression().size())
            globalContext.handleFatalError("Too much indexing.");

        StringBuilder sb = new StringBuilder();
        String destReg = var.getLlName();
        String destLlType = globalContext.pointer(globalContext.variableTypeToLLType(var.getType()),
                var.getDimensionCount() - ctx.expression().size());
        int dimensionCount = var.getDimensionCount();

        for (int i = 0; i < ctx.expression().size(); ++i) {
            Expression subexpression = visit(ctx.expression(i));
            if (subexpression.dimensionCount() != 0)
                globalContext.handleFatalError("Index to an array must not be an array.");

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
            sb.append(arrayIndex.render());
        }

        return new Expression(sb.toString(), destReg, destLlType,
                dimensionCount - ctx.expression().size(),
                false, 0);
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
