package org.compiler;

public class VariableExpression {
    private final String code;
    private final String returnRegister;
    private final VarType type;
    private final int dimensionCount;

    /* extensions to Expression class */
    private final String varName;
    private final String ptrRegister;

    public VariableExpression(String code, String returnRegister, VarType type, int dimensionCount,
                              String varName, String ptrRegister)
    {
        this.code = code;
        this.returnRegister = returnRegister;
        this.type = type;
        this.dimensionCount = dimensionCount;
        this.varName = varName;
        this.ptrRegister = ptrRegister;
    }

    public String code() {
        return code;
    }

    public String returnRegister() {
        return returnRegister;
    }

    public VarType type() {
        return type;
    }

    public int dimensionCount() {
        return dimensionCount;
    }

    /* extensions to Expression class */
    public String getVarName() {
        return varName;
    }

    public String getPtrRegister() {
        return ptrRegister;
    }
}
