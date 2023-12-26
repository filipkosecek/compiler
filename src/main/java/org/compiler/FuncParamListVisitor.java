package org.compiler;

import org.gen.*;

import java.util.ArrayList;
import java.util.List;

public class FuncParamListVisitor extends cssBaseVisitor<List<Expression>> {
    private final GlobalContext globalContext;

    public FuncParamListVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    @Override
    public List<Expression> visitFuncParamList(cssParser.FuncParamListContext ctx) {
        ArrayList<Expression> parameters = new ArrayList<>(ctx.expression().size());
        for (cssParser.ExpressionContext parameter : ctx.expression()) {
            parameters.add(new ExpressionVisitor(globalContext).visit(parameter));
        }
        return parameters;
    }
}
