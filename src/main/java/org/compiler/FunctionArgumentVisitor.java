package org.compiler;

import org.gen.*;

/* toto bude treba upravit ak pridas globalne premenne
 * bude treba prejst vyssie scopy
 */

public class FunctionArgumentVisitor extends cssBaseVisitor<Variable> {
    private void addToScope(String id, Variable var) {
        if (GlobalVars.isVariableDeclared(id))
            GlobalVars.handleFatalError("variable name collision");
        GlobalVars.scopeStack.getLast().addVariable(id, var);
    }

    private void checkVoidType(String type) {
        if (type.equals("void"))
            GlobalVars.handleFatalError("A function argument cannot be of void type.");
    }

    private int getDimensionCount(cssParser.FuncArgClassicContext ctx) {
        if (ctx.LEFT_SQUARE() == null)
            return 0;
        return ctx.LEFT_SQUARE().size();
    }

    @Override
    public Variable visitFuncArgClassic(cssParser.FuncArgClassicContext ctx) {
        checkVoidType(ctx.TYPE().getText());
        Variable var = new Variable(GlobalVars.getNewReg(), ctx.TYPE().getText(), getDimensionCount(ctx), false);
        addToScope(ctx.ID().getText(), var);
        return var;
    }

    @Override
    public Variable visitFuncArgReference(cssParser.FuncArgReferenceContext ctx) {
        checkVoidType(ctx.TYPE().getText());
        Variable var = new Variable(GlobalVars.getNewReg(), ctx.TYPE().getText(), 0, true);
        addToScope(ctx.ID().getText(), var);
        return var;
    }
}
