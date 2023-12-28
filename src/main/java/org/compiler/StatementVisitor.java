package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

import javax.swing.plaf.nimbus.State;
import java.util.ArrayList;

public class StatementVisitor extends cssBaseVisitor<Statement> {
    private static StatementVisitor instance = null;
    public static StatementVisitor getInstance(GlobalContext globalContext) {
        if (instance == null)
            instance = new StatementVisitor(globalContext);
        return instance;
    }

    private final GlobalContext globalContext;

    private StatementVisitor(GlobalContext globalContext) {
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
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        return new Statement(null, expression.code());
    }

    @Override
    public Statement visitCodeFragmentVarDecl(cssParser.CodeFragmentVarDeclContext ctx) {
        String code = MainVisitor.getInstance(globalContext).visit(ctx.varDeclBlock());
        return new Statement(null, code);
    }

    @Override
    public Statement visitCodeFragmentStatement(cssParser.CodeFragmentStatementContext ctx) {
        return visit(ctx.statement());
    }

    @Override
    public Statement visitWhile(cssParser.WhileContext ctx) {
        String firstLabel = globalContext.genNewLabel();
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
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
        String labelBody;
        if (codeBlock.firstLabel() == null) {
            labelBody = globalContext.genNewLabel();
            whileTemplate.add("addBodyLabel", true);
        } else {
            labelBody = codeBlock.firstLabel();
        }
        whileTemplate.add("labelBody", labelBody);
        return new Statement(firstLabel, whileTemplate.render());
    }

    @Override
    public Statement visitStatementReturn(cssParser.StatementReturnContext ctx) {
        Function currentFunction = globalContext.currentFunction;
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
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

    @Override
    public Statement visitIf(cssParser.IfContext ctx) {
        ST ifTemplate = globalContext.templateGroup.getInstanceOf("if");
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        if (expression.dimensionCount() != 0) {
            globalContext.handleFatalError("Only non-array expressions can be in if.");
            throw new RuntimeException("bad");
        }
        Statement codeBlock = visit(ctx.codeBlock());
        ifTemplate.add("exprCode", expression.code());
        ifTemplate.add("exprType", globalContext.variableTypeToLLType(expression.type()));
        ifTemplate.add("exprReg", expression.returnRegister());
        ifTemplate.add("tmpReg", globalContext.getNewReg());
        String bodyLabel;
        if (codeBlock.firstLabel() == null) {
            ifTemplate.add("addBodyLabel", true);
            bodyLabel = globalContext.genNewLabel();
        } else {
            bodyLabel = codeBlock.firstLabel();
        }
        ifTemplate.add("ifBodyLabel", bodyLabel);
        ifTemplate.add("ifBodyCode", codeBlock.code());

        Statement else_ = null;
        globalContext.getLastScope().ifEndLabel = globalContext.genNewLabel();
        ifTemplate.add("labelEnd", globalContext.getLastScope().ifEndLabel);
        if (ctx.else_() != null) {
            else_ = visit(ctx.else_());
            ifTemplate.add("else_", else_.code());
            globalContext.getLastScope().nextElifLabel = else_.firstLabel();
        } else {
            globalContext.getLastScope().nextElifLabel = globalContext.getLastScope().ifEndLabel;
        }

        ArrayList<Statement> elifs = new ArrayList<>(ctx.elif().size());
        for (int i = ctx.elif().size() - 1; i >= 0; --i) {
            Statement elif = visit(ctx.elif(i));
            elifs.add(elif);

            /* Inherited attribute */
            globalContext.getLastScope().nextElifLabel = elif.firstLabel();
        }
        for (int i = elifs.size() - 1; i >= 0; --i) {
            ifTemplate.add("elif", elifs.get(i).code());
        }
        ifTemplate.add("next", globalContext.getLastScope().nextElifLabel);
        return new Statement(null, ifTemplate.render());
    }

    @Override
    public Statement visitElif(cssParser.ElifContext ctx) {
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        Statement body = visit(ctx.codeBlock());
        if (expression.dimensionCount() != 0) {
            globalContext.handleFatalError("Only non-array expressions can be in if.");
            throw new RuntimeException("bad");
        }

        ST elif = globalContext.templateGroup.getInstanceOf("elif");
        elif.add("exprCode", expression.code());
        elif.add("exprType", globalContext.variableTypeToLLType(expression.type()));
        elif.add("exprReg", expression.returnRegister());
        elif.add("tmpReg", globalContext.getNewReg());
        elif.add("codeBody", body.code());
        elif.add("next", globalContext.getLastScope().nextElifLabel);
        String firstLabel = globalContext.genNewLabel();
        elif.add("firstLabel", firstLabel);
        String bodyLabel;
        if (body.firstLabel() == null) {
            elif.add("addCodeBodyLabel", true);
            bodyLabel = globalContext.genNewLabel();
        } else {
            bodyLabel = body.firstLabel();
        }
        elif.add("codeBodyLabel", bodyLabel);
        elif.add("labelEnd", globalContext.getLastScope().ifEndLabel);
        return new Statement(firstLabel, elif.render());
    }

    @Override
    public Statement visitElse(cssParser.ElseContext ctx) {
        ST elseStat = globalContext.templateGroup.getInstanceOf("else_");
        Statement codeBlock = visit(ctx.codeBlock());
        String firstLabel;
        if (codeBlock.firstLabel() == null) {
            firstLabel = globalContext.genNewLabel();
            elseStat.add("addFirstLabel", true);
        } else {
            firstLabel = codeBlock.firstLabel();
        }
        elseStat.add("firstLabel", firstLabel);
        elseStat.add("code", codeBlock.code());
        elseStat.add("labelEnd", globalContext.getLastScope().ifEndLabel);
        return new Statement(firstLabel, elseStat.render());
    }
}
