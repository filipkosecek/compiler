package org.compiler;

import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class GlobalVars {
    public static final STGroup templateGroup = new STGroupFile("/home/filipkosecek/IdeaProjects/untitled/src/main/resources/templates.stg");
    /* scope information */
    public static final LinkedList<ScopeInfo> scopeStack = new LinkedList<>();
    public static boolean scanScopes(String id) {
        Iterator<ScopeInfo> reverse = scopeStack.iterator();
        while (reverse.hasNext()) {
            if (reverse.next().containsVariable(id))
                return false;
        }
        return true;
    }
    public static final HashMap<String, Function> functions = new HashMap<>();
    public static int idCounter = 1;
    public static final Map<String, String> variableTypeToLLType = Map.of("byte", "i8",
                                                                        "ubyte", "i8",
                                                                        "int", "i32",
                                                                        "uint", "i32",
                                                                        "void", "void"
                                                                        );
    public static void handleFileErrors() {
        handleFatalError("couldn't open the input file");
    }

    public static void handleFatalError(String message) {
        System.err.println("fatal error: " + message);
        System.exit(2);
    }

    public static String getNewReg() {
        return String.format("reg%d", GlobalVars.idCounter++);
    }

    public static String genNewLabel() {
        return String.format("label%d", GlobalVars.idCounter++);
    }

    /* visitors */
    public static MainVisitor mainVisitor = new MainVisitor();
    public static FunctionArgumentVisitor functionArgumentVisitor = new FunctionArgumentVisitor();
    public static FunctionArgumentListVisitor functionArgumentListVisitor = new FunctionArgumentListVisitor();
}
