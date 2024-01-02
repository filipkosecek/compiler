package org.compiler;

/**
 * Represents a source program variable.
 */
public class Variable {
    private String llName;
    private final VarType variableType;
    private final int dimensionCount;

    public Variable(String llName, VarType type, int dimensionCount) {
        this.llName = llName;
        this.variableType = type;
        this.dimensionCount = dimensionCount;
    }

    public String getLlName() {
        return llName;
    }

    public void setLlName(String newName) {
        llName = newName;
    }

    public VarType getType() {
        return variableType;
    }

    public int getDimensionCount() {
        return dimensionCount;
    }
}
