package org.compiler;

import org.gen.cssParser;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class GlobalContext {
    public final STGroup templateGroup = new STGroupFile("/home/filipkosecek/IdeaProjects/untitled/src/main/resources/templates.stg");
    /* scope information */
    private final LinkedList<ScopeInfo> scopeStack = new LinkedList<>();
    private final HashMap<String, Function> functions = new HashMap<>();

    public boolean containsFunction(String functionName) {
        return functions.containsKey(functionName);
    }

    public void addFunctionToGlobalContext(String functionName, Function function) {
        functions.put(functionName, function);
    }

    public boolean isVariableDeclared(String id) {
        Iterator<ScopeInfo> reverse = scopeStack.descendingIterator();
        while (reverse.hasNext()) {
            if (reverse.next().containsVariable(id))
                return true;
        }
        return false;
    }

    public Variable getVariable(String id) {
        Iterator<ScopeInfo> reverse = scopeStack.descendingIterator();
        while (reverse.hasNext()) {
            ScopeInfo current = reverse.next();
            if (current.containsVariable(id)) {
                return current.getVariable(id);
            }
        }
        return null;
    }

    public void addNewScope() {
        scopeStack.push(new ScopeInfo());
    }

    public void popScope() {
        scopeStack.pop();
    }

    public void addToLastScope(String id, Variable var) {
        if (isVariableDeclared(id))
            handleFatalError("variable name collision");
        scopeStack.getLast().addVariable(id, var);
    }

    private int idCounter = 1;
    private final Map<String, String> variableTypeToLLType = Map.of("byte", "i8",
                                                                        "ubyte", "i8",
                                                                        "int", "i32",
                                                                        "uint", "i32",
                                                                        "void", "void"
                                                                        );

    public String variableTypeToLLType(String type) {
        return variableTypeToLLType.get(type);
    }
    public void handleFileErrors() {
        handleFatalError("couldn't open the input file");
    }

    public void handleFatalError(String message) {
        System.err.println("fatal error: " + message);
        System.exit(2);
    }

    public String getNewReg() {
        return String.format("reg%d", idCounter++);
    }

    public String genNewLabel() {
        return String.format("label%d", idCounter++);
    }
}
