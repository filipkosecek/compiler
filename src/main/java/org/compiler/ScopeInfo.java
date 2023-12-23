package org.compiler;

import java.util.HashMap;

public class ScopeInfo {
    private final HashMap<String, Variable> variables;

    public ScopeInfo() {
        variables = new HashMap<>();
    }

    public void addVariable(String varName, Variable var) {
        variables.put(varName, var);
    }

    public boolean containsVariable(String varName) {
        return variables.containsKey(varName);
    }
}
