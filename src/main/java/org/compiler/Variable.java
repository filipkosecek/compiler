package org.compiler;

public class Variable {
    private String llName;
    private final String variableType;
    private final int dimensionCount;
    private final boolean isReference;

    public Variable(String llName, String type, int dimensionCount, boolean isReference) {
        this.llName = llName;
        this.variableType = type;
        this.dimensionCount = dimensionCount;
        this.isReference = isReference;
    }

    public String getLlName() {
        return llName;
    }

    public void setLlName(String newName) {
        llName = newName;
    }

    public String getType() {
        return variableType;
    }

    public int getDimensionCount() {
        return dimensionCount;
    }

    public boolean isReference() {
        return this.isReference;
    }
}
