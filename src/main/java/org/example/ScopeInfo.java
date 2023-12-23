package org.example;

import java.util.HashMap;

public class ScopeInfo {
    private HashMap<String, Variable> variables = new HashMap<>();

    public boolean addVariable(String varName, Variable var) {
        if (variables.containsKey(varName))
            return false;
        variables.put(varName, var);
        return true;
    }

    public boolean containsVariable(String varName) {
        return variables.containsKey(varName);
    }
}
