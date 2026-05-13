package com.ttt.safevault.autofill.model;

import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动填充请求模型
 * 封装了需要填充的字段和页面元数据
 */
public class AutofillRequest {
    private final List<AutofillId> usernameIds;
    private final List<AutofillId> passwordIds;
    private final String domain;
    private final String packageName;
    private final String applicationName;
    private final boolean isWeb;

    private AutofillRequest(Builder builder) {
        this.usernameIds = builder.usernameIds;
        this.passwordIds = builder.passwordIds;
        this.domain = builder.domain;
        this.packageName = builder.packageName;
        this.applicationName = builder.applicationName;
        this.isWeb = builder.isWeb;
    }

    public List<AutofillId> getUsernameIds() {
        return usernameIds;
    }

    public List<AutofillId> getPasswordIds() {
        return passwordIds;
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

    public boolean isWeb() {
        return isWeb;
    }

    /**
     * Builder模式构建AutofillRequest
     */
    public static class Builder {
        private List<AutofillId> usernameIds = new ArrayList<>();
        private List<AutofillId> passwordIds = new ArrayList<>();
        private String domain;
        private String packageName;
        private String applicationName;
        private boolean isWeb;

        public Builder addUsernameId(AutofillId id) {
            this.usernameIds.add(id);
            return this;
        }

        public Builder addPasswordId(AutofillId id) {
            this.passwordIds.add(id);
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

        public Builder setIsWeb(boolean isWeb) {
            this.isWeb = isWeb;
            return this;
        }

        public AutofillRequest build() {
            return new AutofillRequest(this);
        }
    }

    @Override
    public String toString() {
        return "AutofillRequest{" +
                "usernameIds=" + usernameIds.size() +
                ", passwordIds=" + passwordIds.size() +
                ", domain='" + domain + '\'' +
                ", packageName='" + packageName + '\'' +
                ", applicationName='" + applicationName + '\'' +
                ", isWeb=" + isWeb +
                '}';
    }
}
