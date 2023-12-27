package org.compiler;

import java.util.ArrayList;
import java.util.List;

public class Function {
    private final VarType returnType;
    private final ArrayList<Variable> argList;
    private final int argc;
    public Function(VarType returnType, List<Variable> argList) {
        this.returnType = returnType;
        this.argList = new ArrayList<>(argList);
        argc = this.argList.size();
    }

    public int getArgumentCount() {
        return argc;
    }

    public List<Variable> getArguments() {
        return argList;
    }

    public VarType getReturnType() {
        return returnType;
    }
}
