package org.compiler;

import org.gen.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Visits parameters (expressions) which are passed
 * as parameters to a function call. Uses singleton design pattern.
 * This class returns list of expressions, therefore it must be separate
 * from ExpressionVisitor.
 */
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

    /**
     * Recursively visits function parameter expressions
     * and return list of them.
     * @param ctx the parse tree
     */
    @Override
    public List<Expression> visitFuncParamList(cssParser.FuncParamListContext ctx) {
        ArrayList<Expression> parameters = new ArrayList<>(ctx.expression().size());
        for (cssParser.ExpressionContext parameter : ctx.expression()) {
            parameters.add(ExpressionVisitor.getInstance(globalContext).visit(parameter));
        }
        return parameters;
    }
}
