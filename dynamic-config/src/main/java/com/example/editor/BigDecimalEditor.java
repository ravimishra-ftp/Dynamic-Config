package com.example.editor;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;

public class BigDecimalEditor extends PropertyEditorSupport {

    @Override
    public void setAsText (String s) {
        setValue(new BigDecimal(s));
    }
}
