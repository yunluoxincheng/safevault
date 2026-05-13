package com.ttt.safevault.autofill.model;

import android.view.autofill.AutofillId;

/**
 * 自动填充字段模型
 * 表示页面中可自动填充的单个字段
 */
public class AutofillField {
    private final AutofillId autofillId;
    private final String hint;
    private final String value;        // 实际输入的值
    private final int inputType;
    private final boolean isFocused;
    private final FieldType fieldType;

    /**
     * 字段类型枚举
     */
    public enum FieldType {
        USERNAME,       // 用户名字段
        PASSWORD,       // 密码字段
        EMAIL,          // 邮箱字段
        PHONE,          // 手机号字段
        ID_CARD,        // 身份证字段
        UNKNOWN         // 未知类型
    }

    public AutofillField(AutofillId autofillId, String hint, int inputType, 
                        boolean isFocused, FieldType fieldType) {
        this(autofillId, hint, null, inputType, isFocused, fieldType);
    }

    public AutofillField(AutofillId autofillId, String hint, String value, int inputType, 
                        boolean isFocused, FieldType fieldType) {
        this.autofillId = autofillId;
        this.hint = hint;
        this.value = value;
        this.inputType = inputType;
        this.isFocused = isFocused;
        this.fieldType = fieldType;
    }

    public AutofillId getAutofillId() {
        return autofillId;
    }

    public String getHint() {
        return hint;
    }

    public String getValue() {
        return value;
    }

    public int getInputType() {
        return inputType;
    }

    public boolean isFocused() {
        return isFocused;
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    @Override
    public String toString() {
        return "AutofillField{" +
                "autofillId=" + autofillId +
                ", hint='" + hint + '\'' +
                ", hasValue=" + (value != null && !value.isEmpty()) +
                ", inputType=" + inputType +
                ", isFocused=" + isFocused +
                ", fieldType=" + fieldType +
                '}';
    }
}
