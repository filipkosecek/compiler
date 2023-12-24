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
            case cssParser.ID:
                if (!globalContext.isVariableDeclared(ctx.base.getText()))
                    globalContext.handleFatalError("Variable not declared.");
                Variable var = globalContext.getVariable(ctx.base.getText());
                if (!var.isReference())
                    return new Expression("%" + var.getLlName(), var.getLlName(),
                            globalContext.variableTypeToLLType(var.getType()),
                        var.getDimensionCount(), false, 0);

                String destReg = globalContext.getNewReg();
                template = globalContext.templateGroup.getInstanceOf("dereference");
                template.add("destReg", destReg);
                template.add("type", globalContext.variableTypeToLLType(var.getType()));
                template.add("ptrReg", var.getLlName());
                return new Expression(template.render(), destReg, globalContext.variableTypeToLLType(var.getType()),
                        0, false, 0);
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
}
