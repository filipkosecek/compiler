package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

import java.util.ArrayList;

public class StatementVisitor extends cssBaseVisitor<Statement> {
    private final GlobalContext globalContext;

    public StatementVisitor(GlobalContext globalContext) {
        this.globalContext = globalContext;
    }

    @Override
    public Statement visitCodeBlock(cssParser.CodeBlockContext ctx) {
        globalContext.addNewScope();
        ST concat = globalContext.templateGroup.getInstanceOf("concatCodeBlock");
        ArrayList<Statement> statements = new ArrayList<>(ctx.codeFragment().size());
        for (int i = 0; i < ctx.codeFragment().size(); ++i) {
            statements.add(visit(ctx.codeFragment(i)));
        }
        for (int i = 0; i < statements.size(); ++i) {
            ST bind = globalContext.templateGroup.getInstanceOf("codeFragment");
            bind.add("code", statements.get(i).code());
            if (i == statements.size() - 1 || statements.get(i + 1).firstLabel() == null) {
                concat.add("code", bind.render());
                continue;
            }
            bind.add("label", statements.get(i + 1).firstLabel());
            bind.add("jump", true);
            concat.add("code", bind.render());
        }

        String firstLabel;
        if (!statements.isEmpty() && statements.getFirst().firstLabel() != null) {
            firstLabel = statements.getFirst().firstLabel();
        } else {
            firstLabel = globalContext.genNewLabel();
            concat.add("addFirstLabel", true);
            concat.add("firstLabel", firstLabel);
        }
        globalContext.popScope();
        return new Statement(firstLabel, concat.render());
    }

    @Override
    public Statement visitCodeFragmentExpr(cssParser.CodeFragmentExprContext ctx) {
        Expression expression = new ExpressionVisitor(globalContext).visit(ctx.expression());
        return new Statement(null, expression.code());
    }

    @Override
    public Statement visitCodeFragmentVarDecl(cssParser.CodeFragmentVarDeclContext ctx) {
        String code = new MainVisitor(globalContext).visit(ctx.varDeclBlock());
        return new Statement(null, code);
    }

    @Override
    public Statement visitCodeFragmentStatement(cssParser.CodeFragmentStatementContext ctx) {
        return visit(ctx.statement());
    }

    @Override
    public Statement visitWhile(cssParser.WhileContext ctx) {
        String firstLabel = globalContext.genNewLabel();
        Expression expression = new ExpressionVisitor(globalContext).visit(ctx.expression());
        if (expression.dimensionCount() != 0) {
            globalContext.handleFatalError("only simple expression can go to while");
            throw new RuntimeException("bad");
        }
        Statement codeBlock = visit(ctx.codeBlock());
        ST whileTemplate = globalContext.templateGroup.getInstanceOf("while");
        whileTemplate.add("tmpReg", globalContext.getNewReg());
        whileTemplate.add("expressionCode", expression.code());
        whileTemplate.add("labelBegin", firstLabel);
        whileTemplate.add("labelEnd", globalContext.genNewLabel());
        whileTemplate.add("bodyCodeBlock", codeBlock.code());
        whileTemplate.add("expressionType", globalContext.variableTypeToLLType(expression.type()));
        whileTemplate.add("expressionReg", expression.returnRegister());
        whileTemplate.add("labelBody", codeBlock.firstLabel());
        return new Statement(firstLabel, whileTemplate.render());
    }

    @Override
    public Statement visitStatementReturn(cssParser.StatementReturnContext ctx) {
        Function currentFunction = globalContext.currentFunction;
        Expression expression = new ExpressionVisitor(globalContext).visit(ctx.expression());
        if (expression.dimensionCount() != 0) {
            globalContext.handleFatalError("Only primitive values can be returned from a function.");
            throw new RuntimeException("bad");
        }
        if (expression.type() != currentFunction.getReturnType()) {
            globalContext.handleFatalError("Return value type does not match.");
            throw new RuntimeException("bad");
        }

        ST returnTemplate = globalContext.templateGroup.getInstanceOf("return");
        returnTemplate.add("type",
                globalContext.variableTypeToLLType(currentFunction.getReturnType()));
        returnTemplate.add("retReg", expression.returnRegister());
        returnTemplate.add("expressionCode", expression.code());
        return new Statement(null, returnTemplate.render());
    }
}
