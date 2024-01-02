package org.compiler;

import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * Contains various global variables and functions.
 * Represents the global context of the program.
 */
public class GlobalContext {
    /* hardcoded path to the template file */
    public final STGroup templateGroup = new STGroupFile("src/main/resources/templates.stg");
    /* stack of scopes */
    private final LinkedList<ScopeInfo> scopeStack = new LinkedList<>();
    /* map of declared functions */
    private final HashMap<String, Function> functions = new HashMap<>();
    /* map of declared strings (global by default) */
    public final HashMap<String, String> globalStrings = new HashMap<>();

    /*
     * These names must match those in StatementVisitor
     * in io handling visit functions. This map maps
     * the format string name to its size.
     */
    public final Map<String, Integer> formatStringSizes = Map.of(
            "@formatEndLine", 2,
            "@formatStr", 3,
            "@formatByte", 3,
            "@formatInt", 3
    );
    /* counter used to generate a unique name for global strings */
    private int globalStringCounter = 1;

    /* Inhterited attribute for declAssign non-terminal */
    private VarType currentDeclarationType;
    /* current function, useful for inherited attributes
     * for return to check type of the return expression
     */
    public Function currentFunction = null;
    /* name of current function, just for error messages */
    public String currentFunctionName = null;

    /**
     * Returns inherited attribute for declAssign
     * non-terminal.
     */
    public VarType getCurrentDeclarationType() {
        return currentDeclarationType;
    }

    /**
     * Sets inherited attribute for declAssign
     * non-terminal
     */
    public void setCurrentDeclarationType(VarType type) {
        currentDeclarationType = type;
    }

    /**
     * Check if map of declared function contains this one.
     */
    public boolean containsFunction(String functionName) {
        return functions.containsKey(functionName);
    }

    /**
     * Add function to the map of declared functions.
     * Warning: does not check if the function name
     * is already there
     */
    public void addFunctionToGlobalContext(String functionName, Function function) {
        functions.put(functionName, function);
    }

    public Function getFunction(String id) {
        return functions.get(id);
    }

    /**
     * Checks if a variable is declared in the relevant scopes.
     * Searches the scope stack for the variable.
     */
    public boolean isVariableDeclared(String id) {
        Iterator<ScopeInfo> reverse = scopeStack.descendingIterator();
        while (reverse.hasNext()) {
            if (reverse.next().containsVariable(id))
                return true;
        }
        return false;
    }

    /**
     * Searches and retrieves the variable.
     * Returns null if variable is not found.
     */
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

    /**
     * Just adds a new scope to the top of the scope stack.
     */
    public void addNewScope() {
        ScopeInfo newScopeInfo = new ScopeInfo();
        ScopeInfo oldScopeInfo = scopeStack.peek();
        if (oldScopeInfo != null) {
            newScopeInfo.currentLoopBegLabel = oldScopeInfo.currentLoopBegLabel;
            newScopeInfo.currentLoopEndLabel = oldScopeInfo.currentLoopEndLabel;
        }
        scopeStack.push(newScopeInfo);
    }

    /**
     * Just removes a scope from the top of the scope stack.
     */
    public void popScope() {
        scopeStack.pop();
    }

    /**
     * Adds a variable to the latest scope.
     * Exits and prints an error message
     * if the variable is already declared.
     */
    public void addToLastScope(String id, Variable var) {
        if (isVariableDeclared(id))
            handleFatalError("variable name collision");
        if (scopeStack.isEmpty())
            throw new RuntimeException("this shouldn't happen");
        scopeStack.peek().addVariable(id, var);
    }

    /**
     * Returns the latest scope.
     */
    public ScopeInfo getLastScope() {
        return scopeStack.peek();
    }

    /**
     * Assigns a source program variables to a new
     * LLVM register.
     */
    public void assignNewRegister(String id, String newRegister) {
        Variable var = getVariable(id);
        if (var == null)
            throw new RuntimeException("this shouldn't happen");

        var.setLlName(newRegister);
    }

    /* counter used to generate unique LLVM registers */
    private int idCounter = 1;
    /* maps source program types to their LLVM counterparts */
    private final Map<VarType, String> variableTypeToLLType = Map.of(VarType.BYTE, "i8",
                                                                        VarType.INT, "i32",
                                                                        VarType.VOID, "void"
                                                                        );

    /**
     * Returns a LLVM type to the corresponding source program type.
     */
    public String variableTypeToLLType(VarType type) {
        return variableTypeToLLType.get(type);
    }
    public void handleFileErrors() {
        handleFatalError("couldn't open the input file");
    }

    /**
     * This function handles fatal errors.
     * Prints an error message and exits.
     */
    public void handleFatalError(String message) {
        if (currentFunctionName != null)
            System.err.println("fatal error: in function " + currentFunctionName + ": " + message);
        else
            System.err.println("fatal error: " + message);
        System.exit(2);
    }

    /**
     * Generate a new unique LLVM register.
     */
    public String getNewReg() {
        return String.format("%%reg%d", idCounter++);
    }

    /**
     * Generate a unique new global string name.
     * Warning: '@' included.
     */
    public String getNewGlobalStringName() {
        return String.format("@str%d", globalStringCounter++);
    }

    /**
     * Generate a new unique LLVM label.
     * Warning: uses the same counter as getNewReg()
     */
    public String genNewLabel() {
        return String.format("label%d", idCounter++);
    }

    /**
     * Maps source program type to LLVM type.
     * In addition, this function can work with pointers.
     * @param sourceType source program type
     * @param n level of the pointer
     */
    public String llPointer(VarType sourceType, int n) {
        String llType = variableTypeToLLType(sourceType);
        StringBuilder sb = new StringBuilder(llType);
        for (int i = 0; i < n; ++i) {
            sb.append('*');
        }
        return sb.toString();
    }
}
