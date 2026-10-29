package com.mockhub.mock.service;

import com.mockhub.common.config.DataProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SoapService 单元测试
 * 覆盖：
 *  - getWsdlContent 的 location 替换
 *  - parseWsdlFile 的响应体骨架生成
 *  - parseWsdlFile 的 wsdl:documentation 提取（v1.4.4）
 */
class SoapServiceTest {

    @TempDir
    Path tempDir;

    private SoapService service;

    @BeforeEach
    void setUp() throws IOException {
        DataProperties props = new DataProperties();
        props.setPath(tempDir.toString());
        Files.createDirectories(tempDir.resolve("wsdl"));
        service = new SoapService(props, new SoapSkeletonGenerator());
        org.sqlite.SQLiteDataSource ds=new org.sqlite.SQLiteDataSource();ds.setUrl("jdbc:sqlite:"+tempDir.resolve("legacy.db"));
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("schema.sql")).execute(ds);
        org.springframework.jdbc.core.JdbcTemplate jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('team','Team','TT','before')");
        jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES('u','u','hash','U','USER','before','before')");
        jdbc.update("INSERT INTO user_team VALUES('u','team','EDITOR')");
        for(String name:new String[]{"test.wsdl","foo.wsdl","docA.wsdl","docB.wsdl","docC.wsdl","docD.wsdl"})
            jdbc.update("INSERT INTO api_definition(id,team_id,name,type,method,path,soap_config,created_at,updated_at) VALUES(?,'team',?,'SOAP','POST',?,?,'before','before')",name,name,"/"+name,"{\"wsdlFileName\":\""+name+"\"}");
        com.mockhub.system.repository.TeamRepository teams=new com.mockhub.system.repository.TeamRepository();org.springframework.test.util.ReflectionTestUtils.setField(teams,"jdbcTemplate",jdbc);
        com.mockhub.system.repository.UserRepository users=new com.mockhub.system.repository.UserRepository();org.springframework.test.util.ReflectionTestUtils.setField(users,"jdbcTemplate",jdbc);
        com.mockhub.system.service.PermissionCheckerImpl permission=new com.mockhub.system.service.PermissionCheckerImpl();org.springframework.test.util.ReflectionTestUtils.setField(permission,"teamRepository",teams);org.springframework.test.util.ReflectionTestUtils.setField(permission,"userRepository",users);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"permissionChecker",permission);org.springframework.test.util.ReflectionTestUtils.setField(service,"teamRepository",teams);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"references",new com.mockhub.mock.repository.WsdlReferenceRepository(jdbc,new com.fasterxml.jackson.databind.ObjectMapper()));
        java.util.Map<String,Object> details=new java.util.HashMap<>();details.put("userId","u");details.put("globalRole","USER");org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("u",null,java.util.Collections.emptyList());auth.setDetails(details);org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @org.junit.jupiter.api.AfterEach void clearContext(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}

    @Test
    void getWsdlContent_replacesLocationWithMockUrl() throws IOException {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<wsdl:definitions xmlns:wsdl=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" xmlns:soap12=\"http://schemas.xmlsoap.org/wsdl/soap12/\">\n" +
                "  <wsdl:service name=\"S\">\n" +
                "    <wsdl:port>\n" +
                "      <soap:address location=\"http://original.host/Service.asmx\"/>\n" +
                "      <soap12:address location=\"http://original.host/Service.asmx\"/>\n" +
                "    </wsdl:port>\n" +
                "  </wsdl:service>\n" +
                "</wsdl:definitions>";
        Files.write(tempDir.resolve("wsdl").resolve("test.wsdl"), wsdl.getBytes("UTF-8"));

        String mockUrl = "http://mockhub.local:8080/mock/TEAM_B/demo/release";
        String content = service.getWsdlContent("team","test.wsdl", mockUrl);

        // SOAP 1.1 和 1.2 两个 location 都应替换为完整 mockUrl
        assertTrue(content.contains("location=\"" + mockUrl + "\""),
                "location 应替换为 mockUrl");
        int occurrences = content.split(Pattern.quote("location=\"" + mockUrl + "\""), -1).length - 1;
        org.junit.jupiter.api.Assertions.assertEquals(2, occurrences,
                "SOAP 1.1 和 SOAP 1.2 两处 location 都应替换");
        assertFalse(content.contains("original.host"),
                "原始 host 不应残留");
    }

    @Test
    void parseWsdlFile_generatesSuggestedResponseBody() throws IOException {
        // 构造最小 WSDL：1 个 operation，含 binding/soap:operation/soapAction
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                "             xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"\n" +
                "             xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "             xmlns:tns=\"http://tempuri.org/\"\n" +
                "             targetNamespace=\"http://tempuri.org/\">\n" +
                "  <types><xs:schema targetNamespace=\"http://tempuri.org/\" elementFormDefault=\"qualified\">\n" +
                "    <xs:element name=\"GetFooResponse\"><xs:complexType><xs:sequence>\n" +
                "      <xs:element name=\"GetFooResult\" type=\"xs:string\" minOccurs=\"0\"/>\n" +
                "    </xs:sequence></xs:complexType></xs:element>\n" +
                "  </xs:schema></types>\n" +
                "  <portType name=\"P\"><operation name=\"GetFoo\"/></portType>\n" +
                "  <binding name=\"B\" type=\"tns:P\">\n" +
                "    <soap:binding transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
                "    <operation name=\"GetFoo\"><soap:operation soapAction=\"http://tempuri.org/GetFoo\"/></operation>\n" +
                "  </binding>\n" +
                "</definitions>";
        Files.write(tempDir.resolve("wsdl").resolve("foo.wsdl"), wsdl.getBytes("UTF-8"));

        com.mockhub.mock.model.dto.WsdlParseResult result = service.parseOperations("team","foo.wsdl");

        org.junit.jupiter.api.Assertions.assertEquals(1, result.getOperations().size());
        com.mockhub.mock.model.dto.WsdlParseResult.WsdlOperation op = result.getOperations().get(0);
        assertNotNull(op.getSuggestedResponseBody(), "骨架应生成");
        assertTrue(op.getSuggestedResponseBody().contains("GetFooResponse"));
        assertTrue(op.getSuggestedResponseBody().contains("GetFooResult"));
    }

    /**
     * case A：WSDL 的 portType/operation 下含 wsdl:documentation，应提取到 description。
     */
    @Test
    void parseWsdlFile_extractsDocumentationFromPortType() throws IOException {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                "             xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"\n" +
                "             xmlns:tns=\"http://tempuri.org/\"\n" +
                "             targetNamespace=\"http://tempuri.org/\">\n" +
                "  <portType name=\"P\">\n" +
                "    <operation name=\"Login\">\n" +
                "      <documentation>用户登录接口，参数 userId 为必填</documentation>\n" +
                "    </operation>\n" +
                "  </portType>\n" +
                "  <binding name=\"B\" type=\"tns:P\">\n" +
                "    <soap:binding transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
                "    <operation name=\"Login\"><soap:operation soapAction=\"http://tempuri.org/Login\"/></operation>\n" +
                "  </binding>\n" +
                "</definitions>";
        Files.write(tempDir.resolve("wsdl").resolve("docA.wsdl"), wsdl.getBytes("UTF-8"));

        com.mockhub.mock.model.dto.WsdlParseResult result = service.parseOperations("team","docA.wsdl");

        assertEquals(1, result.getOperations().size());
        com.mockhub.mock.model.dto.WsdlParseResult.WsdlOperation op = result.getOperations().get(0);
        assertEquals("用户登录接口，参数 userId 为必填", op.getDescription(),
                "应从 portType/operation/documentation 提取描述文本");
    }

    /**
     * case B：WSDL 无 documentation 元素，description 应为 null（不报错）。
     */
    @Test
    void parseWsdlFile_noDocumentation_returnsNullDescription() throws IOException {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                "             xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"\n" +
                "             xmlns:tns=\"http://tempuri.org/\"\n" +
                "             targetNamespace=\"http://tempuri.org/\">\n" +
                "  <portType name=\"P\"><operation name=\"NoDoc\"/></portType>\n" +
                "  <binding name=\"B\" type=\"tns:P\">\n" +
                "    <soap:binding transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
                "    <operation name=\"NoDoc\"><soap:operation soapAction=\"http://tempuri.org/NoDoc\"/></operation>\n" +
                "  </binding>\n" +
                "</definitions>";
        Files.write(tempDir.resolve("wsdl").resolve("docB.wsdl"), wsdl.getBytes("UTF-8"));

        com.mockhub.mock.model.dto.WsdlParseResult result = service.parseOperations("team","docB.wsdl");

        assertEquals(1, result.getOperations().size());
        assertNull(result.getOperations().get(0).getDescription(),
                "WSDL 未提供 documentation 时 description 应为 null");
    }

    /**
     * case C：documentation 空白（只有空格/换行）应被视为 null，避免后端传无意义空串到前端。
     */
    @Test
    void parseWsdlFile_blankDocumentation_returnsNull() throws IOException {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                "             xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"\n" +
                "             xmlns:tns=\"http://tempuri.org/\"\n" +
                "             targetNamespace=\"http://tempuri.org/\">\n" +
                "  <portType name=\"P\">\n" +
                "    <operation name=\"Blank\">\n" +
                "      <documentation>   \n\t  </documentation>\n" +
                "    </operation>\n" +
                "  </portType>\n" +
                "  <binding name=\"B\" type=\"tns:P\">\n" +
                "    <soap:binding transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
                "    <operation name=\"Blank\"><soap:operation soapAction=\"http://tempuri.org/Blank\"/></operation>\n" +
                "  </binding>\n" +
                "</definitions>";
        Files.write(tempDir.resolve("wsdl").resolve("docC.wsdl"), wsdl.getBytes("UTF-8"));

        com.mockhub.mock.model.dto.WsdlParseResult result = service.parseOperations("team","docC.wsdl");

        assertEquals(1, result.getOperations().size());
        assertNull(result.getOperations().get(0).getDescription(),
                "仅空白字符的 documentation 应视为空");
    }

    /**
     * case D：多个 operation 各自的 documentation 不串台。
     */
    @Test
    void parseWsdlFile_multipleOperations_independentDescriptions() throws IOException {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\"\n" +
                "             xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\"\n" +
                "             xmlns:tns=\"http://tempuri.org/\"\n" +
                "             targetNamespace=\"http://tempuri.org/\">\n" +
                "  <portType name=\"P\">\n" +
                "    <operation name=\"Login\"><documentation>登录</documentation></operation>\n" +
                "    <operation name=\"Logout\"><documentation>登出</documentation></operation>\n" +
                "    <operation name=\"Anon\"/>\n" +
                "  </portType>\n" +
                "  <binding name=\"B\" type=\"tns:P\">\n" +
                "    <soap:binding transport=\"http://schemas.xmlsoap.org/soap/http\"/>\n" +
                "    <operation name=\"Login\"><soap:operation soapAction=\"http://tempuri.org/Login\"/></operation>\n" +
                "    <operation name=\"Logout\"><soap:operation soapAction=\"http://tempuri.org/Logout\"/></operation>\n" +
                "    <operation name=\"Anon\"><soap:operation soapAction=\"http://tempuri.org/Anon\"/></operation>\n" +
                "  </binding>\n" +
                "</definitions>";
        Files.write(tempDir.resolve("wsdl").resolve("docD.wsdl"), wsdl.getBytes("UTF-8"));

        com.mockhub.mock.model.dto.WsdlParseResult result = service.parseOperations("team","docD.wsdl");

        assertEquals(3, result.getOperations().size());
        java.util.Map<String, String> descByName = new java.util.HashMap<>();
        for (com.mockhub.mock.model.dto.WsdlParseResult.WsdlOperation op : result.getOperations()) {
            descByName.put(op.getOperationName(), op.getDescription());
        }
        assertEquals("登录", descByName.get("Login"));
        assertEquals("登出", descByName.get("Logout"));
        assertNull(descByName.get("Anon"));
    }
}
