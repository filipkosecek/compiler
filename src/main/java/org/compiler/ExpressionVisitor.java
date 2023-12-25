package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

import java.util.ArrayList;
import java.util.List;

public class ExpressionVisitor extends cssBaseVisitor<Expression> {
    private final GlobalContext globalContext;

    public ExpressionVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    /**
     * Visit literal expressions.
     * @param ctx the parse tree
     * @return Expression structure
     */
    @Override
    public Expression visitBaseExpr(cssParser.BaseExprContext ctx) {
        switch (ctx.base.getType()) {
            case cssParser.STRING:
                String name = globalContext.getNewGlobalStringName();
                String destReg = globalContext.getNewReg();
                ST code = globalContext.templateGroup.getInstanceOf("globalStringAccess");
                code.add("dest", destReg);
                code.add("size", String.valueOf(ctx.STRING().getText().length() - 2 + 1));
                code.add("name", name);
                StringBuilder sb = new StringBuilder(ctx.STRING().getText());
                sb.deleteCharAt(ctx.STRING().getText().length() - 1);
                sb.deleteCharAt(0);
                globalContext.globalStrings.put(name, sb.toString());
                return new Expression(code.render(), destReg, "byte",
                        1, false, 0);
            case cssParser.CHAR:
                return new Expression("", "", "byte", 0,
                        true, ctx.CHAR().getText().charAt(1));
            case cssParser.INT:
                return new Expression("", "", "int", 0,
                        true, Integer.parseInt(ctx.INT().getText()));
        }
        return null;
    }

    /**
     * Return code and Expression structure which corresponds
     * to array access.
     */
    private Expression arrayAccess(Variable var, List<Expression> expressionList, int n) {
        String destReg = var.getLlName();
        ST multiLevelIndexing = globalContext.templateGroup.getInstanceOf("arrayMultiLevelIndexing");
        for (int i = 0; i < n; ++i) {
            Expression expression = expressionList.get(i);
            String ptrReg = destReg;
            destReg = globalContext.getNewReg();
            ST arrayIndex = globalContext.templateGroup.getInstanceOf("arrayIndexing");
            arrayIndex.add("tmpReg", globalContext.getNewReg());
            arrayIndex.add("destReg", destReg);
            arrayIndex.add("ptrReg", ptrReg);
            String ptrType = globalContext.pointer(globalContext.variableTypeToLLType(var.getType()),
                    var.getDimensionCount() - i);
            String tmpDestType = globalContext.pointer(globalContext.variableTypeToLLType(var.getType()),
                    var.getDimensionCount() - i - 1);
            arrayIndex.add("destType", tmpDestType);
            arrayIndex.add("ptrType", ptrType);
            arrayIndex.add("indexType", globalContext.variableTypeToLLType(expression.type()));
            if (expression.isNumericConstant())
                arrayIndex.add("indexReg", String.valueOf(expression.numericConstantValue()));
            else
                arrayIndex.add("indexReg", expression.returnRegister());
            arrayIndex.add("previousCode", expression.code());
            multiLevelIndexing.add("indexing", arrayIndex.render());
        }

        return new Expression(multiLevelIndexing.render(), destReg,
                var.getType(), var.getDimensionCount() - n,
                false, 0);
    }

    /**
     * Return an Expression structure containing code
     * which corresponds to a dereference and its value.
     */
    private Expression dereferenceLocalVar(Variable var) {
        ST template = globalContext.templateGroup.getInstanceOf("dereference");
        String destReg = globalContext.getNewReg();
        template.add("destReg", destReg);
        template.add("destType", globalContext.variableTypeToLLType(var.getType()));
        template.add("ptrReg", var.getLlName());
        template.add("ptrType",
                globalContext.pointer(globalContext.variableTypeToLLType(var.getType()), 1));
        return new Expression(template.render(), destReg, var.getType(),
                0, false, 0);
    }

    private boolean checkVariable(Variable variable, int expressionDimensions) {
        if (variable == null)
            globalContext.handleFatalError("undeclared variable");
        if (variable.getDimensionCount() < expressionDimensions)
            globalContext.handleFatalError("Too much indexing.");
        return true;
    }

    /**
     * Return value of a variable or array.
     */
    @Override
    public Expression visitIdExpr(cssParser.IdExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        checkVariable(var, ctx.expression().size());

        if (var.getDimensionCount() == 0)
            return dereferenceLocalVar(var);

        ArrayList<Expression> subexpressions = new ArrayList<>(ctx.expression().size());
        for (int i = 0; i < ctx.expression().size(); ++i) {
            Expression subexpression = visit(ctx.expression(i));
            if (subexpression.dimensionCount() != 0)
                globalContext.handleFatalError("Index to an array must not be an array.");
            subexpressions.add(subexpression);
        }

        return arrayAccess(var, subexpressions, subexpressions.size());
    }

    @Override
    public Expression visitAssignExpr(cssParser.AssignExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        checkVariable(var, ctx.expression().size());
        Expression assignValue = visit(ctx.expression().getLast());
        if (var.getDimensionCount() != assignValue.dimensionCount())
            globalContext.handleFatalError("assign type don't match");

        if (var.getDimensionCount() == 0) {
            ST store = globalContext.templateGroup.getInstanceOf("writeExpression");
            store.add("valueType", globalContext.variableTypeToLLType(var.getType()));
            if (assignValue.isNumericConstant())
                store.add("value", String.valueOf(assignValue.numericConstantValue()));
            else
                store.add("value", assignValue.returnRegister());
            store.add("ptrType",
                    globalContext.pointer(globalContext.variableTypeToLLType(var.getType()), 1));
            store.add("ptr", var.getLlName());
            store.add("expressionCode", assignValue.code());
            return new Expression(store.render(), assignValue.returnRegister(),
                    assignValue.type(), assignValue.dimensionCount(), false, 0);
        }
    }
}
