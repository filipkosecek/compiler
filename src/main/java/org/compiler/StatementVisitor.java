package org.compiler;

import org.gen.*;
import org.stringtemplate.v4.ST;

import java.util.ArrayList;

/**
 * Visits statements.
 */
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

    /**
     * Visits codeBlock representing a code block. Concatenates
     * children statements. Adds a jump instruction to current code
     * fragment to point to the next one. If the label of the next
     * code fragment is null, the blocks are merged to avoid redundant
     * labels. Statement returned by this function always begins
     * a label.
     * @param ctx the parse tree
     */
    @Override
    public Statement visitCodeBlock(cssParser.CodeBlockContext ctx) {
        globalContext.addNewScope();
        ST concat = globalContext.templateGroup.getInstanceOf("concatCodeBlock");
        ArrayList<Statement> statements = new ArrayList<>(ctx.codeFragment().size());
        for (int i = 0; i < ctx.codeFragment().size(); ++i) {
            statements.add(visit(ctx.codeFragment(i)));
        }
        /* concatenate blocks */
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

        /* if the first does not have a label, generate one */
        String firstLabel;
        if (!statements.isEmpty() && statements.get(0).firstLabel() != null) {
            firstLabel = statements.get(0).firstLabel();
        } else {
            firstLabel = globalContext.genNewLabel();
            concat.add("addFirstLabel", true);
            concat.add("firstLabel", firstLabel);
        }
        globalContext.popScope();
        return new Statement(firstLabel, concat.render());
    }

    /**
     * Visits code fragment representing an expression (i.e. function call).
     */
    @Override
    public Statement visitCodeFragmentExpr(cssParser.CodeFragmentExprContext ctx) {
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        return new Statement(null, expression.code());
    }

    /**
     * Visits code fragment representing a variable declaration.
     * @param ctx the parse tree
     */
    @Override
    public Statement visitCodeFragmentVarDecl(cssParser.CodeFragmentVarDeclContext ctx) {
        String code = MainVisitor.getInstance(globalContext).visit(ctx.varDeclBlock());
        return new Statement(null, code);
    }

    /**
     * Visits code fragment representing a statement.
     * @param ctx the parse tree
     */
    @Override
    public Statement visitCodeFragmentStatement(cssParser.CodeFragmentStatementContext ctx) {
        return visit(ctx.statement());
    }

    /**
     * Visits a while loop statement. While always ends with
     * a newly generated label where a jump is performed once
     * the loop has finished. Function visitCodeBlock correctly
     * adds br instruction to jump to the next code fragment
     * if the following code fragment starts with one. If not, the execution
     * of the following code fragment begins with end label of the loop.
     * @param ctx the parse tree
     */
    @Override
    public Statement visitWhile(cssParser.WhileContext ctx) {
        String firstLabel = globalContext.genNewLabel();
        String endLabel = globalContext.genNewLabel();
        /* set inherited attributes for continue and break statements */
        globalContext.getLastScope().currentLoopBegLabel = firstLabel;
        globalContext.getLastScope().currentLoopEndLabel = endLabel;
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        if (expression.type() == VarType.VOID) {
            globalContext.handleFatalError("while statement header cannot " +
                    "contain an expression of type 'void'");
        }
        if (expression.dimensionCount() != 0) {
            globalContext.handleFatalError("only simple expression can go to while");
        }
        Statement codeBlock = visit(ctx.codeBlock());
        ST whileTemplate = globalContext.templateGroup.getInstanceOf("while");
        whileTemplate.add("tmpReg", globalContext.getNewReg());
        whileTemplate.add("expressionCode", expression.code());
        whileTemplate.add("labelBegin", firstLabel);
        whileTemplate.add("labelEnd", endLabel);
        whileTemplate.add("bodyCodeBlock", codeBlock.code());
        whileTemplate.add("expressionType", globalContext.variableTypeToLLType(expression.type()));
        whileTemplate.add("expressionReg", expression.returnRegister());
        String labelBody;
        /* jump to the loop body, i.e. label of the body code block */
        if (codeBlock.firstLabel() == null) {
            labelBody = globalContext.genNewLabel();
            whileTemplate.add("addBodyLabel", true);
        } else {
            labelBody = codeBlock.firstLabel();
        }
        whileTemplate.add("labelBody", labelBody);
        /* unset inherited attributes for continue and break statements */
        globalContext.getLastScope().currentLoopBegLabel = null;
        globalContext.getLastScope().currentLoopEndLabel = null;
        return new Statement(firstLabel, whileTemplate.render());
    }

    @Override
    public Statement visitStatementReturn(cssParser.StatementReturnContext ctx) {
        Function currentFunction = globalContext.currentFunction;
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        if (expression.type() == VarType.VOID) {
            globalContext.handleFatalError("value of type 'void' cannot be returned from a function");
        }
        if (expression.dimensionCount() != 0) {
            globalContext.handleFatalError("only primitive values can be returned" +
                    " from a function, i.e. non-array");
        }
        if (expression.type() != currentFunction.getReturnType()) {
            globalContext.handleFatalError("return value type does not match");
        }

        ST returnTemplate = globalContext.templateGroup.getInstanceOf("return");
        returnTemplate.add("type",
                globalContext.variableTypeToLLType(currentFunction.getReturnType()));
        returnTemplate.add("retReg", expression.returnRegister());
        returnTemplate.add("expressionCode", expression.code());
        return new Statement(null, returnTemplate.render());
    }

    /**
     * Visit if statement. Recursively visits else if and else.
     * If the expression in the header is false, a jump to the next else if
     * or else is performed. Else ifs and else are visited from left to right,
     * so that each block knows where to jump when the expression is false (equal to 0).
     * @param ctx the parse tree
     */
    @Override
    public Statement visitIf(cssParser.IfContext ctx) {
        ST ifTemplate = globalContext.templateGroup.getInstanceOf("if");
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        if (expression.type() == VarType.VOID) {
            globalContext.handleFatalError("if header cannot contain an " +
                    "expression of type 'void'");
        }
        if (expression.dimensionCount() != 0) {
            globalContext.handleFatalError("if header can only contain a primitive non-array expression");
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

        /* visit elifs from right to left */
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

    /**
     * Visit else if statement.
     * @param ctx the parse tree
     */
    @Override
    public Statement visitElif(cssParser.ElifContext ctx) {
        Expression expression = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        Statement body = visit(ctx.codeBlock());
        if (expression.type() == VarType.VOID) {
            globalContext.handleFatalError("else if header cannot contain " +
                    "an expression of type 'void'");
        }
        if (expression.dimensionCount() != 0) {
            globalContext.handleFatalError("if header can only contain a primitive non-array expression");
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

    /**
     * Visit else statement.
     * @param ctx the parse tree
     */
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

    @Override
    public Statement visitStatementCont(cssParser.StatementContContext ctx) {
        if (globalContext.getLastScope().currentLoopBegLabel == null) {
            globalContext.handleFatalError("continue statement must only be used inside a loop");
        }
        ST template = globalContext.templateGroup.getInstanceOf("continue");
        template.add("begLoopLabel", globalContext.getLastScope().currentLoopBegLabel);
        return new Statement(null, template.render());
    }

    @Override
    public Statement visitStatementBreak(cssParser.StatementBreakContext ctx) {
        if (globalContext.getLastScope().currentLoopEndLabel == null) {
            globalContext.handleFatalError("break statement must only be used inside a loop");
        }
        ST template = globalContext.templateGroup.getInstanceOf("break");
        template.add("endLoopLabel", globalContext.getLastScope().currentLoopEndLabel);
        return new Statement(null, template.render());
    }

    /**
     * Generates code which prints to stdout.
     * Uses printf function along with format strings
     * whose sizes are stored in a map in GlobalContext.
     * The strings are defined in a separate LLVM file
     * format.ll. Only primitive type or strings can
     * be printed.
     * @param ctx the parse tree
     */
    @Override
    public Statement visitStatementOutput(cssParser.StatementOutputContext ctx) {
        /* if the expression is empty, print a new line character */
        if (ctx.expression() == null) {
            String formatStringName = "@formatEndLine";
            ST template = globalContext.templateGroup.getInstanceOf("callPrintfEndline");
            template.add("tmpReg", globalContext.getNewReg());
            template.add("formatStringSize", String.valueOf(globalContext.formatStringSizes.get(formatStringName)));
            template.add("formatStringName", formatStringName);
            return new Statement(null, template.render());
        }

        Expression value = ExpressionVisitor.getInstance(globalContext).visit(ctx.expression());
        if (value.dimensionCount() > 0 && value.type() != VarType.BYTE) {
            globalContext.handleFatalError("only strings and primitive non-array " +
                    "expressions can be printed");
            throw new RuntimeException("this never executes, just to suppress warnings");
        }

        String formatStringName;
        ST printfTemplate = globalContext.templateGroup.getInstanceOf("callPrintf");
        printfTemplate.add("exprCode", value.code());
        printfTemplate.add("tmpReg", globalContext.getNewReg());
        switch (value.type()) {
            case BYTE:
                if (value.dimensionCount() == 0) {
                    formatStringName = "@formatByte";
                } else if (value.dimensionCount() == 1) {
                    formatStringName = "@formatStr";
                } else {
                    globalContext.handleFatalError("only strings and primitive non-array " +
                            "expressions can be printed.");
                    throw new RuntimeException("this never executes, just to suppress warnings");
                }
                break;
            case INT:
                formatStringName = "@formatInt";
                break;
            case VOID:
                globalContext.handleFatalError("values of type void cannot be printed");
                throw new RuntimeException("this never executes, just to suppress warnings");
            default:
                throw new RuntimeException("This case should never happen.");
        }
        printfTemplate.add("formatStringName", formatStringName);
        printfTemplate.add("formatStringSize",
                String.valueOf(globalContext.formatStringSizes.get(formatStringName)));
        printfTemplate.add("valueType", globalContext.llPointer(value.type(), value.dimensionCount()));
        printfTemplate.add("value", value.returnRegister());
        return new Statement(null, printfTemplate.render());
    }

    /**
     * Generates code which reads from stdin.
     * Uses scanf function along with format strings
     * whose sizes are stored in a map in GlobalContext.
     * The strings are defined in a separate LLVM file
     * format.ll. Only primitive type or strings can
     * be read.
     * @param ctx the parse tree
     */
    @Override
    public Statement visitStatementInput(cssParser.StatementInputContext ctx) {
        ST template = globalContext.templateGroup.getInstanceOf("scanf");

        VariableExpression var = VariableExpressionVisitor.getInstance(globalContext).visit(ctx.variable());
        if (var.dimensionCount() > 0 && var.type() != VarType.BYTE) {
            globalContext.handleFatalError("only strings and primitive values can be read from stdin");
            throw new RuntimeException("this never executes, just to suppress warnings");
        }

        String formatStringName;
        switch (var.type()) {
            case BYTE:
                if (var.dimensionCount() == 1) {
                    formatStringName = "@formatStr";
                } else if (var.dimensionCount() == 0) {
                    formatStringName = "@formatByte";
                } else {
                    globalContext.handleFatalError("only strings and primitive values " +
                            "can be read from stdin");
                    throw new RuntimeException("this never executes, just to suppress warnings");
                }
                break;
            case INT:
                formatStringName = "@formatInt";
                break;
            case VOID:
                globalContext.handleFatalError("cannot load a value to a void type");
                throw new RuntimeException("this never executes, just to suppress warnings");
            default:
                throw new RuntimeException("this should never happen");
        }

        template.add("exprCode", var.code());
        template.add("tmpReg", globalContext.getNewReg());
        template.add("formatStringName", formatStringName);
        template.add("formatStringSize",
                String.valueOf(globalContext.formatStringSizes.get(formatStringName)));
        if (var.getPtrRegister() == null || var.dimensionCount() > 0) {
            template.add("ptrType", globalContext.llPointer(var.type(), var.dimensionCount()));
            template.add("ptr", var.returnRegister());
        } else {
            template.add("ptrType", globalContext.llPointer(var.type(), var.dimensionCount() + 1));
            template.add("ptr", var.getPtrRegister());
        }
        return new Statement(null, template.render());
    }
}
