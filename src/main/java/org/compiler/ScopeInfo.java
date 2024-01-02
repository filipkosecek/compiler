package org.compiler;

import java.util.HashMap;

/**
 * Contains information about current scope.
 */
public class ScopeInfo {
    private final HashMap<String, Variable> variables;

    /* Inherited attributes for elif statements */
    public String nextElifLabel = null;
    public String ifEndLabel = null;

    /* Inherited attribute for CONTINUE */
    public String currentLoopBegLabel = null;

    /* Inherited attribute for BREAK */
    public String currentLoopEndLabel = null;

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
