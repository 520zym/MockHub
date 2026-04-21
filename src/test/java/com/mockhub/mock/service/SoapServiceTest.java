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
        service = new SoapService(props);
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
}
