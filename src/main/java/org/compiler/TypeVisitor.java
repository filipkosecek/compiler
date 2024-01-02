package org.compiler;

import org.gen.*;

/**
 * Visits type non-terminal and returns one of enum values.
 */
public class TypeVisitor extends cssBaseVisitor<VarType> {
    private static TypeVisitor instance = null;
    public static TypeVisitor getInstance() {
        if (instance == null)
            instance = new TypeVisitor();
        return instance;
    }

    private TypeVisitor() {

    }

    @Override
    public VarType visitType(cssParser.TypeContext ctx) {
        return switch (ctx.t.getType()) {
            case cssParser.TYPE_BYTE -> VarType.BYTE;
            case cssParser.TYPE_INT -> VarType.INT;
            case cssParser.TYPE_VOID -> VarType.VOID;
            default -> throw new RuntimeException("this should never happen");
        };
    }
}
