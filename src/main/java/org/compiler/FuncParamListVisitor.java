package org.compiler;

import org.gen.*;

import java.util.ArrayList;
import java.util.List;

public class FuncParamListVisitor extends cssBaseVisitor<List<Expression>> {
    private static FuncParamListVisitor instance = null;
    public static FuncParamListVisitor getInstance(GlobalContext globalContext) {
        if (instance == null)
            instance = new FuncParamListVisitor(globalContext);
        return instance;
    }

    private final GlobalContext globalContext;

    private FuncParamListVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    @Override
    public List<Expression> visitFuncParamList(cssParser.FuncParamListContext ctx) {
        ArrayList<Expression> parameters = new ArrayList<>(ctx.expression().size());
        for (cssParser.ExpressionContext parameter : ctx.expression()) {
            parameters.add(ExpressionVisitor.getInstance(globalContext).visit(parameter));
        }
        return parameters;
    }
}
