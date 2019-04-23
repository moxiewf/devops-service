package io.choerodon.devops.app.service.impl;

import com.google.gson.Gson;
import io.choerodon.asgard.saga.annotation.Saga;
import io.choerodon.asgard.saga.dto.StartInstanceDTO;
import io.choerodon.asgard.saga.feign.SagaClient;
import io.choerodon.core.convertor.ConvertHelper;
import io.choerodon.core.convertor.ConvertPageHelper;
import io.choerodon.core.domain.Page;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.iam.ResourceLevel;
import io.choerodon.core.oauth.DetailsHelper;
import io.choerodon.devops.api.dto.ApplicationDeployDTO;
import io.choerodon.devops.api.dto.PipelineAppDeployDTO;
import io.choerodon.devops.api.dto.PipelineDTO;
import io.choerodon.devops.api.dto.PipelineRecordDTO;
import io.choerodon.devops.api.dto.PipelineRecordListDTO;
import io.choerodon.devops.api.dto.PipelineRecordReqDTO;
import io.choerodon.devops.api.dto.PipelineReqDTO;
import io.choerodon.devops.api.dto.PipelineStageDTO;
import io.choerodon.devops.api.dto.PipelineStageRecordDTO;
import io.choerodon.devops.api.dto.PipelineTaskDTO;
import io.choerodon.devops.api.dto.PipelineTaskRecordDTO;
import io.choerodon.devops.api.dto.PipelineUserRecordRelDTO;
import io.choerodon.devops.api.dto.iam.UserDTO;
import io.choerodon.devops.app.service.ApplicationVersionService;
import io.choerodon.devops.app.service.PipelineService;
import io.choerodon.devops.domain.application.entity.ApplicationVersionE;
import io.choerodon.devops.domain.application.entity.PipelineAppDeployE;
import io.choerodon.devops.domain.application.entity.PipelineE;
import io.choerodon.devops.domain.application.entity.PipelineRecordE;
import io.choerodon.devops.domain.application.entity.PipelineStageE;
import io.choerodon.devops.domain.application.entity.PipelineStageRecordE;
import io.choerodon.devops.domain.application.entity.PipelineTaskE;
import io.choerodon.devops.domain.application.entity.PipelineTaskRecordE;
import io.choerodon.devops.domain.application.entity.PipelineUserRecordRelE;
import io.choerodon.devops.domain.application.entity.PipelineUserRelE;
import io.choerodon.devops.domain.application.entity.PipelineValueE;
import io.choerodon.devops.domain.application.entity.iam.UserE;
import io.choerodon.devops.domain.application.repository.ApplicationVersionRepository;
import io.choerodon.devops.domain.application.repository.IamRepository;
import io.choerodon.devops.domain.application.repository.PipelineAppDeployRepository;
import io.choerodon.devops.domain.application.repository.PipelineRecordRepository;
import io.choerodon.devops.domain.application.repository.PipelineRepository;
import io.choerodon.devops.domain.application.repository.PipelineStageRecordRepository;
import io.choerodon.devops.domain.application.repository.PipelineStageRepository;
import io.choerodon.devops.domain.application.repository.PipelineTaskRecordRepository;
import io.choerodon.devops.domain.application.repository.PipelineTaskRepository;
import io.choerodon.devops.domain.application.repository.PipelineUserRelRecordRepository;
import io.choerodon.devops.domain.application.repository.PipelineUserRelRepository;
import io.choerodon.devops.domain.application.repository.PipelineValueRepository;
import io.choerodon.devops.domain.application.repository.WorkFlowRepository;
import io.choerodon.devops.infra.common.util.enums.CommandType;
import io.choerodon.devops.infra.common.util.enums.WorkFlowStatus;
import io.choerodon.devops.infra.dataobject.workflow.DevopsPipelineDTO;
import io.choerodon.devops.infra.dataobject.workflow.DevopsPipelineStageDTO;
import io.choerodon.devops.infra.dataobject.workflow.DevopsPipelineTaskDTO;
import io.choerodon.mybatis.pagehelper.domain.PageRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Creator: ChangpingShi0213@gmail.com
 * Date:  19:57 2019/4/3
 * Description:
 */
@Service
public class PipelineServiceImpl implements PipelineService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineServiceImpl.class);
    private static final String MANUAL = "manual";
    private static final String AUTO = "auto";
    private static final String STAGE = "stage";
    private static final String TASK = "task";

    private static final Gson gson = new Gson();
    @Autowired
    private PipelineRepository pipelineRepository;
    @Autowired
    private PipelineUserRelRepository pipelineUserRelRepository;
    @Autowired
    private PipelineUserRelRecordRepository pipelineUserRelRecordRepository;
    @Autowired
    private PipelineRecordRepository pipelineRecordRepository;
    @Autowired
    private PipelineStageRecordRepository stageRecordRepository;
    @Autowired
    private PipelineStageRepository stageRepository;
    @Autowired
    private IamRepository iamRepository;
    @Autowired
    private PipelineTaskRepository pipelineTaskRepository;
    @Autowired
    private PipelineAppDeployRepository appDeployRepository;
    @Autowired
    private PipelineValueRepository valueRepository;
    @Autowired
    private PipelineTaskRecordRepository taskRecordRepository;
    @Autowired
    private WorkFlowRepository workFlowRepository;
    @Autowired
    private ApplicationVersionRepository versionRepository;
    @Autowired
    private SagaClient sagaClient;

    @Autowired
    private ApplicationVersionService versionService;

    @Override
    public Page<PipelineDTO> listByOptions(Long projectId, PageRequest pageRequest, String params) {
        Page<PipelineDTO> pipelineDTOS = ConvertPageHelper.convertPage(pipelineRepository.listByOptions(projectId, pageRequest, params), PipelineDTO.class);
        Page<PipelineDTO> page = new Page<>();
        BeanUtils.copyProperties(pipelineDTOS, page);
        page.setContent(pipelineDTOS.getContent().stream().peek(t -> {
            UserE userE = iamRepository.queryUserByUserId(t.getCreatedBy());
            if (userE == null) {
                throw new CommonException("error.get.create.user");
            }
            t.setCreateUserName(userE.getLoginName());
            t.setCreateUserUrl(userE.getImageUrl());
            t.setCreateUserRealName(userE.getRealName());
            t.setExecute(pipelineUserRelRepository.listByOptions(t.getId(), null, null)
                    .stream()
                    .map(PipelineUserRelE::getUserId)
                    .collect(Collectors.toList())
                    .contains(DetailsHelper.getUserDetails().getUserId()));
        }).collect(Collectors.toList()));
        return page;
    }

    @Override
    public Page<PipelineRecordDTO> listRecords(Long projectId, Long pipelineId, PageRequest pageRequest, String params) {
        Page<PipelineRecordDTO> pageRecordDTOS = ConvertPageHelper.convertPage(
                pipelineRecordRepository.listByOptions(projectId, pipelineId, pageRequest, params), PipelineRecordDTO.class);
        List<PipelineRecordDTO> pipelineRecordDTOS = pageRecordDTOS.getContent().stream().map(t -> {
            t.setIndex(false);
            t.setStageDTOList(ConvertHelper.convertList(stageRecordRepository.list(projectId, t.getId()), PipelineStageRecordDTO.class));
            if (t.getStatus().equals(WorkFlowStatus.PENDINGCHECK.toValue())) {
                for (int i = 0; i < t.getStageDTOList().size(); i++) {
                    if (t.getStageDTOList().get(i).getStatus().equals(WorkFlowStatus.PENDINGCHECK.toValue())) {
                        List<PipelineTaskRecordE> list = taskRecordRepository.queryByStageRecordId(t.getStageDTOList().get(i).getId(), null);
                        if (list != null && list.size() > 0) {
                            Optional<PipelineTaskRecordE> taskRecordE = list.stream().filter(task -> task.getStatus().equals(WorkFlowStatus.PENDINGCHECK.toValue())).findFirst();
                            t.setStageName(t.getStageDTOList().get(i).getStageName());
                            t.setTaskRecordId(taskRecordE.get().getId());
                            t.setStageRecordId(t.getStageDTOList().get(i).getId());
                            t.setType(TASK);
                            t.setIndex(checkTaskTriggerPermission(taskRecordE.get().getTaskId(), taskRecordE.get().getId()));
                            break;
                        }
                    } else if (t.getStageDTOList().get(i).getStatus().equals(WorkFlowStatus.UNEXECUTED.toValue())) {
                        t.setType(STAGE);
                        t.setStageName(t.getStageDTOList().get(i - 1).getStageName());
                        t.setStageRecordId(t.getStageDTOList().get(i).getId());
                        if (checkTriggerPermission(null, null, t.getStageDTOList().get(i).getId())) {
                            t.setIndex(true);
                        }
                        break;
                    }
                }
            } else if (t.getStatus().equals(WorkFlowStatus.STOP.toValue())) {
                t.setType(STAGE);
                for (int i = 0; i < t.getStageDTOList().size(); i++) {
                    if (t.getStageDTOList().get(i).getStatus().equals(WorkFlowStatus.STOP.toValue())) {
                        List<PipelineTaskRecordE> recordEList = taskRecordRepository.queryByStageRecordId(t.getStageDTOList().get(i).getId(), null);
                        Optional<PipelineTaskRecordE> optional = recordEList.stream().filter(recordE -> recordE.getStatus().equals(WorkFlowStatus.STOP.toValue())).findFirst();
                        if (optional.isPresent() && optional.get() != null) {
                            t.setType(TASK);
                        }
                        t.setStageRecordId(t.getStageDTOList().get(i).getId());
                        break;
                    }
                }
                t.setIndex(checkTriggerPermission(null, pipelineId, null));
            }
            return t;
        }).collect(Collectors.toList());
        pageRecordDTOS.setContent(pipelineRecordDTOS);
        return pageRecordDTOS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PipelineReqDTO create(Long projectId, PipelineReqDTO pipelineReqDTO) {
        //pipeline
        PipelineE pipelineE = ConvertHelper.convert(pipelineReqDTO, PipelineE.class);
        pipelineE.setProjectId(projectId);
        checkName(projectId, pipelineReqDTO.getName());
        pipelineE = pipelineRepository.create(projectId, pipelineE);
        createUserRel(pipelineReqDTO.getPipelineUserRelDTOS(), pipelineE.getId(), null, null);

        //stage
        Long pipelineId = pipelineE.getId();
        List<PipelineStageE> pipelineStageES = ConvertHelper.convertList(pipelineReqDTO.getPipelineStageDTOS(), PipelineStageE.class)
                .stream().map(t -> {
                    t.setPipelineId(pipelineId);
                    t.setProjectId(projectId);
                    return stageRepository.create(t);
                }).collect(Collectors.toList());
        for (int i = 0; i < pipelineStageES.size(); i++) {
            Long stageId = pipelineStageES.get(i).getId();
            createUserRel(pipelineReqDTO.getPipelineStageDTOS().get(i).getStageUserRelDTOS(), null, stageId, null);
            //task
            pipelineReqDTO.getPipelineStageDTOS().get(i).getPipelineTaskDTOS().forEach(t -> {
                AddPipelineTask(t, projectId, stageId);
            });
        }
        return pipelineReqDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PipelineReqDTO update(Long projectId, PipelineReqDTO pipelineReqDTO) {
        pipelineReqDTO.setProjectId(projectId);
        //pipeline
        PipelineE pipelineE = ConvertHelper.convert(pipelineReqDTO, PipelineE.class);
        pipelineE = pipelineRepository.update(projectId, pipelineE);
        updateUserRel(pipelineReqDTO.getPipelineUserRelDTOS(), pipelineE.getId(), null, null);

        Long pipelineId = pipelineE.getId();
        //删除stage
        List<Long> newStageIds = ConvertHelper.convertList(pipelineReqDTO.getPipelineStageDTOS(), PipelineStageE.class)
                .stream().filter(t -> t.getId() != null)
                .map(PipelineStageE::getId).collect(Collectors.toList());
        stageRepository.queryByPipelineId(pipelineId).forEach(t -> {
            if (!newStageIds.contains(t.getId())) {
                stageRepository.delete(t.getId());
                updateUserRel(null, null, t.getId(), null);
                pipelineTaskRepository.queryByStageId(t.getId()).forEach(taskE -> {
                    taskRecordRepository.delete(taskE.getId());
                    updateUserRel(null, null, null, taskE.getId());
                });
            }
        });

        for (int i = 0; i < pipelineReqDTO.getPipelineStageDTOS().size(); i++) {
            //新增和修改stage
            PipelineStageE stageE = ConvertHelper.convert(pipelineReqDTO.getPipelineStageDTOS().get(i), PipelineStageE.class);
            if (stageE.getId() != null) {
                stageRepository.update(stageE);
            } else {
                stageE.setPipelineId(pipelineId);
                stageE = stageRepository.create(stageE);
                createUserRel(pipelineReqDTO.getPipelineStageDTOS().get(i).getStageUserRelDTOS(), null, stageE.getId(), null);
            }

            Long stageId = stageE.getId();
            updateUserRel(pipelineReqDTO.getPipelineStageDTOS().get(i).getStageUserRelDTOS(), null, stageId, null);
            //task删除
            List<Long> newTaskIds = pipelineReqDTO.getPipelineStageDTOS().get(i).getPipelineTaskDTOS()
                    .stream()
                    .filter(t -> t.getId() != null)
                    .map(PipelineTaskDTO::getId)
                    .collect(Collectors.toList());
            pipelineTaskRepository.queryByStageId(stageId).forEach(t -> {
                if (!newTaskIds.contains(t.getId())) {
                    pipelineTaskRepository.deleteById(t.getId());
                    if (t.getType().equals(MANUAL)) {
                        updateUserRel(null, null, null, t.getId());
                    }
                }
            });
            //task
            pipelineReqDTO.getPipelineStageDTOS().get(i).getPipelineTaskDTOS().stream().filter(Objects::nonNull).forEach(t -> {
                if (t.getId() != null) {
                    if (AUTO.equals(t.getType())) {
                        t.setAppDeployId(appDeployRepository.update(ConvertHelper.convert(t.getAppDeployDTOS(), PipelineAppDeployE.class)).getId());
                        PipelineValueE pipelineValueE = new PipelineValueE();
                        pipelineValueE.setId(t.getAppDeployDTOS().getValueId());
                        pipelineValueE.setValue(t.getAppDeployDTOS().getValue());
                        valueRepository.createOrUpdate(pipelineValueE);
                    }
                    Long taskId = pipelineTaskRepository.update(ConvertHelper.convert(t, PipelineTaskE.class)).getId();
                    if (MANUAL.equals(t.getType())) {
                        updateUserRel(t.getTaskUserRelDTOS(), null, null, taskId);
                    }
                } else {
                    AddPipelineTask(t, projectId, stageId);
                }
            });
        }
        return pipelineReqDTO;
    }

    @Override
    public PipelineDTO updateIsEnabled(Long projectId, Long pipelineId, Integer isEnabled) {
        return ConvertHelper.convert(pipelineRepository.updateIsEnabled(pipelineId, isEnabled), PipelineDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long projectId, Long pipelineId) {
        //回写记录状态
        pipelineRecordRepository.queryByPipelineId(pipelineId).forEach(t -> {
            PipelineRecordE recordE = new PipelineRecordE();
            recordE.setId(t.getId());
            recordE.setPipelineId(pipelineId);
            recordE.setStatus(WorkFlowStatus.DELETED.toValue());
            pipelineRecordRepository.update(recordE);
        });
        pipelineUserRelRepository.listByOptions(pipelineId, null, null).forEach(t -> pipelineUserRelRepository.delete(t));
        //删除stage和task
        stageRepository.queryByPipelineId(pipelineId).forEach(stage -> {
            pipelineTaskRepository.queryByStageId(stage.getId()).forEach(task -> {
                if (task.getAppDeployId() != null) {
                    appDeployRepository.deleteById(task.getAppDeployId());
                }
                pipelineTaskRepository.deleteById(task.getId());
                pipelineUserRelRepository.listByOptions(null, null, task.getId()).forEach(t -> pipelineUserRelRepository.delete(t));
            });
            stageRepository.delete(stage.getId());
            pipelineUserRelRepository.listByOptions(null, stage.getId(), null).forEach(t -> pipelineUserRelRepository.delete(t));
        });
        //删除pipeline
        pipelineRepository.delete(pipelineId);
    }

    @Override
    public PipelineReqDTO queryById(Long projectId, Long pipelineId) {
        PipelineReqDTO pipelineReqDTO = ConvertHelper.convert(pipelineRepository.queryById(pipelineId), PipelineReqDTO.class);
        pipelineReqDTO.setPipelineUserRelDTOS(pipelineUserRelRepository.listByOptions(pipelineId, null, null).stream().map(PipelineUserRelE::getUserId).collect(Collectors.toList()));
        List<PipelineStageDTO> pipelineStageES = ConvertHelper.convertList(stageRepository.queryByPipelineId(pipelineId), PipelineStageDTO.class);
        pipelineStageES = pipelineStageES.stream()
                .peek(stage -> {
                    List<PipelineTaskDTO> pipelineTaskDTOS = ConvertHelper.convertList(pipelineTaskRepository.queryByStageId(stage.getId()), PipelineTaskDTO.class);
                    pipelineTaskDTOS = pipelineTaskDTOS.stream().peek(task -> {
                        if (task.getAppDeployId() != null) {
                            task.setAppDeployDTOS(ConvertHelper.convert(appDeployRepository.queryById(task.getAppDeployId()), PipelineAppDeployDTO.class));
                        } else {
                            task.setTaskUserRelDTOS(pipelineUserRelRepository.listByOptions(null, null, task.getId()).stream().map(PipelineUserRelE::getUserId).collect(Collectors.toList()));
                        }
                    }).collect(Collectors.toList());
                    stage.setPipelineTaskDTOS(pipelineTaskDTOS);
                    stage.setStageUserRelDTOS(pipelineUserRelRepository.listByOptions(null, stage.getId(), null).stream().map(PipelineUserRelE::getUserId).collect(Collectors.toList()));
                }).collect(Collectors.toList());
        pipelineReqDTO.setPipelineStageDTOS(pipelineStageES);
        return pipelineReqDTO;
    }

    @Override
    public void execute(Long projectId, Long pipelineId) {
        //校验当前触发人员是否有权限触发
        PipelineE pipelineE = pipelineRepository.queryById(pipelineId);
        if (!checkTriggerPermission(pipelineE, pipelineId, null)) {
            throw new CommonException("error.permission.trigger.pipeline");
        }
        //保存pipeline 和 pipelineUserRel
        PipelineRecordE pipelineRecordE = pipelineRecordRepository.create(new PipelineRecordE(pipelineId, pipelineE.getTriggerType(), projectId, WorkFlowStatus.RUNNING.toValue()));
        PipelineUserRecordRelE pipelineUserRecordRelE = new PipelineUserRecordRelE();
        pipelineUserRecordRelE.setPipelineRecordId(pipelineRecordE.getId());
        pipelineUserRecordRelE.setUserId(DetailsHelper.getUserDetails().getUserId());
        pipelineUserRelRecordRepository.create(pipelineUserRecordRelE);

        //准备workFlow数据
        DevopsPipelineDTO devopsPipelineDTO = setWorkFlowDTO(pipelineRecordE.getId(), pipelineId);
        pipelineRecordE.setBpmDefinition(gson.toJson(devopsPipelineDTO));
        pipelineRecordRepository.update(pipelineRecordE);

        //发送请求给workflow，创建流程实例
        try {
            pipelineRecordE.setProcessInstanceId(workFlowRepository.create(projectId, devopsPipelineDTO));
            pipelineRecordE.setStatus(WorkFlowStatus.PENDINGCHECK.toValue());
            updateFirstStage(pipelineRecordE.getId(), pipelineId);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            pipelineRecordE.setStatus(WorkFlowStatus.FAILED.toValue());
        } finally {
            pipelineRecordRepository.update(pipelineRecordE);
        }

    }

    @Override
    @Saga(code = "devops-pipeline-auto-deploy-instance",
            description = "创建流水线自动部署实例", inputSchema = "{}")
    public void autoDeploy(Long stageRecordId, Long taskId) {
        //获取数据
        PipelineTaskE pipelineTaskE = pipelineTaskRepository.queryById(taskId);
        PipelineAppDeployE appDeployE = appDeployRepository.queryById(pipelineTaskE.getAppDeployId());
        List<ApplicationVersionE> versionES = versionRepository.listByAppId(appDeployE.getApplicationId(), null)
                .stream().filter(t -> t.getCreationDate().getTime() > appDeployE.getCreationDate().getTime()).collect(Collectors.toList());
        Integer index = -1;
        for (int i = 0; i < versionES.size(); i++) {
            ApplicationVersionE versionE = versionES.get(i);
            if (appDeployE.getTriggerVersion() == null || appDeployE.getTriggerVersion().isEmpty()) {
                index = i;
                break;
            } else {
                List<String> list = Arrays.asList(appDeployE.getTriggerVersion().split(","));
                Optional<String> branch = list.stream().filter(t -> versionE.getVersion().contains(t)).findFirst();
                if (branch.isPresent() && !branch.get().isEmpty()) {
                    index = i;
                    break;
                }
            }
        }
        if (index == -1) {
            throw new CommonException("no.version.can.trigger.deploy");
        }
        //保存记录
        PipelineTaskRecordE pipelineTaskRecordE = new PipelineTaskRecordE(stageRecordId, pipelineTaskE.getType(),
                appDeployE.getTriggerVersion(), appDeployE.getApplicationId(),
                appDeployE.getEnvId(), appDeployE.getInstanceId(),
                valueRepository.queryById(appDeployE.getValueId()).getValue());
        pipelineTaskRecordE.setTaskId(taskId);
        pipelineTaskRecordE.setProjectId(pipelineTaskE.getProjectId());
        pipelineTaskRecordE.setStatus(WorkFlowStatus.RUNNING.toValue());
        pipelineTaskRecordE.setName(pipelineTaskE.getName());
        pipelineTaskRecordE = taskRecordRepository.createOrUpdate(pipelineTaskRecordE);
        try {
            String type = appDeployE.getInstanceId() == null ? CommandType.CREATE.getType() : CommandType.UPDATE.getType();
            ApplicationDeployDTO applicationDeployDTO = new ApplicationDeployDTO(versionES.get(index).getId(), appDeployE.getEnvId(),
                    valueRepository.queryById(appDeployE.getValueId()).getValue(), appDeployE.getApplicationId(), type, appDeployE.getInstanceId(),
                    appDeployE.getInstanceName(), pipelineTaskRecordE.getId(), appDeployE.getId());
            String input = gson.toJson(applicationDeployDTO);
            sagaClient.startSaga("devops-pipeline-auto-deploy-instance", new StartInstanceDTO(input, "env", appDeployE.getEnvId().toString(), ResourceLevel.PROJECT.value(), appDeployE.getProjectId()));
        } catch (Exception e) {
            pipelineTaskRecordE.setStatus(WorkFlowStatus.FAILED.toValue());
            taskRecordRepository.createOrUpdate(pipelineTaskRecordE);
            throw new CommonException("error.create.pipeline.auto.deploy.instance", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long projectId, PipelineUserRecordRelDTO recordRelDTO) {
        String status;
        if (recordRelDTO.getIsApprove()) {
            Boolean result = workFlowRepository.approveUserTask(projectId, pipelineRecordRepository.queryById(recordRelDTO.getPipelineRecordId()).getProcessInstanceId(), recordRelDTO.getIsApprove());
            status = result ? WorkFlowStatus.SUCCESS.toValue() : WorkFlowStatus.FAILED.toValue();
            if (STAGE.equals(recordRelDTO.getType())) {
                status = result ? WorkFlowStatus.RUNNING.toValue() : WorkFlowStatus.FAILED.toValue();
            }
        } else {
            status = WorkFlowStatus.STOP.toValue();
        }
        PipelineUserRecordRelE userRelE = new PipelineUserRecordRelE();
        userRelE.setUserId(DetailsHelper.getUserDetails().getUserId());
        switch (recordRelDTO.getType()) {
            case TASK: {
                userRelE.setTaskRecordId(recordRelDTO.getTaskRecordId());
                pipelineUserRelRecordRepository.create(userRelE);
                PipelineTaskRecordE taskRecordE = taskRecordRepository.queryById(recordRelDTO.getTaskRecordId());
                PipelineTaskE taskE = pipelineTaskRepository.queryById(taskRecordE.getTaskId());
                //判断是否成功
                if (status.equals(WorkFlowStatus.SUCCESS.toValue())) {
                    //判断是否是会签
                    if (taskE.getIsCountersigned() == 1) {
                        List<PipelineUserRelE> userList = pipelineUserRelRepository.listByOptions(null, null, taskE.getId());
                        List<PipelineUserRecordRelE> userRecordList = pipelineUserRelRecordRepository.queryByRecordId(null, null, taskE.getId());
                        //是否全部同意
                        if (userList.size() != userRecordList.size()) {
                            break;
                        }
                    }
                    updateStatus(recordRelDTO.getPipelineRecordId(), null, WorkFlowStatus.RUNNING.toValue());
                    startNextTaskRecord(taskRecordE.getId(), recordRelDTO.getPipelineRecordId(), recordRelDTO.getStageRecordId());
                } else {
                    updateStatus(recordRelDTO.getPipelineRecordId(), recordRelDTO.getStageRecordId(), status);
                }
                taskRecordE.setStatus(status);
                taskRecordRepository.createOrUpdate(taskRecordE);
                break;
            }
            case STAGE: {
                userRelE.setStageRecordId(recordRelDTO.getStageRecordId());
                pipelineUserRelRecordRepository.create(userRelE);
                updateStatus(recordRelDTO.getPipelineRecordId(), recordRelDTO.getStageRecordId(), status);
                if (status.equals(WorkFlowStatus.RUNNING.toValue())) {
                    //阶段中的第一个任务为人工任务时
                    PipelineTaskE pipelineTaskE = pipelineTaskRepository.queryByStageId(stageRecordRepository.queryById(recordRelDTO.getStageRecordId()).getStageId()).get(0);
                    if (MANUAL.equals(pipelineTaskE.getType())) {
                        PipelineTaskRecordE taskRecordE = new PipelineTaskRecordE();
                        BeanUtils.copyProperties(pipelineTaskE, taskRecordE);
                        taskRecordE.setId(null);
                        taskRecordE.setStageRecordId(recordRelDTO.getStageRecordId());
                        taskRecordE.setStatus(WorkFlowStatus.PENDINGCHECK.toValue());
                        taskRecordE.setTaskId(pipelineTaskE.getId());
                        taskRecordE.setTaskType(pipelineTaskE.getType());
                        taskRecordRepository.createOrUpdate(taskRecordE);
                        status = WorkFlowStatus.PENDINGCHECK.toValue();
                        updateStatus(recordRelDTO.getPipelineRecordId(), recordRelDTO.getStageRecordId(), status);
                    }
                }
                break;
            }
            default: {
                break;
            }
        }
    }

    /**
     * 检测是否满足部署条件
     *
     * @param pipelineId
     * @return
     */
    @Override
    public Boolean checkDeploy(Long pipelineId) {
        //判断pipeline是否被禁用
        if (pipelineRepository.queryById(pipelineId).getIsEnabled() == 0) {
            return false;
        }
        List<PipelineAppDeployE> appDeployEList = new ArrayList<>();
        //获取所有appDeploy
        stageRepository.queryByPipelineId(pipelineId).forEach(stageE -> {
            pipelineTaskRepository.queryByStageId(stageE.getId()).forEach(taskE -> {
                if (taskE.getAppDeployId() != null) {
                    PipelineAppDeployE appDeployE = appDeployRepository.queryById(taskE.getAppDeployId());
                    appDeployEList.add(appDeployE);
                }
            });
        });
        //如果全部为人工任务
        if (appDeployEList.isEmpty()) {
            return true;
        }
        //检测是否满足条件
        for (PipelineAppDeployE appDeployE : appDeployEList) {
            if (appDeployE.getCreationDate().getTime() > versionRepository.getLatestVersion(appDeployE.getApplicationId()).getCreationDate().getTime()) {
                return false;
            } else {
                if (appDeployE.getTriggerVersion() == null || appDeployE.getTriggerVersion().isEmpty()) {
                    return true;
                } else {
                    List<String> list = Arrays.asList(appDeployE.getTriggerVersion().split(","));
                    //是否有对应版本
                    List<ApplicationVersionE> versionES = versionRepository.listByAppId(appDeployE.getApplicationId(), null)
                            .stream()
                            .filter(versionE -> versionE.getCreationDate().getTime() > appDeployE.getCreationDate().getTime())
                            .collect(Collectors.toList());

                    for (ApplicationVersionE versionE : versionES) {
                        Optional<String> branch = list.stream().filter(t -> versionE.getVersion().contains(t)).findFirst();
                        if (branch.isPresent() && !branch.get().isEmpty()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private PipelineTaskE getFirsetTask(Long pipelineId) {
        return pipelineTaskRepository.queryByStageId(stageRepository.queryByPipelineId(pipelineId).get(0).getId()).get(0);
    }

    /**
     * 准备workflow创建实例所需数据
     * 为此workflow下所有stage创建记录
     */
    @Override
    public DevopsPipelineDTO setWorkFlowDTO(Long pipelineRecordId, Long pipelineId) {
        //workflow数据
        DevopsPipelineDTO devopsPipelineDTO = new DevopsPipelineDTO();
        devopsPipelineDTO.setPipelineRecordId(pipelineRecordId);
        List<DevopsPipelineStageDTO> devopsPipelineStageDTOS = new ArrayList<>();
        //stage
        List<PipelineStageE> stageES = stageRepository.queryByPipelineId(pipelineId);
        for (int i = 0; i < stageES.size(); i++) {
            PipelineStageE stageE = stageES.get(i);
            //创建所有stageRecord
            PipelineStageRecordE recordE = new PipelineStageRecordE();
            BeanUtils.copyProperties(stageE, recordE);
            recordE.setStatus(WorkFlowStatus.UNEXECUTED.toValue());
            recordE.setStageId(stageE.getId());
            recordE.setPipelineRecordId(pipelineRecordId);
            recordE.setId(null);
            recordE = stageRecordRepository.createOrUpdate(recordE);

            //stage
            DevopsPipelineStageDTO devopsPipelineStageDTO = new DevopsPipelineStageDTO();
            devopsPipelineStageDTO.setStageRecordId(recordE.getId());
            devopsPipelineStageDTO.setParallel(stageE.getIsParallel() != null && stageE.getIsParallel() == 1);
            if (i == stageES.size() - 1) {
                devopsPipelineStageDTO.setNextStageTriggerType(stageE.getTriggerType());
            }
            List<PipelineUserRelE> relEList = pipelineUserRelRepository.listByOptions(null, stageE.getId(), null);
            devopsPipelineStageDTO.setMultiAssign(relEList.size() > 1);
            devopsPipelineStageDTO.setUsernames(relEList.stream()
                    .map(relE -> iamRepository.queryUserByUserId(relE.getUserId()).getLoginName())
                    .collect(Collectors.toList()));

            List<DevopsPipelineTaskDTO> devopsPipelineTaskDTOS = new ArrayList<>();
            pipelineTaskRepository.queryByStageId(stageE.getId()).forEach(task -> {
                //task
                List<PipelineUserRelE> taskUserRels = pipelineUserRelRepository.listByOptions(null, null, task.getId());
                DevopsPipelineTaskDTO devopsPipelineTaskDTO = new DevopsPipelineTaskDTO();
                devopsPipelineTaskDTO.setTaskName(task.getName());
                devopsPipelineTaskDTO.setTaskType(task.getType());
                devopsPipelineTaskDTO.setMultiAssign(taskUserRels.size() > 1);
                devopsPipelineTaskDTO.setUsernames(taskUserRels.stream().map(relE -> iamRepository.queryUserByUserId(relE.getUserId()).getLoginName()).collect(Collectors.toList()));
                devopsPipelineTaskDTO.setTaskId(task.getId());
                if (task.getIsCountersigned() != null) {
                    devopsPipelineTaskDTO.setIsSign(task.getIsCountersigned().longValue());
                }
                devopsPipelineTaskDTOS.add(devopsPipelineTaskDTO);
            });
            devopsPipelineStageDTO.setTasks(devopsPipelineTaskDTOS);
            devopsPipelineStageDTOS.add(devopsPipelineStageDTO);
        }
        stageRepository.queryByPipelineId(pipelineId).forEach(t -> {


        });
        devopsPipelineDTO.setStages(devopsPipelineStageDTOS);
        return devopsPipelineDTO;
    }

    @Override
    public String getAppDeployStatus(Long stageRecordId, Long taskId) {
        List<PipelineTaskRecordE> list = taskRecordRepository.queryByStageRecordId(stageRecordId, taskId);
        return list.get(0).getStatus();
    }

    @Override
    public void setAppDeployStatus(Long pipelineRecordId, Long stageRecordId, Long taskId, Boolean status) {
        PipelineRecordE pipelineRecordE = pipelineRecordRepository.queryById(pipelineRecordId);
        PipelineStageRecordE stageRecordE = stageRecordRepository.queryById(stageRecordId);
        PipelineStageE stageE = stageRepository.queryById(stageRecordE.getStageId());
        if (status) {
            if (stageE.getIsParallel() == 1) {
                List<PipelineTaskE> taskList = pipelineTaskRepository.queryByStageId(stageE.getId());
                List<PipelineTaskRecordE> taskRecordEList = taskRecordRepository.queryByStageRecordId(stageRecordE.getId(), null)
                        .stream().filter(t -> t.getStatus().equals(WorkFlowStatus.SUCCESS.toValue())).collect(Collectors.toList());
                if (taskList.size() == taskRecordEList.size()) {
                    startNextTaskRecord(taskRecordRepository.queryByStageRecordId(stageRecordId, taskId).get(0).getId(), pipelineRecordId, stageRecordId);
                }
            } else {
                startNextTaskRecord(taskRecordRepository.queryByStageRecordId(stageRecordId, taskId).get(0).getId(), pipelineRecordId, stageRecordId);
            }
        } else {
            //停止实例
            workFlowRepository.stopInstance(pipelineRecordE.getProjectId(), pipelineRecordE.getProcessInstanceId());
        }
    }

    @Override
    public PipelineRecordReqDTO getRecordById(Long projectId, Long pipelineRecordId) {
        PipelineRecordReqDTO recordReqDTO = new PipelineRecordReqDTO();
        BeanUtils.copyProperties(pipelineRecordRepository.queryById(pipelineRecordId), recordReqDTO);
        //获取pipeline触发人员
        UserE userE = getTriggerUser(pipelineRecordId, null);
        if (userE != null) {
            recordReqDTO.setTriggerUserId(userE.getId());
            recordReqDTO.setTriggerUserName(userE.getRealName());
        }
        //查询stage
        List<PipelineStageRecordDTO> recordDTOList = new ArrayList<>();
        stageRecordRepository.queryByPipeRecordId(pipelineRecordId, null).forEach(t ->
                recordDTOList.add(ConvertHelper.convert(t, PipelineStageRecordDTO.class))
        );
        recordDTOList.forEach(t -> {
            //获取stage触发人员
            UserE userE1 = getTriggerUser(null, t.getId());
            if (userE1 != null) {
                t.setTriggerUserId(userE1.getId());
                t.setTriggerUserName(userE1.getRealName());
            }
            List<PipelineTaskRecordDTO> taskRecordDTOS = new ArrayList<>();
            //查询task
            taskRecordRepository.queryByStageRecordId(t.getId(), null).forEach(r -> {
                        PipelineTaskRecordDTO taskRecordDTO = ConvertHelper.convert(r, PipelineTaskRecordDTO.class);
                        //获取task触发人员
                        taskRecordDTO.setAuditUsers(StringUtils.join(pipelineUserRelRecordRepository.queryByRecordId(null, null, r.getId())
                                .stream()
                                .map(u -> iamRepository.queryUserByUserId(u.getUserId()).getRealName())
                                .toArray(), ","));
                        taskRecordDTOS.add(taskRecordDTO);
                    }
            );
            t.setTaskRecordDTOS(taskRecordDTOS);
        });
        recordReqDTO.setStageRecordDTOS(recordDTOList);

        return recordReqDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long projectId, Long pipelineRecordId) {
        String bpmDefinition = pipelineRecordRepository.queryById(pipelineRecordId).getBpmDefinition();
        DevopsPipelineDTO pipelineDTO = gson.fromJson(bpmDefinition, DevopsPipelineDTO.class);
        String instanceId = workFlowRepository.create(projectId, pipelineDTO);
        //清空之前数据
        PipelineRecordE pipelineRecordE = pipelineRecordRepository.queryById(pipelineRecordId);
        pipelineRecordE.setStatus(WorkFlowStatus.RUNNING.toValue());
        pipelineRecordE.setProcessInstanceId(instanceId);
        pipelineRecordRepository.update(pipelineRecordE);
        stageRecordRepository.queryByPipeRecordId(pipelineRecordId, null).forEach(t -> {
            t.setStatus(null);
            stageRecordRepository.update(t);
            taskRecordRepository.queryByStageRecordId(t.getId(), null).forEach(taskRecordE -> {
                taskRecordRepository.delete(taskRecordE.getId());
            });
        });
        //更新第一阶段
        if (pipelineRecordE.getTriggerType().equals(MANUAL)) {
            updateFirstStage(pipelineRecordId, pipelineRecordE.getPipelineId());
        }
    }

    @Override
    public List<PipelineRecordListDTO> queryByPipelineId(Long pipelineId) {
        return pipelineRecordRepository.queryByPipelineId(pipelineId).stream().map(t ->
                new PipelineRecordListDTO(t.getId(), t.getCreationDate())).collect(Collectors.toList());
    }

    @Override
    public void checkName(Long projectId, String name) {
        pipelineRepository.checkName(projectId, name);
    }

    @Override
    public List<PipelineDTO> listPipelineDTO(Long projectId) {
        return ConvertHelper.convertList(pipelineRepository.queryByProjectId(projectId), PipelineDTO.class);
    }

    @Override
    public List<UserDTO> getAllUsers(Long projectId) {
        return iamRepository.getAllMember(projectId);
    }

    @Override
    public void test(Long versionId) {
        versionService.checkAutoDeploy(versionRepository.query(versionId));
    }

    private void updateFirstStage(Long pipelineRecordId, Long pipelineId) {
        //更新第一个阶段状态
        PipelineStageRecordE stageRecordE = stageRecordRepository.queryByPipeRecordId(pipelineRecordId,
                stageRepository.queryByPipelineId(pipelineId).get(0).getId()).get(0);
        stageRecordE.setStatus(WorkFlowStatus.PENDINGCHECK.toValue());
        stageRecordRepository.createOrUpdate(stageRecordE);
        //更新第一个任务状态
        PipelineTaskRecordE taskRecordE = new PipelineTaskRecordE();
        PipelineTaskE taskE = getFirsetTask(pipelineId);
        BeanUtils.copyProperties(taskE, taskRecordE);
        taskRecordE.setId(null);
        taskRecordE.setTaskId(taskE.getId());
        taskRecordE.setStageRecordId(stageRecordE.getId());
        taskRecordE.setStatus(WorkFlowStatus.PENDINGCHECK.toValue());
        taskRecordE.setTaskType(taskE.getType());
        taskRecordRepository.createOrUpdate(taskRecordE);
    }

    @Override
    public void updateStatus(Long pipelineRecordId, Long stageRecordId, String status) {
        if (pipelineRecordId != null) {
            PipelineRecordE pipelineRecordE = new PipelineRecordE();
            pipelineRecordE.setId(pipelineRecordId);
            pipelineRecordE.setStatus(status);
            pipelineRecordRepository.update(pipelineRecordE);
        }

        if (stageRecordId != null) {
            PipelineStageRecordE stageRecordE = new PipelineStageRecordE();
            stageRecordE.setId(stageRecordId);
            stageRecordE.setStatus(status);
            stageRecordRepository.createOrUpdate(stageRecordE);
        }
    }

    private void AddPipelineTask(PipelineTaskDTO t, Long projectId, Long stageId) {
        t.setProjectId(projectId);
        t.setStageId(stageId);
        if (AUTO.equals(t.getType())) {
            //PipelineValue
            PipelineValueE pipelineValueE = valueRepository.queryById(t.getAppDeployDTOS().getValueId());
            pipelineValueE.setValue(t.getAppDeployDTOS().getValue());
            valueRepository.createOrUpdate(pipelineValueE);
            //appDeploy
            PipelineAppDeployE appDeployE = ConvertHelper.convert(t.getAppDeployDTOS(), PipelineAppDeployE.class);
            appDeployE.setProjectId(projectId);
            t.setAppDeployId(appDeployRepository.create(appDeployE).getId());
        }
        Long taskId = pipelineTaskRepository.create(ConvertHelper.convert(t, PipelineTaskE.class)).getId();
        if (MANUAL.equals(t.getType())) {
            createUserRel(t.getTaskUserRelDTOS(), null, null, taskId);
        }
    }

    private UserE getTriggerUser(Long pipelineRecordId, Long stageRecordId) {
        List<PipelineUserRecordRelE> taskUserRecordRelES = pipelineUserRelRecordRepository.queryByRecordId(pipelineRecordId, stageRecordId, null);
        if (taskUserRecordRelES != null && taskUserRecordRelES.size() > 0) {
            Long triggerUserId = taskUserRecordRelES.get(0).getUserId();
            return iamRepository.queryUserByUserId(triggerUserId);
        }
        return null;
    }

    private void startNextTaskRecord(Long taskRecordId, Long pipelineRecordId, Long stageRecordId) {
        PipelineTaskRecordE taskRecordE = taskRecordRepository.queryById(taskRecordId);
        Long stageId = isStageLastTask(taskRecordId);
        //属于阶段的最后一个任务
        if (stageId != null) {
            PipelineStageRecordE stageRecordE = stageRecordRepository.queryById(taskRecordE.getStageRecordId());
            stageRecordE.setStatus(WorkFlowStatus.SUCCESS.toValue());
            Long time = System.currentTimeMillis() - stageRecordE.getLastUpdateDate().getTime();
            stageRecordE.setExecutionTime(time.toString());
            stageRecordRepository.createOrUpdate(stageRecordE);
            //属于pipeline最后一个任务
            Long pipelineId = isPipelineLastTask(stageId);
            PipelineRecordE recordE = pipelineRecordRepository.queryById(pipelineRecordId);
            if (pipelineId != null) {
                recordE.setStatus(WorkFlowStatus.SUCCESS.toValue());
                pipelineRecordRepository.update(recordE);
            } else {
                //更新下一个阶段状态
                startNextStageRecord(stageId, recordE, stageRecordId);
            }
        } else {
            PipelineTaskE taskE = getNextTask(taskRecordE.getTaskId());
            if (taskE.getType().equals(MANUAL)) {
                PipelineTaskRecordE pipelineTaskRecordE = new PipelineTaskRecordE();
                BeanUtils.copyProperties(taskE, pipelineTaskRecordE);
                pipelineTaskRecordE.setId(null);
                pipelineTaskRecordE.setTaskId(taskE.getId());
                pipelineTaskRecordE.setStatus(WorkFlowStatus.PENDINGCHECK.toValue());
                taskRecordRepository.createOrUpdate(pipelineTaskRecordE);
                updateStatus(pipelineRecordId, stageRecordId, WorkFlowStatus.PENDINGCHECK.toValue());
            }
        }
    }

    private void startNextStageRecord(Long stageId, PipelineRecordE recordE, Long stageRecordId) {
        PipelineStageE nextStage = getNextStage(stageId);
        PipelineStageRecordE pipelineStageRecordE = stageRecordRepository.queryByPipeRecordId(recordE.getId(), nextStage.getId()).get(0);
        if (stageRecordRepository.queryById(stageRecordId).getTriggerType().equals(AUTO)) {
            pipelineStageRecordE.setStatus(WorkFlowStatus.RUNNING.toValue());
            List<PipelineTaskE> list = pipelineTaskRepository.queryByStageId(nextStage.getId());
            if (list != null && list.size() > 0) {
                if (list.get(0).getType().equals(WorkFlowStatus.PENDINGCHECK.toValue())) {
                    PipelineTaskRecordE taskRecordE = new PipelineTaskRecordE();
                    BeanUtils.copyProperties(pipelineTaskRepository.queryById(list.get(0).getId()), taskRecordE);
                    taskRecordE.setTaskId(list.get(0).getId());
                    taskRecordE.setId(null);
                    taskRecordE.setStatus(WorkFlowStatus.PENDINGCHECK.toValue());
                    taskRecordRepository.createOrUpdate(taskRecordE);
                    recordE.setStatus(WorkFlowStatus.PENDINGCHECK.toValue());
                    pipelineRecordRepository.update(recordE);
                }
            }
        } else {
            recordE.setStatus(WorkFlowStatus.PENDINGCHECK.toValue());
            pipelineRecordRepository.update(recordE);
        }
        stageRecordRepository.createOrUpdate(pipelineStageRecordE);
    }

    private PipelineStageE getNextStage(Long stageId) {
        List<PipelineStageE> list = stageRepository.queryByPipelineId(stageRepository.queryById(stageId).getPipelineId());
        return list.stream().filter(t -> t.getId() > stageId).findFirst().orElse(null);
    }

    private PipelineTaskE getNextTask(Long taskId) {
        List<PipelineTaskE> list = pipelineTaskRepository.queryByStageId(pipelineTaskRepository.queryById(taskId).getStageId());
        return list.stream().filter(t -> t.getId() > taskId).findFirst().orElse(null);
    }

    private Long isStageLastTask(Long taskRecordId) {
        PipelineTaskE pipelineTaskE = pipelineTaskRepository.queryById(taskRecordRepository.queryById(taskRecordId).getTaskId());
        List<PipelineTaskE> pipelineTaskES = pipelineTaskRepository.queryByStageId(pipelineTaskE.getStageId());
        return pipelineTaskES.get(pipelineTaskES.size() - 1).getId().equals(pipelineTaskE.getId()) ? pipelineTaskE.getStageId() : null;
    }

    private Long isPipelineLastTask(Long stageId) {
        PipelineStageE pipelineStageE = stageRepository.queryById(stageId);
        List<PipelineStageE> pipelineStageES = stageRepository.queryByPipelineId(pipelineStageE.getPipelineId());
        return pipelineStageES.get(pipelineStageES.size() - 1).getId().equals(pipelineStageE.getId()) ? pipelineStageE.getPipelineId() : null;
    }

    private Boolean checkTriggerPermission(PipelineE pipelineE, Long pipelineId, Long stageId) {
        if (pipelineE != null && AUTO.equals(pipelineE.getTriggerType())) {
            return false;
        }
        List<Long> userIds = pipelineUserRelRepository.listByOptions(pipelineId, stageId, null)
                .stream()
                .map(PipelineUserRelE::getUserId)
                .collect(Collectors.toList());
        if (!userIds.contains(DetailsHelper.getUserDetails().getUserId())) {
            return false;
        }
        return true;
    }

    private Boolean checkTaskTriggerPermission(Long taskId, Long taskRecordId) {
        PipelineTaskE taskE = pipelineTaskRepository.queryById(taskId);
        List<Long> userIds = pipelineUserRelRepository.listByOptions(null, null, null)
                .stream()
                .map(PipelineUserRelE::getUserId)
                .collect(Collectors.toList());
        if (taskE.getIsCountersigned() == 1) {
            List<Long> userIdRecords = pipelineUserRelRecordRepository.queryByRecordId(null, null, taskRecordId)
                    .stream()
                    .map(PipelineUserRecordRelE::getUserId)
                    .collect(Collectors.toList());
            //移除已经执行
            userIds.forEach(t -> {
                if (userIdRecords.contains(t)) {
                    userIds.remove(t);
                }
            });
        }
        return userIds.contains(DetailsHelper.getUserDetails().getUserId());
    }

    private void createUserRel(List<Long> pipelineUserRelDTOS, Long pipelineId, Long stageId, Long taskId) {
        if (pipelineUserRelDTOS != null) {
            pipelineUserRelDTOS.forEach(t -> {
                PipelineUserRelE userRelE = new PipelineUserRelE(t, pipelineId, stageId, taskId);
                pipelineUserRelRepository.create(userRelE);
            });
        }
    }

    private void updateUserRel(List<Long> relDTOList, Long pipelineId, Long stageId, Long taskId) {
        List<Long> addUserRelEList = new ArrayList<>();
        List<Long> relEList = pipelineUserRelRepository.listByOptions(pipelineId, stageId, taskId).stream().map(PipelineUserRelE::getUserId).collect(Collectors.toList());
        relDTOList.forEach(relE -> {
            if (!relEList.contains(relE)) {
                addUserRelEList.add(relE);
            } else {
                relEList.remove(relE);
            }
        });
        addUserRelEList.forEach(addUserId -> {
            PipelineUserRelE addUserRelE = new PipelineUserRelE(addUserId, pipelineId, stageId, taskId);
            pipelineUserRelRepository.create(addUserRelE);
        });
        relEList.forEach(delUserId -> {
            PipelineUserRelE addUserRelE = new PipelineUserRelE(delUserId, pipelineId, stageId, taskId);
            pipelineUserRelRepository.delete(addUserRelE);
        });
    }

}
