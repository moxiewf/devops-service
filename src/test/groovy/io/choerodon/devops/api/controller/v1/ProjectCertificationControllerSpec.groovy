package io.choerodon.devops.api.controller.v1

import com.github.pagehelper.PageInfo
import io.choerodon.core.exception.CommonException
import io.choerodon.core.exception.ExceptionResponse
import io.choerodon.devops.DependencyInjectUtil
import io.choerodon.devops.IntegrationTestConfiguration
import io.choerodon.devops.api.vo.ProjectCertificationVO
import io.choerodon.devops.app.service.IamService
import io.choerodon.devops.infra.dto.iam.OrganizationDTO
import io.choerodon.devops.infra.dto.iam.ProjectDTO
import io.choerodon.devops.infra.feign.BaseServiceClient
import io.choerodon.devops.infra.feign.operator.BaseServiceClientOperator
import io.choerodon.devops.infra.handler.ClusterConnectionHandler
import io.choerodon.devops.infra.mapper.DevopsCertificationMapper
import io.choerodon.devops.infra.mapper.DevopsCertificationProRelMapper
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Subject

import static org.mockito.ArgumentMatchers.*
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(IntegrationTestConfiguration)
@Subject(ProjectCertificationController)
@Stepwise
class ProjectCertificationControllerSpec extends Specification {

    private static final String MAPPING = "/v1/projects/{project_id}/certs"
    private static Long ID


    @Autowired
    private TestRestTemplate restTemplate

    @Autowired
    private BaseServiceClientOperator baseServiceClientOperator
    @Autowired
    private DevopsCertificationMapper devopsCertificationMapper
    @Autowired
    private DevopsCertificationProRelMapper devopsCertificationProRelMapper

    @Autowired
    @Qualifier("mockClusterConnectionHandler")
    private ClusterConnectionHandler envUtil

    BaseServiceClient baseServiceClient = Mockito.mock(BaseServiceClient)

    void setup() {
        DependencyInjectUtil.setAttribute(baseServiceClientOperator, "baseServiceClient", baseServiceClient)

        ProjectDTO projectDO = new ProjectDTO()
        projectDO.setId(1L)
        projectDO.setCode("pro")
        projectDO.setOrganizationId(1L)
        ResponseEntity<ProjectDTO> responseEntity = new ResponseEntity<>(projectDO, HttpStatus.OK)
        Mockito.doReturn(responseEntity).when(baseServiceClient).queryIamProject(anyLong())

        OrganizationDTO organizationDO = new OrganizationDTO()
        organizationDO.setId(1L)
        organizationDO.setCode("org")
        ResponseEntity<OrganizationDTO> responseEntity1 = new ResponseEntity<>(organizationDO, HttpStatus.OK)
        Mockito.doReturn(responseEntity1).when(baseServiceClient).queryOrganizationById(anyLong())

        List<ProjectDTO> projectDOList = new ArrayList<>()
        projectDOList.add(projectDO)
        PageInfo<ProjectDTO> projectDOPage = new PageInfo<>(projectDOList)
        ResponseEntity<PageInfo<ProjectDTO>> projectDOPageResponseEntity = new ResponseEntity<>(projectDOPage, HttpStatus.OK)
        Mockito.when(baseServiceClient.queryProjectByOrgId(anyLong(), anyInt(), anyInt(), anyString(), any(String[]))).thenReturn(projectDOPageResponseEntity)
    }

//    def "Create"() {
//        given: '初始化DTO'
//        MultiValueMap<String, Object> map = new LinkedMultiValueMap<String, Object>()
//        map.add("name", "test")
//        map.add("domain", "test")
//        map.add("skipCheckProjectPermission", false)
//        map.add("projects", 1)
//        map.add("keyValue", "-----BEGIN RSA PRIVATE KEY-----\n" +
//                "MIICXgIBAAKBgQC3G09zGCmlMetvNaWiHbp9d8jItkj5ik0wKcn7jBy/eOdlno5m\n" +
//                "y+eijTP/KX8D2QNj2vlF+31/AThYoxI80qUZ3imw8vDVc0cBeGLxEDLVweCQEy7C\n" +
//                "ivpkmEWCeyoqThenrwONoIjajG7ZJggFTrsXHL6HsbkGxYrABG2PmQ/w0QIDAQAB\n" +
//                "AoGBAIxvTcggSBCC8OciZh6oXlfMfxoxdFavU/QUmO1s0L+pow+1Q9JjoQxy7+ZL\n" +
//                "lTcGQitbzsN11xKJhQW2TE6J4EVimJZQSAE4DDmYpMOrkjnBQhkUlaZkkukvDSRS\n" +
//                "JqwBI/04G7se+RouHyXjRS9U76HnPM8+/IS2h+T6CbXLOpYBAkEA2j0JmyGVs+WV\n" +
//                "I9sG5glamJqTBa4CfTORrdFW4EULoGkUc24ZFFqn9W4e5yfl/pCkPptCenvIrAWp\n" +
//                "/ymnHeLn6QJBANbKGO9uBizAt4+o+kHYdANcbU/Cs3PLj8yOOtjkuMbH4tPNQmB6\n" +
//                "/u3npiVk7/Txfkg0BjRzDDZib109eKbvGKkCQBgMneBghRS7+gFng40Z/sfOUOFR\n" +
//                "WajeY/FZnk88jJlyuvQ1b8IUc2nSZslmViwFWHQlu9+vgF+kiCU8O9RJSvECQQCl\n" +
//                "Vkx7giYerPqgC2MY7JXhQHSkwSuCJ2A6BgImk2npGlTw1UATJJq4Z2jtwBU2Z+7d\n" +
//                "ha6BEU6FTqCLFZaaadKBAkEAxko4hrgBsX9BKpFJE3aUIUcMTJfJQdiAhq0k4DV8\n" +
//                "5GVrcp8zl6mUTPZDaOmDhuAjGdAQJqj0Xo0PZ0fOZPtR+w==\n" +
//                "-----END RSA PRIVATE KEY-----")
//        map.add("certValue", "-----BEGIN CERTIFICATE-----\n" +
//                "MIICYTCCAcoCCQCs45mePIbzRTANBgkqhkiG9w0BAQUFADB1MQswCQYDVQQGEwJV\n" +
//                "UzENMAsGA1UECAwETWFyczETMBEGA1UEBwwKaVRyYW5zd2FycDETMBEGA1UECgwK\n" +
//                "aVRyYW5zd2FycDETMBEGA1UECwwKaVRyYW5zd2FycDEYMBYGA1UEAwwPd3d3LjU5\n" +
//                "MXdpZmkuY29tMB4XDTE4MTAxNzAyMTA0OFoXDTI4MTAxNDAyMTA0OFowdTELMAkG\n" +
//                "A1UEBhMCVVMxDTALBgNVBAgMBE1hcnMxEzARBgNVBAcMCmlUcmFuc3dhcnAxEzAR\n" +
//                "BgNVBAoMCmlUcmFuc3dhcnAxEzARBgNVBAsMCmlUcmFuc3dhcnAxGDAWBgNVBAMM\n" +
//                "D3d3dy41OTF3aWZpLmNvbTCBnzANBgkqhkiG9w0BAQEFAAOBjQAwgYkCgYEAtxtP\n" +
//                "cxgppTHrbzWloh26fXfIyLZI+YpNMCnJ+4wcv3jnZZ6OZsvnoo0z/yl/A9kDY9r5\n" +
//                "Rft9fwE4WKMSPNKlGd4psPLw1XNHAXhi8RAy1cHgkBMuwor6ZJhFgnsqKk4Xp68D\n" +
//                "jaCI2oxu2SYIBU67Fxy+h7G5BsWKwARtj5kP8NECAwEAATANBgkqhkiG9w0BAQUF\n" +
//                "AAOBgQC2Pko8q1NicJ0oPuhFTPm7n03LtPhCaV/aDf3mqtGxraYifg8iFTxVyZ1c\n" +
//                "ol0eEJFsibrQrPEwdSuSVqzwif5Tab9dV92PPFm+Sq0D1Uc0xI4ziXQ+a55K9wrV\n" +
//                "TKXxS48TOpnTA8fVFNkUkFNB54Lhh9AwKsx123kJmyaWccbt9Q==\n" +
//                "-----END CERTIFICATE-----")
//        when: '组织下创建证书'
//        restTemplate.postForEntity(MAPPING, map, Object.class, 1L)
//
//        then: '校验返回值'
//        devopsCertificationMapper.selectAll().size() == 1
//
//    }

    def "Update"() {
//        given: '初始化DTO'
//        ProjectCertificationVO orgCertificationDTO = new ProjectCertificationVO()
//        List<Long> projectIds = new ArrayList<>()
//        projectIds.add(2L)
//        orgCertificationDTO.setProjects(projectIds)
//        ID = devopsCertificationMapper.selectAll().get(0).getId()
//        orgCertificationDTO.setSkipCheckProjectPermission(false)
//
//        when: '更新证书下的项目'
//        restTemplate.put(MAPPING + "/" + ID, orgCertificationDTO, 1L)
//
//        then: '校验是否更新'
//        devopsCertificationProRelMapper.selectAll().get(0).getProjectId() == 2
    }

    def "Query"() {
        when: '查询单个证书信息'
        def dto = restTemplate.getForObject(MAPPING + "/" + ID, ProjectCertificationVO.class, 1L)

        then: '校验返回值'
        dto["name"] == "test"
    }

    def "CheckName"() {
        when: '校验证书名唯一性'
        def exception = restTemplate.getForEntity(MAPPING + "/check_name?name=uniqueName", ExceptionResponse.class, 1L)

        then: '名字不存在不抛出异常'
        exception.statusCode.is2xxSuccessful()
        notThrown(CommonException)
    }

    def "PageProjects"() {
        given: '模糊查询参数'
        String[] str = new String[1]
        str[0] = "{}"

        when: '分页查询项目列表'
        def e = restTemplate.postForEntity(MAPPING + "/page_projects?page=0&size=10&certId=" + ID, str, PageInfo.class, 1L)

        then: '校验返回值'
        e.getBody().getList().get(0)["code"] == "pro"
    }

    def "ListCertProjects"() {
        when: '查询已有权限的项目列表'
        def e = restTemplate.getForEntity(MAPPING + "/list_cert_projects/{certId}", List.class, 1L, ID)

        then: '校验返回值'
        e.getBody().get(0)["code"] == "pro"
    }

    def "ListOrgCert"() {
        given: '查询参数'
        String str = new String("{}")


        when: '证书列表查询'
        def e = restTemplate.postForEntity(MAPPING + "/page_cert?page=0&size=10", str, PageInfo.class, 1L)

        then: '校验返回值'
        e.getBody().getList().get(0)["name"] == "test"
    }

    def "DeleteOrgCert"() {

        when: '删除证书'
        restTemplate.delete(MAPPING + "/{certId}", 1L, ID)

        then: '校验返回值'
        devopsCertificationMapper.selectAll().size() == 0
        devopsCertificationProRelMapper.selectAll().size() == 0
    }

    // TODO 该接口通知李浩完成
    //列出项目下所有与该证书未分配权限的项目成员
    def "ListAllNonRelatedMembers"() {
    }

    // TODO 该接口通知李浩完成
    //删除项目在该证书下的权限
    def "DeletePermissionOfProject"() {
    }
}
