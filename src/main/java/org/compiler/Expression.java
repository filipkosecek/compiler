package org.compiler;

/**
 * This class is returned by ExpressionVisitor.
 */
public class Expression{
        private final String code;
        private final String returnRegister;
        private final VarType type;
        private final int dimensionCount;

        public Expression(String code, String returnRegister, VarType type, int dimensionCount)
        {
                this.code = code;
                this.returnRegister = returnRegister;
                this.type = type;
                this.dimensionCount = dimensionCount;
        }

        public String code() {
                return code;
        }

        public String returnRegister() {
                return returnRegister;
        }

        public VarType type() {
                return type;
        }

        public int dimensionCount() {
                return dimensionCount;
        }
}
