package io.choerodon.devops.app.service;

import io.choerodon.devops.infra.dto.DevopsProjectDTO;

/**
 * Created by Sheep on 2019/7/15.
 */
public interface DevopsProjectService {
    DevopsProjectDTO queryByAppId(Long appId);

    DevopsProjectDTO baseQueryByGitlabAppGroupId(Integer appGroupId);

    DevopsProjectDTO baseQueryByProjectId(Long projectId);

    DevopsProjectDTO baseQueryByGitlabEnvGroupId(Integer envGroupId);

    void baseUpdate(DevopsProjectDTO devopsProjectDTO);

    Long queryAppIdByProjectId(Long projectId);

    Long queryProjectIdByAppId(Long appId);
}