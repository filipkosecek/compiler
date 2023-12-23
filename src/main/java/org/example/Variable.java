package org.example;

import java.util.ArrayList;
import java.util.List;

public class Variable {
    public String llvmName;
    public List<CodeFragmentExpression> sizes;

    public VariableType type;
    public CodeFragmentExpression initValue;
    public boolean isDynamic = false;

    public Variable(String llvmName, List<CodeFragmentExpression> sizes,
                    VariableType type, CodeFragmentExpression initValue,
                    boolean isDynamic) {
        this.llvmName = llvmName;
        if (sizes != null)
            this.sizes = new ArrayList<>(sizes);
        else
            this.sizes = null;
        this.type = type;
        this.initValue = initValue;
        this.isDynamic = isDynamic;
    }
}
