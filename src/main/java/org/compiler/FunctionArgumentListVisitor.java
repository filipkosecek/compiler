package org.compiler;

import org.gen.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Visits argList non-terminal. Must be a separate visitor,
 * since it returns a list of Variable instances
 * and code which is later inserted to the function header,
 * i.e. i32 %arg1, i32 %arg2, ...
 */
public class FunctionArgumentListVisitor extends cssBaseVisitor<Pair<List<Variable>, String>> {
    private static FunctionArgumentListVisitor instance = null;
    public static FunctionArgumentListVisitor getInstance(GlobalContext globalContext) {
        if (instance == null)
            instance = new FunctionArgumentListVisitor(globalContext);
        return instance;
    }

    private final GlobalContext globalContext;
    private final FunctionArgumentVisitor functionArgumentVisitor;

    private FunctionArgumentListVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
        this.functionArgumentVisitor = FunctionArgumentVisitor.getInstance(globalContext);
    }

    /**
     * A little ugly, but this function simply concatenates
     * variables to a list separated by commas.
     */
    private String generateCode(ArrayList<Variable> argList) {
        // argList size is at least one (grammar)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argList.size(); ++i) {
            if (i != 0) {
                sb.append(" ,");
            }
            Variable arg = argList.get(i);
            sb.append(globalContext.variableTypeToLLType(arg.getType()));
            for (int j = 0; j < arg.getDimensionCount(); ++j) {
                sb.append('*');
            }
            sb.append(" ");
            sb.append(arg.getLlName());
        }
        return sb.toString();
    }

    /**
     * Visit all function parameters and return argument list
     * and corresponding code.
     * @param ctx the parse tree
     */
    @Override
    public Pair<List<Variable>, String> visitArgList(cssParser.ArgListContext ctx) {
        ArrayList<Variable> argList = new ArrayList<>(ctx.funcArg().size());
        for (int i = 0; i < ctx.funcArg().size(); ++i) {
            argList.add(functionArgumentVisitor.visit(ctx.funcArg(i)));
        }
        return new Pair<>(argList, generateCode(argList));
    }
}
