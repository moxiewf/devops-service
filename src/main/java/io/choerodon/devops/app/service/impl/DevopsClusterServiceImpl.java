package io.choerodon.devops.app.service.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.choerodon.base.domain.PageRequest;
import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.exception.CommonException;
import io.choerodon.devops.api.vo.*;
import io.choerodon.devops.app.service.ClusterNodeInfoService;
import io.choerodon.devops.app.service.DevopsClusterService;
import io.choerodon.devops.app.service.DevopsEnvPodService;
import io.choerodon.devops.api.vo.iam.entity.*;
import io.choerodon.devops.api.vo.iam.entity.iam.UserE;
import io.choerodon.devops.domain.application.repository.DevopsClusterProPermissionRepository;
import io.choerodon.devops.domain.application.repository.DevopsClusterRepository;
import io.choerodon.devops.domain.application.repository.DevopsEnvironmentRepository;
import io.choerodon.devops.infra.dto.DevopsClusterDTO;
import io.choerodon.devops.infra.handler.ClusterConnectionHandler;
import io.choerodon.devops.infra.mapper.DevopsClusterMapper;
import io.choerodon.devops.infra.util.*;
import io.kubernetes.client.JSON;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class DevopsClusterServiceImpl implements DevopsClusterService {

    @Value("${agent.version}")
    private String agentExpectVersion;

    @Value("${agent.serviceUrl}")
    private String agentServiceUrl;

    @Value("${agent.repoUrl}")
    private String agentRepoUrl;

    @Autowired
    private DevopsClusterRepository devopsClusterRepository;
    @Autowired
    private DevopsClusterProPermissionRepository devopsClusterProPermissionRepository;
    @Autowired
    private IamRepository iamRepository;
    @Autowired
    private ClusterConnectionHandler clusterConnectionHandler;
    @Autowired
    private DevopsEnvironmentRepository devopsEnvironmentRepository;
    @Autowired
    private ClusterNodeInfoService clusterNodeInfoService;
    @Autowired
    private DevopsEnvPodService devopsEnvPodService;

    private JSON json = new JSON();


    @Autowired
    private DevopsClusterMapper devopsClusterMapper;


    @Override
    @Transactional
    public String createCluster(Long organizationId, DevopsClusterReqDTO devopsClusterReqDTO) {
        // 插入记录
        DevopsClusterDTO devopsClusterDTO = new DevopsClusterDTO();
        BeanUtils.copyProperties(devopsClusterReqDTO, devopsClusterDTO);
        devopsClusterDTO.setToken(GenerateUUID.generateUUID());
        devopsClusterDTO.setOrganizationId(organizationId);
        devopsClusterDTO = baseCreateCluster(devopsClusterDTO);
        if (!devopsClusterDTO.getSkipCheckProjectPermission() && devopsClusterReqDTO.getProjects() != null) {
            for (Long projectId : devopsClusterReqDTO.getProjects()) {
                DevopsClusterProPermissionE devopsClusterProPermissionE = new DevopsClusterProPermissionE();
                devopsClusterProPermissionE.setClusterId(devopsClusterDTO.getId());
                devopsClusterProPermissionE.setProjectId(projectId);
                devopsClusterProPermissionRepository.baseInsertPermission(devopsClusterProPermissionE);
            }
        }

        UserE userE = iamRepository.queryUserByUserId(GitUserNameUtil.getUserId().longValue());

        // 渲染激活环境的命令参数
        InputStream inputStream = this.getClass().getResourceAsStream("/shell/cluster.sh");
        Map<String, String> params = new HashMap<>();
        params.put("{VERSION}", agentExpectVersion);
        params.put("{NAME}", "choerodon-cluster-agent-" + devopsClusterDTO.getCode());
        params.put("{SERVICEURL}", agentServiceUrl);
        params.put("{TOKEN}", devopsClusterDTO.getToken());
        params.put("{EMAIL}", userE == null ? "" : userE.getEmail());
        params.put("{CHOERODONID}", devopsClusterDTO.getChoerodonId());
        params.put("{REPOURL}", agentRepoUrl);
        params.put("{CLUSTERID}", devopsClusterDTO
                .getId().toString());
        return FileUtil.replaceReturnString(inputStream, params);
    }

    @Override
    @Transactional
    public void updateCluster(Long clusterId, DevopsClusterReqDTO devopsClusterReqDTO) {
        List<Long> projects = devopsClusterReqDTO.getProjects();
        Boolean skipCheckPro = devopsClusterReqDTO.getSkipCheckProjectPermission();
        List<Long> addProjects = new ArrayList<>();
        DevopsClusterE devopsClusterE = devopsClusterRepository.baseQuery(clusterId);
        //以前不跳过项目权限校验,但是现在跳过，情况集群对应的项目集群校验表
        if (skipCheckPro && !devopsClusterE.getSkipCheckProjectPermission()) {
            devopsClusterProPermissionRepository.baseDeleteByClusterId(clusterId);
        } else {
            //操作集群项目权限校验表记录
            List<Long> projectIds = devopsClusterProPermissionRepository.baseListByClusterId(clusterId)
                    .stream().map(DevopsClusterProPermissionE::getProjectId).collect(Collectors.toList());

            projects.forEach(projectId -> {
                if (!projectIds.contains(projectId)) {
                    addProjects.add(projectId);
                } else {
                    projectIds.remove(projectId);
                }
            });
            addProjects.forEach(addProject -> {
                DevopsClusterProPermissionE devopsClusterProPermissionE = new DevopsClusterProPermissionE();
                devopsClusterProPermissionE.setClusterId(clusterId);
                devopsClusterProPermissionE.setProjectId(addProject);
                devopsClusterProPermissionRepository.baseInsertPermission(devopsClusterProPermissionE);
            });
            projectIds.forEach(deleteProject -> {
                DevopsClusterProPermissionE devopsClusterProPermissionE = new DevopsClusterProPermissionE();
                devopsClusterProPermissionE.setClusterId(clusterId);
                devopsClusterProPermissionE.setProjectId(deleteProject);
                devopsClusterProPermissionRepository.baseDeletePermission(devopsClusterProPermissionE);
            });
        }
        devopsClusterE = ConvertHelper.convert(devopsClusterReqDTO, DevopsClusterE.class);
        devopsClusterE.setId(clusterId);
        devopsClusterRepository.baseUpdate(devopsClusterE);
    }

    @Override
    public void checkName(Long organizationId, String name) {
        DevopsClusterDTO devopsClusterDTO = new DevopsClusterDTO();
        devopsClusterDTO.setOrganizationId(organizationId);
        devopsClusterDTO.setName(name);
        baseCheckName(devopsClusterDTO);
    }

    @Override
    public PageInfo<ProjectReqVO> listProjects(Long organizationId, Long clusterId, PageRequest pageRequest,
                                               String[] params) {
        PageInfo<ProjectVO> projects = iamRepository
                .queryProjectByOrgId(organizationId, pageRequest.getPage(), pageRequest.getSize(), null, params);
        PageInfo<ProjectReqVO> pageProjectDTOS = new PageInfo<>();
        List<ProjectReqVO> projectDTOS = new ArrayList<>();
        if (!projects.getList().isEmpty()) {
            BeanUtils.copyProperties(projects, pageProjectDTOS);
            List<Long> projectIds;
            if (clusterId != null) {
                projectIds = devopsClusterProPermissionRepository.baseListByClusterId(clusterId).stream()
                        .map(DevopsClusterProPermissionE::getProjectId).collect(Collectors.toList());
            } else {
                projectIds = new ArrayList<>();
            }
            projects.getList().forEach(projectDO -> {
                ProjectReqVO projectDTO = new ProjectReqVO(projectDO.getId(), projectDO.getName(), projectDO.getCode(), projectIds.contains(projectDO.getId()));
                projectDTOS.add(projectDTO);
            });
        }
        BeanUtils.copyProperties(projects, pageProjectDTOS);
        pageProjectDTOS.setList(projectDTOS);
        return pageProjectDTOS;
    }

    @Override
    public String queryShell(Long clusterId) {
        DevopsClusterE devopsClusterE = getDevopsClusterEStatus(clusterId);
        InputStream inputStream;
        if (devopsClusterE.getUpgrade()) {
            inputStream = this.getClass().getResourceAsStream("/shell/cluster-upgrade.sh");
        } else {
            inputStream = this.getClass().getResourceAsStream("/shell/cluster.sh");
        }
        UserE userE = iamRepository.queryUserByUserId(devopsClusterE.getCreatedBy());
        Map<String, String> params = new HashMap<>();
        params.put("{VERSION}", agentExpectVersion);
        params.put("{NAME}", "choerodon-cluster-agent-" + devopsClusterE.getCode());
        params.put("{SERVICEURL}", agentServiceUrl);
        params.put("{TOKEN}", devopsClusterE.getToken());
        params.put("{EMAIL}", userE == null ? "" : userE.getEmail());
        params.put("{REPOURL}", agentRepoUrl);
        params.put("{CHOERODONID}", devopsClusterE.getChoerodonId());
        params.put("{CLUSTERID}", devopsClusterE
                .getId().toString());
        return FileUtil.replaceReturnString(inputStream, params);
    }

    private DevopsClusterE getDevopsClusterEStatus(Long clusterId) {
        DevopsClusterE devopsClusterE = devopsClusterRepository.baseQuery(clusterId);
        List<Long> connectedEnvList = clusterConnectionHandler.getConnectedEnvList();
        List<Long> updatedEnvList = clusterConnectionHandler.getUpdatedEnvList();
        setClusterStatus(connectedEnvList, updatedEnvList, devopsClusterE);
        return devopsClusterE;
    }

    @Override
    public void checkCode(Long organizationId, String code) {
        DevopsClusterDTO devopsClusterDTO = new DevopsClusterDTO();
        devopsClusterDTO.setOrganizationId(organizationId);
        devopsClusterDTO.setCode(code);
        baseCheckCode(devopsClusterDTO);
    }

    @Override
    public PageInfo<ClusterWithNodesDTO> pageClusters(Long organizationId, Boolean doPage, PageRequest pageRequest, String params) {
        PageInfo<DevopsClusterE> devopsClusterEPage = devopsClusterRepository
                .basePageClustersByOptions(organizationId, doPage, pageRequest, params);
        PageInfo<ClusterWithNodesDTO> devopsClusterRepDTOPage = new PageInfo<>();
        BeanUtils.copyProperties(devopsClusterEPage, devopsClusterRepDTOPage);
        List<Long> connectedEnvList = clusterConnectionHandler.getConnectedEnvList();
        List<Long> updatedEnvList = clusterConnectionHandler.getUpdatedEnvList();
        devopsClusterEPage.getList().forEach(devopsClusterE ->
                setClusterStatus(connectedEnvList, updatedEnvList, devopsClusterE));
        devopsClusterRepDTOPage.setList(fromClusterE2ClusterWithNodesDTO(devopsClusterEPage.getList(), organizationId));
        return devopsClusterRepDTOPage;
    }

    /**
     * convert cluster entity to instances of {@link ClusterWithNodesDTO}
     *
     * @param clusterEList   the cluster entities
     * @param organizationId the organization id
     * @return the instances of the return type
     */
    private List<ClusterWithNodesDTO> fromClusterE2ClusterWithNodesDTO(List<DevopsClusterE> clusterEList, Long organizationId) {
        // default three records of nodes in the instance
        PageRequest pageRequest = new PageRequest(1, 3);

        return clusterEList.stream().map(cluster -> {
            ClusterWithNodesDTO clusterWithNodesDTO = new ClusterWithNodesDTO();
            BeanUtils.copyProperties(cluster, clusterWithNodesDTO);
            if (Boolean.TRUE.equals(clusterWithNodesDTO.getConnect())) {
                clusterWithNodesDTO.setNodes(clusterNodeInfoService.pageQueryClusterNodeInfo(cluster.getId(), organizationId, pageRequest));
            }
            return clusterWithNodesDTO;
        }).collect(Collectors.toList());
    }


    @Override
    public List<ProjectReqVO> listClusterProjects(Long organizationId, Long clusterId) {
        return devopsClusterProPermissionRepository.baseListByClusterId(clusterId).stream()
                .map(devopsClusterProPermissionE -> {
                    ProjectVO projectE = iamRepository.queryIamProject(devopsClusterProPermissionE.getProjectId());
                    return new ProjectReqVO(devopsClusterProPermissionE.getProjectId(), projectE.getName(), projectE.getCode(), null);
                }).collect(Collectors.toList());
    }

    private void setClusterStatus(List<Long> connectedEnvList, List<Long> updatedEnvList, DevopsClusterE t) {
        if (connectedEnvList.contains(t.getId())) {
            if (updatedEnvList.contains(t.getId())) {
                t.initUpgrade(false);
                t.initConnect(true);
            } else {
                t.initUpgrade(true);
                t.initConnect(false);
                t.setUpgradeMessage("Version is too low, please upgrade!");
            }
        } else {
            t.initUpgrade(false);
            t.initConnect(false);
        }
    }

    @Override
    public void deleteCluster(Long clusterId) {
        devopsClusterRepository.baseDelete(clusterId);
    }

    @Override
    public Boolean IsClusterRelatedEnvs(Long clusterId) {
        List<Long> connectedEnvList = clusterConnectionHandler.getConnectedEnvList();
        List<DevopsEnvironmentE> devopsEnvironmentES = devopsEnvironmentRepository.baseListByClusterId(clusterId);
        if (connectedEnvList.contains(clusterId) || !devopsEnvironmentES.isEmpty()) {
            throw new CommonException("error.cluster.delete");
        }
        return true;
    }


    @Override
    public DevopsClusterRepDTO getCluster(Long clusterId) {
        return ConvertHelper.convert(devopsClusterRepository.baseQuery(clusterId), DevopsClusterRepDTO.class);
    }

    @Override
    public PageInfo<DevopsClusterPodVO> pageQueryPodsByNodeName(Long clusterId, String nodeName, PageRequest pageRequest, String searchParam) {
        PageInfo<DevopsEnvPodE> ePage = devopsClusterRepository.basePageQueryPodsByNodeName(clusterId, nodeName, pageRequest, searchParam);
        PageInfo<DevopsClusterPodVO> clusterPodDTOPage = new PageInfo<>();
        BeanUtils.copyProperties(ePage, clusterPodDTOPage, "content");
        clusterPodDTOPage.setList(ePage.getList().stream().map(this::podE2ClusterPodDTO).collect(Collectors.toList()));
        return clusterPodDTOPage;
    }

    @Override
    public DevopsClusterRepDTO queryByCode(Long organizationId, String code) {
        devopsClusterRepository.baseQueryByCode(organizationId, code);
        return ConvertHelper.convert(devopsClusterRepository.baseQueryByCode(organizationId, code), DevopsClusterRepDTO.class);
    }

    /**
     * pod entity to cluster pod vo
     *
     * @param pod pod entity
     * @return the cluster pod vo
     */
    private DevopsClusterPodVO podE2ClusterPodDTO(DevopsEnvPodE pod) {
        DevopsClusterPodVO devopsEnvPodDTO = new DevopsClusterPodVO();
        BeanUtils.copyProperties(pod, devopsEnvPodDTO);

        devopsEnvPodService.setContainers(pod);

        devopsEnvPodDTO.setContainersForLogs(
                pod.getContainers()
                        .stream()
                        .map(container -> new DevopsEnvPodContainerLogDTO(pod.getName(), container.getName()))
                        .collect(Collectors.toList())
        );
        return devopsEnvPodDTO;
    }



    @Override
    public DevopsClusterDTO baseCreateCluster(DevopsClusterDTO devopsClusterDTO) {
        List<DevopsClusterDTO> devopsClusterDTOS = devopsClusterMapper.selectAll();
        String choerodonId = GenerateUUID.generateUUID().split("-")[0];
        if (!devopsClusterDTOS.isEmpty()) {
            devopsClusterDTO.setChoerodonId(devopsClusterDTOS.get(0).getChoerodonId());
        } else {
            devopsClusterDTO.setChoerodonId(choerodonId);
        }
        if (devopsClusterMapper.insert(devopsClusterDTO) != 1) {
            throw new CommonException("error.devops.cluster.insert");
        }
        return devopsClusterDTO;
    }

    @Override
    public void baseCheckName(DevopsClusterDTO devopsClusterDTO) {
        if (devopsClusterMapper.selectOne(devopsClusterDTO) != null) {
            throw new CommonException("error.cluster.name.exist");
        }
    }

    @Override
    public void baseCheckCode(DevopsClusterDTO devopsClusterDTO) {
        if (devopsClusterMapper.selectOne(devopsClusterDTO) != null) {
            throw new CommonException("error.cluster.code.exist");
        }
    }

    @Override
    public List<DevopsClusterDTO> baseListByProjectId(Long projectId, Long organizationId) {
        return devopsClusterMapper.listByProjectId(projectId, organizationId);
    }

    @Override
    public DevopsClusterDTO baseQuery(Long clusterId) {
        return devopsClusterMapper.selectByPrimaryKey(clusterId);
    }

    @Override
    public void baseUpdate(DevopsClusterDTO inputClusterDTO) {
        DevopsClusterDTO devopsClusterDTO = devopsClusterMapper.selectByPrimaryKey(inputClusterDTO.getId());
        inputClusterDTO.setObjectVersionNumber(devopsClusterDTO.getObjectVersionNumber());
        devopsClusterMapper.updateByPrimaryKeySelective(inputClusterDTO);
        devopsClusterMapper.updateSkipCheckPro(inputClusterDTO.getId(), inputClusterDTO.getSkipCheckProjectPermission());
    }

    @Override
    public PageInfo<DevopsClusterDTO> basePageClustersByOptions(Long organizationId, Boolean doPage, PageRequest pageRequest, String params) {
        PageInfo<DevopsClusterDTO> devopsClusterEPage;
        if (!StringUtils.isEmpty(params)) {
            Map<String, Object> searchParamMap = json.deserialize(params, Map.class);
            devopsClusterEPage = PageHelper
                    .startPage(pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() -> devopsClusterMapper.listClusters(organizationId, TypeUtil.cast(searchParamMap.get(TypeUtil.SEARCH_PARAM)), TypeUtil.cast(searchParamMap.get(TypeUtil.PARAM))));
        } else {
            devopsClusterEPage = PageHelper.startPage(
                    pageRequest.getPage(), pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo(() -> devopsClusterMapper.listClusters(organizationId, null, null));
        }
        return devopsClusterEPage;
    }

    @Override
    public void baseDelete(Long clusterId) {
        devopsClusterMapper.deleteByPrimaryKey(clusterId);
    }

    @Override
    public DevopsClusterDTO baseQueryByToken(String token) {
        DevopsClusterDTO devopsClusterDTO = new DevopsClusterDTO();
        devopsClusterDTO.setToken(token);
        return devopsClusterMapper.selectOne(devopsClusterDTO);
    }

    @Override
    public List<DevopsClusterDTO> baseList() {
        return devopsClusterMapper.selectAll();
    }

    @Override
    public PageInfo<DevopsEnvPodE> basePageQueryPodsByNodeName(Long clusterId, String nodeName, PageRequest pageRequest, String searchParam) {
        return ConvertPageHelper.convertPageInfo(PageHelper.startPage(pageRequest.getPage(),pageRequest.getSize(), PageRequestUtil.getOrderBy(pageRequest)).doSelectPageInfo( () -> devopsClusterMapper.pageQueryPodsByNodeName(clusterId, nodeName, searchParam)), DevopsEnvPodE.class);
    }

    @Override
    public DevopsClusterDTO baseQueryByCode(Long organizationId, String code) {
        DevopsClusterDTO devopsClusterDTO = new DevopsClusterDTO();
        devopsClusterDTO.setOrganizationId(organizationId);
        devopsClusterDTO.setCode(code);
        return devopsClusterMapper.selectOne(devopsClusterDTO);
    }

    @Override
    public void baseUpdateProjectId(Long orgId, Long proId) {
        devopsClusterMapper.updateProjectId(orgId, proId);
    }
}
