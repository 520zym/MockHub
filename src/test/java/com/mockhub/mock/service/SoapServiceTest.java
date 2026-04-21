package com.mockhub.mock.service;

import com.mockhub.common.config.DataProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SoapService 单元测试
 * 覆盖 getWsdlContent 的 location 替换。
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
    }

    @Test
    void getWsdlContent_replacesLocationWithMockUrl() throws IOException {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<wsdl:definitions>\n" +
                "  <wsdl:service name=\"S\">\n" +
                "    <wsdl:port>\n" +
                "      <soap:address location=\"http://original.host/Service.asmx\"/>\n" +
                "      <soap12:address location=\"http://original.host/Service.asmx\"/>\n" +
                "    </wsdl:port>\n" +
                "  </wsdl:service>\n" +
                "</wsdl:definitions>";
        Files.write(tempDir.resolve("wsdl").resolve("test.wsdl"), wsdl.getBytes("UTF-8"));

        String mockUrl = "http://mockhub.local:8080/mock/EFB/ck/release";
        String content = service.getWsdlContent("test.wsdl", mockUrl);

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

        com.mockhub.mock.model.dto.WsdlParseResult result = service.parseOperations("foo.wsdl");

        org.junit.jupiter.api.Assertions.assertEquals(1, result.getOperations().size());
        com.mockhub.mock.model.dto.WsdlParseResult.WsdlOperation op = result.getOperations().get(0);
        assertNotNull(op.getSuggestedResponseBody(), "骨架应生成");
        assertTrue(op.getSuggestedResponseBody().contains("GetFooResponse"));
        assertTrue(op.getSuggestedResponseBody().contains("GetFooResult"));
    }
}
