package org.compiler;

import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class GlobalContext {
    public final STGroup templateGroup = new STGroupFile("src/main/resources/templates.stg");
    /* scope information */
    private final LinkedList<ScopeInfo> scopeStack = new LinkedList<>();
    private final HashMap<String, Function> functions = new HashMap<>();
    public final HashMap<String, String> globalStrings = new HashMap<>();
    private int globalStringCounter = 1;

    /* This variable is for variale declaration. */
    private VarType currentDeclarationType;
    public Function currentFunction = null;

    public VarType getCurrentDeclarationType() {
        return currentDeclarationType;
    }

    public void setCurrentDeclarationType(VarType type) {
        currentDeclarationType = type;
    }

    public boolean containsFunction(String functionName) {
        return functions.containsKey(functionName);
    }

    public void addFunctionToGlobalContext(String functionName, Function function) {
        functions.put(functionName, function);
    }

    public Function getFunction(String id) {
        return functions.get(id);
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
        ScopeInfo newScopeInfo = new ScopeInfo();
        ScopeInfo oldScopeInfo = scopeStack.peek();
        if (oldScopeInfo != null) {
            newScopeInfo.currentLoopBegLabel = oldScopeInfo.currentLoopBegLabel;
            newScopeInfo.currentLoopEndLabel = oldScopeInfo.currentLoopEndLabel;
        }
        scopeStack.push(newScopeInfo);
    }

    public void popScope() {
        scopeStack.pop();
    }

    public void addToLastScope(String id, Variable var) {
        if (isVariableDeclared(id))
            handleFatalError("variable name collision");
        if (scopeStack.isEmpty())
            throw new RuntimeException("bad");
        scopeStack.peek().addVariable(id, var);
    }

    public ScopeInfo getLastScope() {
        return scopeStack.peek();
    }

    public void assignNewRegister(String id, String newRegister) {
        Variable var = getVariable(id);
        if (var == null)
            throw new RuntimeException("shit happens");

        var.setLlName(newRegister);
    }

    private int idCounter = 1;
    private final Map<VarType, String> variableTypeToLLType = Map.of(VarType.BYTE, "i8",
                                                                        VarType.UBYTE, "i8",
                                                                        VarType.INT, "i32",
                                                                        VarType.UINT, "i32",
                                                                        VarType.VOID, "void"
                                                                        );

    public String variableTypeToLLType(VarType type) {
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
        return String.format("%%reg%d", idCounter++);
    }

    public String getNewGlobalStringName() {
        return String.format("@str%d", globalStringCounter++);
    }

    public String genNewLabel() {
        return String.format("label%d", idCounter++);
    }

    public String llPointer(VarType sourceType, int n) {
        String llType = variableTypeToLLType(sourceType);
        StringBuilder sb = new StringBuilder(llType);
        for (int i = 0; i < n; ++i) {
            sb.append('*');
        }
        return sb.toString();
    }
}
