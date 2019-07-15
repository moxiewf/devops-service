package io.choerodon.devops.app.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.choerodon.devops.app.service.CertificationService;
import io.choerodon.devops.app.service.DevopsEnvFileResourceService;
import io.choerodon.devops.api.vo.iam.entity.CertificationE;
import io.choerodon.devops.api.vo.iam.entity.DevopsEnvCommandVO;
import io.choerodon.devops.api.vo.iam.entity.DevopsEnvFileResourceVO;
import io.choerodon.devops.api.vo.iam.entity.DevopsEnvironmentE;
import io.choerodon.devops.infra.exception.GitOpsExplainException;
import io.choerodon.devops.domain.application.repository.CertificationRepository;
import io.choerodon.devops.domain.application.repository.DevopsEnvCommandRepository;
import io.choerodon.devops.domain.application.repository.DevopsEnvFileResourceRepository;
import io.choerodon.devops.domain.application.repository.DevopsEnvironmentRepository;
import io.choerodon.devops.domain.application.valueobject.C7nCertification;
import io.choerodon.devops.domain.application.valueobject.certification.CertificationExistCert;
import io.choerodon.devops.domain.application.valueobject.certification.CertificationSpec;
import io.choerodon.devops.app.service.HandlerObjectFileRelationsService;
import io.choerodon.devops.infra.util.GitUtil;
import io.choerodon.devops.infra.util.TypeUtil;
import io.choerodon.devops.infra.enums.*;
import io.choerodon.devops.infra.dto.CertificationFileDO;
import io.kubernetes.client.models.V1Endpoints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class HandlerC7nCertificationServiceImpl implements HandlerObjectFileRelationsService<C7nCertification> {

    private static final String CERTIFICATE = "Certificate";
    private static final String GIT_SUFFIX = "/.git";
    public static final String LETSENCRYPT_PROD = "letsencrypt-prod";
    public static final String LOCALHOST = "localhost";
    public static final String CLUSTER_ISSUER = "ClusterIssuer";

    @Autowired
    private CertificationRepository certificationRepository;
    @Autowired
    private CertificationService certificationService;
    @Autowired
    private DevopsEnvFileResourceRepository devopsEnvFileResourceRepository;
    @Autowired
    private DevopsEnvironmentRepository devopsEnvironmentRepository;
    @Autowired
    private DevopsEnvFileResourceService devopsEnvFileResourceService;
    @Autowired
    private DevopsEnvCommandRepository devopsEnvCommandRepository;

    @Override
    public void handlerRelations(Map<String, String> objectPath, List<DevopsEnvFileResourceVO> beforeSync,

                                 List<C7nCertification> c7nCertifications, List<V1Endpoints> v1Endpoints, Long envId, Long projectId, String path, Long userId) {
        List<C7nCertification> updateC7nCertification = new ArrayList<>();
        List<String> beforeC7nCertification = beforeSync.stream()
                .filter(devopsEnvFileResourceE -> devopsEnvFileResourceE.getResourceType().equals(CERTIFICATE))
                .map(devopsEnvFileResourceE -> {
                    CertificationE certificationE = certificationRepository
                            .baseQueryById(devopsEnvFileResourceE.getResourceId());
                    if (certificationE == null) {
                        devopsEnvFileResourceRepository
                                .baseDeleteByEnvIdAndResourceId(envId, devopsEnvFileResourceE.getResourceId(), ObjectType.CERTIFICATE.getType());
                        return null;
                    }
                    return certificationE.getName();
                })
                .collect(Collectors.toList());

        List<C7nCertification> addC7nCertification = new ArrayList<>();
        c7nCertifications.stream()
                .forEach(certification -> {
                    int index = beforeC7nCertification.indexOf(certification.getMetadata().getName());
                    if (index != -1) {
                        updateC7nCertification.add(certification);
                        beforeC7nCertification.remove(index);
                    } else {
                        addC7nCertification.add(certification);
                    }
                });
        updateC7nCertification.forEach(c7nCertification1 ->
                updateC7nCertificationPath(c7nCertification1, envId, objectPath, path));
        beforeC7nCertification
                .forEach(certName -> {
                    CertificationE certificationE = certificationRepository.baseQueryByEnvAndName(envId, certName);
                    if (certificationE != null) {
                        certificationService.certDeleteByGitOps(certificationE.getId());
                        devopsEnvFileResourceRepository
                                .baseDeleteByEnvIdAndResourceId(envId, certificationE.getId(), ObjectType.CERTIFICATE.getType());
                    }

                });


        addC7nCertification.stream().forEach(c7nCertification -> {
            String filePath = "";
            try {
                filePath = objectPath.get(TypeUtil.objToString(c7nCertification.hashCode()));
                DevopsEnvFileResourceVO devopsEnvFileResourceE = new DevopsEnvFileResourceVO();
                devopsEnvFileResourceE.setEnvironment(new DevopsEnvironmentE(envId));
                devopsEnvFileResourceE.setFilePath(filePath);
                devopsEnvFileResourceE.setResourceId(
                        createCertificationAndGetId(
                                envId, c7nCertification, c7nCertification.getMetadata().getName(), filePath, path, userId));
                devopsEnvFileResourceE.setResourceType(c7nCertification.getKind());
                devopsEnvFileResourceRepository.baseCreate(devopsEnvFileResourceE);
            } catch (Exception e) {
                throw new GitOpsExplainException(e.getMessage(), filePath, e);
            }
        });
    }

    private void updateC7nCertificationPath(C7nCertification c7nCertification,
                                            Long envId, Map<String, String> objectPath, String path) {
        Long certId = checkC7nCertificationChanges(c7nCertification, envId, objectPath, path);

        String kind = c7nCertification.getKind();
        DevopsEnvFileResourceVO devopsEnvFileResourceE = devopsEnvFileResourceRepository
                .baseQueryByEnvIdAndResourceId(envId, certId, kind);
        devopsEnvFileResourceService.updateOrCreateFileResource(objectPath, envId,
                devopsEnvFileResourceE, c7nCertification.hashCode(), certId, kind);

    }

    private Long checkC7nCertificationChanges(C7nCertification c7nCertification, Long envId,
                                              Map<String, String> objectPath, String path) {
        DevopsEnvironmentE environmentE = devopsEnvironmentRepository.baseQueryById(envId);
        String certName = c7nCertification.getMetadata().getName();
        CertificationE certificationE = certificationRepository.baseQueryByEnvAndName(envId, certName);
        CertificationFileDO certificationFileDO = certificationRepository.baseGetCertFile(certificationE.getId());
        String type;
        String keyContent = null;
        String certContent = null;
        Map<String,String> issuerRef = new HashMap<>();
        if (certificationFileDO != null) {
            type = CertificationType.UPLOAD.getType();
            keyContent = certificationFileDO.getKeyFile();
            certContent = certificationFileDO.getCertFile();
            issuerRef.put("name", LOCALHOST);
            issuerRef.put("kind", CLUSTER_ISSUER);
        } else {
            type = CertificationType.REQUEST.getType();
            issuerRef.put("name", LETSENCRYPT_PROD);
            issuerRef.put("kind", CLUSTER_ISSUER);
        }
        c7nCertification.getSpec().setIssuerRef(issuerRef);


        String filePath = objectPath.get(TypeUtil.objToString(c7nCertification.hashCode()));
        C7nCertification oldC7nCertification = certificationService.getC7nCertification(
                certName, type, certificationE.getDomains(), keyContent, certContent, environmentE.getCode());
        if (!c7nCertification.equals(oldC7nCertification)) {
            throw new GitOpsExplainException(GitOpsObjectError.CERT_CHANGED.getError(), filePath);
        }
        updateCommandSha(filePath, path, certificationE.getCommandId());
        return certificationE.getId();
    }

    private Long createCertificationAndGetId(Long envId, C7nCertification c7nCertification, String certName,
                                             String filePath, String path, Long userId) {
        CertificationE certificationE = certificationRepository
                .baseQueryByEnvAndName(envId, certName);
        if (certificationE == null) {
            certificationE = new CertificationE();

            CertificationSpec certificationSpec = c7nCertification.getSpec();
            String domain = certificationSpec.getCommonName();
            List<String> dnsDomain = certificationSpec.getDnsNames();
            List<String> domains = new ArrayList<>();
            domains.add(domain);
            if (dnsDomain != null && !dnsDomain.isEmpty()) {
                domains.addAll(dnsDomain);
            }
            certificationE.setDomains(domains);
            certificationE.setEnvironmentE(new DevopsEnvironmentE(envId));
            certificationE.setName(certName);
            certificationE.setStatus(CertificationStatus.OPERATING.getStatus());
            certificationE = certificationRepository.baseCreate(certificationE);
            CertificationExistCert existCert = c7nCertification.getSpec().getExistCert();
            if (existCert != null) {
                certificationE.setCertificationFileId(certificationRepository.baseStoreCertFile(
                        new CertificationFileDO(existCert.getCert(), existCert.getKey())));
            }
            Long commandId = certificationService
                    .createCertCommandE(CommandType.CREATE.getType(), certificationE.getId(), userId);
            certificationE.setCommandId(commandId);
            certificationRepository.baseUpdateCommandId(certificationE);
        }
        updateCommandSha(filePath, path, certificationE.getCommandId());
        return certificationE.getId();
    }

    private void updateCommandSha(String filePath, String path, Long commandId) {
        DevopsEnvCommandVO devopsEnvCommandE = devopsEnvCommandRepository.query(commandId);
        devopsEnvCommandE.setSha(GitUtil.getFileLatestCommit(path + GIT_SUFFIX, filePath));
        devopsEnvCommandRepository.update(devopsEnvCommandE);
    }
}
