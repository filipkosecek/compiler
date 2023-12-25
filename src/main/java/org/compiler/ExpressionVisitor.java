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
        ST template;
        String reg;
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
    private Expression dereference(Variable var, List<Expression> expressionList, int n) {
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
     * TODO when a reference is the entire expression, no code should be returned
     */
    private Expression returnReference(Variable var) {
        ST template = globalContext.templateGroup.getInstanceOf("dereference");
        String destReg = globalContext.getNewReg();
        template.add("destReg", destReg);
        template.add("destType", globalContext.variableTypeToLLType(var.getType()));
        template.add("ptrReg", var.getLlName());
        template.add("ptrType", globalContext.variableTypeToLLType(var.getType()) + "*");
        return new Expression(template.render(), destReg, var.getType(),
                0, false, 0);
    }

    /**
     * Return value of a variable or array.
     */
    @Override
    public Expression visitIdExpr(cssParser.IdExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        if (var == null) {
            globalContext.handleFatalError("undeclared variable");
            System.exit(1);
        }

        if (var.getDimensionCount() < ctx.expression().size())
            globalContext.handleFatalError("Too much indexing.");

        if (var.isReference())
            return returnReference(var);

        ArrayList<Expression> subexpressions = new ArrayList<>(ctx.expression().size());
        for (int i = 0; i < ctx.expression().size(); ++i) {
            Expression subexpression = visit(ctx.expression(i));
            if (subexpression.dimensionCount() != 0)
                globalContext.handleFatalError("Index to an array must not be an array.");
            subexpressions.add(subexpression);
        }

        return dereference(var, subexpressions, subexpressions.size());
    }

    /**
     * Generate code which assigns Expression structure
     * to reference.
     */
    private Expression assignToReference(Variable var, Expression expression) {
        ST template = globalContext.templateGroup.getInstanceOf("store");
        template.add("valueType", globalContext.variableTypeToLLType(expression.type()));
        if (expression.isNumericConstant())
            template.add("valueReg", String.valueOf(expression.numericConstantValue()));
        else
            template.add("valueReg", expression.returnRegister());
        template.add("ptrType", globalContext.pointer(globalContext.variableTypeToLLType(var.getType()), 1));
        template.add("ptrReg", var.getLlName());
        template.add("previousCode", expression.code());
        return new Expression(template.render(), expression.returnRegister(),
                expression.type(), 0, false, 0);
    }

    /**
     * Assign value to a non-array variable.
     */
    @Override
    public Expression visitAssignIdExpr(cssParser.AssignIdExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        if (var == null)
            globalContext.handleFatalError("undeclared variable");
        Expression value = visit(ctx.expression());
        if (var.getDimensionCount() != value.dimensionCount()) {
            globalContext.handleFatalError("cannot assign, types don't match");
        }
        if (!var.getType().equals(value.type())) {
            globalContext.handleFatalError("cannot assign, types don't match");
        }

        if (var.isReference())
            return assignToReference(var, value);

        if (!value.isNumericConstant()) {
            globalContext.assignNewRegister(ctx.ID().getText(), value.returnRegister());
            return new Expression("", value.returnRegister(), var.getType(), var.getDimensionCount(),
                    false, 0);
        }

        ST template = globalContext.templateGroup.getInstanceOf("add");
        template.add("previousCode", value.code());
        template.add("type", globalContext.variableTypeToLLType(var.getType()));
        template.add("val1", String.valueOf(value.numericConstantValue()));
        template.add("val2", "0");
        String destReg = globalContext.getNewReg();
        template.add("destReg", destReg);
        globalContext.assignNewRegister(ctx.ID().getText(), destReg);
        return new Expression(template.render(), destReg, var.getType(), 0,
                false, 0);
    }

    @Override
    public Expression visitAssignArrayExpr(cssParser.AssignArrayExprContext ctx) {
        Variable var = globalContext.getVariable(ctx.ID().getText());
        if (var == null)
            globalContext.handleFatalError("undeclared variable");
        Expression value = visit(ctx.expression().getLast());
        int dimensionAfterDeref = var.getDimensionCount() - (ctx.expression().size() - 1);
        if (dimensionAfterDeref != value.dimensionCount()) {
            globalContext.handleFatalError("cannot assign, types don't match");
        }
        if (!var.getType().equals(value.type())) {
            globalContext.handleFatalError("cannot assign, types don't match");
        }

        ArrayList<Expression> expressions = new ArrayList<>(ctx.expression().size() - 1);
        for (int i = 0; i < ctx.expression().size() - 1; ++i) {
            expressions.add(visit(ctx.expression(i)));
        }
        Expression tmp = dereference(var, expressions, expressions.size() - 1);
        Expression indexExpression = expressions.getLast();
        ST arrayWrite = globalContext.templateGroup.getInstanceOf("arrayWrite");
        arrayWrite.add("tmpReg", globalContext.getNewReg());
        arrayWrite.add("valueType",
                globalContext.pointer(globalContext.variableTypeToLLType(value.type()), value.dimensionCount()));
        if (value.isNumericConstant())
            arrayWrite.add("value", String.valueOf(value.numericConstantValue()));
        else
            arrayWrite.add("value", value.returnRegister());
        arrayWrite.add("ptrType",
                globalContext.pointer(globalContext.variableTypeToLLType(tmp.type()), tmp.dimensionCount()));
        arrayWrite.add("ptr", tmp.returnRegister());
        arrayWrite.add("indexType", indexExpression.type());
        if (indexExpression.isNumericConstant())
            arrayWrite.add("index", String.valueOf(indexExpression.numericConstantValue()));
        else
            arrayWrite.add("index", indexExpression.returnRegister());
        arrayWrite.add("previousCode", value.code());
        arrayWrite.add("previousCode", tmp.code());

        return new Expression(arrayWrite.render(), null, null, 0, false, 0);
    }
}
