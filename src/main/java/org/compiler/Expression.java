package org.compiler;

public class Expression{
        private final String code;
        private final String returnRegister;
        private final String type;
        private final int dimensionCount;
        private final boolean isNumericConstant;
        private final int numericConstantValue;

        public Expression(String code, String returnRegister, String type, int dimensionCount,
                          boolean isNumericConstant, int numericConstantValue)
        {
                this.code = code;
                this.returnRegister = returnRegister;
                this.type = type;
                this.dimensionCount = dimensionCount;
                this.isNumericConstant = isNumericConstant;
                this.numericConstantValue = numericConstantValue;
        }

        public String code() {
                return code;
        }

        public String returnRegister() {
                return returnRegister;
        }

        public String type() {
                return type;
        }

        public int dimensionCount() {
                return dimensionCount;
        }

        public boolean isNumericConstant() {
                return isNumericConstant;
        }

        public int numericConstantValue() {
                return numericConstantValue;
        }

        public String getValue() {
                if (isNumericConstant)
                        return String.valueOf(numericConstantValue);
                return returnRegister;
        }
}
