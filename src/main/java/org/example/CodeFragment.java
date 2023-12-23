package org.example;

public class CodeFragment {
    public CodeFragmentExpression codeFragmentExpression;
    public String code;
    public Variable variable;

    public CodeFragment() {
        codeFragmentExpression = null;
        code = null;
    }

    public CodeFragment(String code) {
        this.code = code;
    }

    public CodeFragment(Variable variable) {
        this.variable = variable;
    }

    public CodeFragment(CodeFragmentExpression codeFragmentExpression) {
        this.codeFragmentExpression = codeFragmentExpression;
    }
}
