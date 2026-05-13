package com.ttt.safevault.autofill.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动填充解析后的数据模型
 * 封装了从AssistStructure中解析出的所有信息
 */
public class AutofillParsedData {
    private final List<AutofillField> fields;
    private final String domain;
    private final String packageName;
    private final String applicationName;
    private final String title;        // 页面或应用标题
    private final boolean isWeb;

    private AutofillParsedData(Builder builder) {
        this.fields = builder.fields;
        this.domain = builder.domain;
        this.packageName = builder.packageName;
        this.applicationName = builder.applicationName;
        this.title = builder.title;
        this.isWeb = builder.isWeb;
    }

    public List<AutofillField> getFields() {
        return fields;
    }

    public String getDomain() {
        return domain;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getTitle() {
        return title;
    }

    public boolean isWeb() {
        return isWeb;
    }

    /**
     * 获取用户名字段列表（包括用户名、邮箱、手机号、身份证）
     */
    public List<AutofillField> getUsernameFields() {
        List<AutofillField> usernameFields = new ArrayList<>();
        for (AutofillField field : fields) {
            if (field.getFieldType() == AutofillField.FieldType.USERNAME ||
                field.getFieldType() == AutofillField.FieldType.EMAIL ||
                field.getFieldType() == AutofillField.FieldType.PHONE ||
                field.getFieldType() == AutofillField.FieldType.ID_CARD) {
                usernameFields.add(field);
            }
        }
        return usernameFields;
    }

    /**
     * 获取密码字段列表
     */
    public List<AutofillField> getPasswordFields() {
        List<AutofillField> passwordFields = new ArrayList<>();
        for (AutofillField field : fields) {
            if (field.getFieldType() == AutofillField.FieldType.PASSWORD) {
                passwordFields.add(field);
            }
        }
        return passwordFields;
    }

    /**
     * Builder模式构建AutofillParsedData
     */
    public static class Builder {
        private List<AutofillField> fields = new ArrayList<>();
        private String domain;
        private String packageName;
        private String applicationName;
        private String title;
        private boolean isWeb;

        public Builder addField(AutofillField field) {
            this.fields.add(field);
            return this;
        }

        public Builder setDomain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setApplicationName(String applicationName) {
            this.applicationName = applicationName;
            return this;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setIsWeb(boolean isWeb) {
            this.isWeb = isWeb;
            return this;
        }

        public AutofillParsedData build() {
            return new AutofillParsedData(this);
        }
    }

    @Override
    public String toString() {
        return "AutofillParsedData{" +
                "fields=" + fields.size() +
                ", domain='" + domain + '\'' +
                ", packageName='" + packageName + '\'' +
                ", applicationName='" + applicationName + '\'' +
                ", title='" + title + '\'' +
                ", isWeb=" + isWeb +
                '}';
    }
}
