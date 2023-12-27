package org.compiler;

public class Expression{
        private final String code;
        private final String returnRegister;
        private final String type;
        private final int dimensionCount;

        public Expression(String code, String returnRegister, String type, int dimensionCount)
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

        public String type() {
                return type;
        }

        public int dimensionCount() {
                return dimensionCount;
        }
}
