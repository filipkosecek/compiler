package org.compiler;

import org.gen.*;

public class TypeVisitor extends cssBaseVisitor<VarType> {
    @Override
    public VarType visitType(cssParser.TypeContext ctx) {
        return switch (ctx.t.getType()) {
            case cssParser.TYPE_BYTE -> VarType.BYTE;
            case cssParser.TYPE_UBYTE -> VarType.UBYTE;
            case cssParser.TYPE_INT -> VarType.INT;
            case cssParser.TYPE_UINT -> VarType.UINT;
            case cssParser.TYPE_VOID -> VarType.VOID;
            default -> throw new RuntimeException("this should never happen");
        };
    }
}
