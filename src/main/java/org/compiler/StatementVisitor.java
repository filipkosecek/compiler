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
}
