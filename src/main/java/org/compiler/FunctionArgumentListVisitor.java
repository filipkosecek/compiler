package org.compiler;

import org.gen.*;
import java.util.ArrayList;
import java.util.List;

/* bude treba upravit ak pridam globalne premenne */
public class FunctionArgumentListVisitor extends cssBaseVisitor<Pair<List<Variable>, String>> {
    private final GlobalContext globalContext;
    private final FunctionArgumentVisitor functionArgumentVisitor;

    public FunctionArgumentListVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
        this.functionArgumentVisitor = new FunctionArgumentVisitor(globalContext);
    }

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
            if (arg.isReference())
                sb.append('*');
            sb.append(" ");
            sb.append(arg.getLlName());
        }
        return sb.toString();
    }

    /**
     * Visit all function parameters and return argument list
     * and corresponding code.
     * @param ctx the parse tree
     * @return
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
