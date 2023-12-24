package org.compiler;

public record Expression(
        String code, String returnRegister, String type,
        int dimensionCount, boolean isNumericConstant,
        int numericConstantValue
        ) {
}
