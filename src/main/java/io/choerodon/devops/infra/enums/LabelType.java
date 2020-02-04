package io.choerodon.devops.infra.enums;

public enum LabelType {
    GITLAB_PROJECT_OWNER("project.gitlab.owner"),
    GITLAB_PROJECT_DEVELOPER("project.gitlab.developer"),
    ORGANIZATION_GITLAB_OWNER("organization.gitlab.owner");

    private String value;

    LabelType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
