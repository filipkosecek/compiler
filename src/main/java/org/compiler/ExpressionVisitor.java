package org.compiler;

import org.gen.*;

public class ExpressionVisitor extends cssBaseVisitor<Expression> {
    private final GlobalContext globalContext;

    public ExpressionVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    @Override
    public Expression visitBaseExpr(cssParser.BaseExprContext ctx) {
        switch (ctx.base.getType()) {
            case cssParser.ID:
                break;
            case cssParser.STRING:
                break;
            case cssParser.CHAR:
                break;
            case cssParser.INT:
                break;
            default:
        }
        return null;
    }
}
