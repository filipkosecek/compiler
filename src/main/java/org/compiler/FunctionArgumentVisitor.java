package org.compiler;

import org.gen.*;

/* toto bude treba upravit ak pridas globalne premenne
 * bude treba prejst vyssie scopy
 */

public class FunctionArgumentVisitor extends cssBaseVisitor<Variable> {
    private final GlobalContext globalContext;

    public FunctionArgumentVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    private void checkVoidType(String type) {
        if (type.equals("void"))
            globalContext.handleFatalError("A function argument cannot be of void type.");
    }

    private int getDimensionCount(cssParser.FuncArgClassicContext ctx) {
        return ctx.LEFT_SQUARE().size();
    }

    /**
     * Return type of argument and put it to the last scope.
     */
    @Override
    public Variable visitFuncArgClassic(cssParser.FuncArgClassicContext ctx) {
        checkVoidType(ctx.TYPE().getText());
        Variable var = new Variable(globalContext.getNewReg(),
                ctx.TYPE().getText(), getDimensionCount(ctx), false);
        globalContext.addToLastScope(ctx.ID().getText(), var);
        return var;
    }

    /**
     * Process reference function argument.
     */
    @Override
    public Variable visitFuncArgReference(cssParser.FuncArgReferenceContext ctx) {
        checkVoidType(ctx.TYPE().getText());
        Variable var = new Variable(globalContext.getNewReg(),
                ctx.TYPE().getText(), 0, true);
        globalContext.addToLastScope(ctx.ID().getText(), var);
        return var;
    }
}
