package org.compiler;

import org.gen.*;

/**
 * Visits a single function argument from
 * a function declaration signature. Returns
 * an instance of Variable up the tree
 * to the FunctionArgumentListVisitor.
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

    /**
     * This functions checks if a variable is of void type.
     * If so, program exits and prints an error message.
     */
    private void checkVoidType(VarType type) {
        if (type == VarType.VOID)
            globalContext.handleFatalError("a function argument cannot be of void type.");
    }

    private int getDimensionCount(cssParser.FuncArgContext ctx) {
        return ctx.LEFT_SQUARE().size();
    }

    /**
     * Return type of argument and put it to the last scope.
     */
    @Override
    public Variable visitFuncArg(cssParser.FuncArgContext ctx) {
        VarType type = TypeVisitor.getInstance().visit(ctx.type());
        checkVoidType(type);
        /* check if the variable is already declared in current scope */
        if (globalContext.getVariable(ctx.ID().getText()) != null)
            globalContext.handleFatalError("variable '" + ctx.ID().getText() +
                    "' already declared");
        Variable var = new Variable(globalContext.getNewReg(),
                type, getDimensionCount(ctx));
        globalContext.addToLastScope(ctx.ID().getText(), var);
        return var;
    }
}
