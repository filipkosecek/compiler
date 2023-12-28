package org.compiler;

import org.gen.*;

/* toto bude treba upravit ak pridas globalne premenne
 * bude treba prejst vyssie scopy
 */

public class FunctionArgumentVisitor extends cssBaseVisitor<Variable> {
    private static FunctionArgumentVisitor instance = null;
    public static FunctionArgumentVisitor getInstance(GlobalContext globalContext) {
        if (instance == null)
            instance = new FunctionArgumentVisitor(globalContext);
        return instance;
    }

    private final GlobalContext globalContext;

    private FunctionArgumentVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    private void checkVoidType(VarType type) {
        if (type == VarType.VOID)
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
        VarType type = TypeVisitor.getInstance().visit(ctx.type());
        checkVoidType(type);
        Variable var = new Variable(globalContext.getNewReg(),
                type, getDimensionCount(ctx), false);
        globalContext.addToLastScope(ctx.ID().getText(), var);
        return var;
    }

    /**
     * Process reference function argument.
     */
    @Override
    public Variable visitFuncArgReference(cssParser.FuncArgReferenceContext ctx) {
        VarType type = TypeVisitor.getInstance().visit(ctx.type());
        checkVoidType(type);
        Variable var = new Variable(globalContext.getNewReg(),
                type, 0, true);
        globalContext.addToLastScope(ctx.ID().getText(), var);
        return var;
    }
}
