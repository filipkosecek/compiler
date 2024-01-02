package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

import java.util.ArrayList;
import java.util.List;

/**
 * Visits an expression representing a variable value.
 */
public class VariableExpressionVisitor extends cssBaseVisitor<VariableExpression> {
    private static VariableExpressionVisitor instance = null;

    public static VariableExpressionVisitor getInstance(GlobalContext globalContext) {
        if (instance == null)
            instance = new VariableExpressionVisitor(globalContext);
        return instance;
    }

    private final GlobalContext globalContext;

    private VariableExpressionVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    private void checkVariable(Variable variable, int expressionDimensions, String name) {
        if (variable == null) {
            globalContext.handleFatalError("undeclared variable '" + name + "'");
            throw new RuntimeException("this never executes, just to suppress warnings");
        }
        if (variable.getDimensionCount() < expressionDimensions)
            globalContext.handleFatalError("too many array indices for '" + name + "'");
    }

    /**
     * Returns an Expression structure containing code
     * which corresponds to dereference and its value.
     */
    private VariableExpression dereferenceLocalVar(Variable var, String varName) {
        ST template = globalContext.templateGroup.getInstanceOf("dereference");
        String destReg = globalContext.getNewReg();
        template.add("destReg", destReg);
        template.add("destType", globalContext.variableTypeToLLType(var.getType()));
        template.add("ptr", var.getLlName());
        template.add("ptrType",
                globalContext.llPointer(var.getType(), 1));
        return new VariableExpression(template.render(), destReg, var.getType(), 0,
                varName, var.getLlName());
    }

    /**
     * Returns code and Expression structure which corresponds
     * to array access.
     */
    private VariableExpression arrayAccess(Variable var, List<Expression> expressionList, String varName) {
        String destReg = var.getLlName();
        int n = expressionList.size();
        if (n == 0)
            return new VariableExpression("", destReg, var.getType(),
                    var.getDimensionCount(), varName, null);

        ST multiLevelIndexing = globalContext.templateGroup.getInstanceOf("arrayMultiLevelIndexing");
        String tmpReg = null;
        /* dereference a pointer to get a lower level pointer and ultimately the final value */
        for (int i = 0; i < n; ++i) {
            Expression expression = expressionList.get(i);
            String ptrReg = destReg;
            destReg = globalContext.getNewReg();
            ST arrayIndex = globalContext.templateGroup.getInstanceOf("arrayIndexing");
            tmpReg = globalContext.getNewReg();
            arrayIndex.add("tmpReg", tmpReg);
            arrayIndex.add("destReg", destReg);
            arrayIndex.add("ptrReg", ptrReg);
            String ptrType = globalContext.llPointer(var.getType(),
                    var.getDimensionCount() - i);
            String tmpDestType = globalContext.llPointer(var.getType(),
                    var.getDimensionCount() - i - 1);
            arrayIndex.add("destType", tmpDestType);
            arrayIndex.add("ptrType", ptrType);
            arrayIndex.add("indexType", globalContext.variableTypeToLLType(expression.type()));
            arrayIndex.add("indexReg", expression.returnRegister());
            arrayIndex.add("expressionCode", expression.code());
            multiLevelIndexing.add("indexing", arrayIndex.render());
        }

        return new VariableExpression(multiLevelIndexing.render(), destReg,
                var.getType(), var.getDimensionCount() - n, varName, tmpReg);
    }

    @Override
    public VariableExpression visitVariable(cssParser.VariableContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        checkVariable(var, ctx.expression().size(), ctx.ID().getText());

        if (var.getDimensionCount() == 0)
            return dereferenceLocalVar(var, ctx.ID().getText());

        ArrayList<Expression> subexpressions = new ArrayList<>(ctx.expression().size());
        for (int i = 0; i < ctx.expression().size(); ++i) {
            Expression subexpression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression(i));
            if (subexpression.dimensionCount() != 0)
                globalContext.handleFatalError("index to an array must not be an array");
            subexpressions.add(subexpression);
        }

        return arrayAccess(var, subexpressions, ctx.ID().getText());
    }
}
