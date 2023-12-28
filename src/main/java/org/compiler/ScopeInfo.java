package org.compiler;

import java.util.HashMap;

public class ScopeInfo {
    private final HashMap<String, Variable> variables;
    public String nextElifLabel;

    public ScopeInfo() {
        variables = new HashMap<>();
    }

    public void addVariable(String varName, Variable var) {
        variables.put(varName, var);
    }

    public boolean containsVariable(String varName) {
        return variables.containsKey(varName);
    }

    public Variable getVariable(String varName) {
        return variables.get(varName);
    }
}
