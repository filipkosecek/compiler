package org.compiler;

public record Expression(String code, String returnRegister, String type, boolean isConstant) {
}
