package com.mockhub.mock.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SoapSkeletonGenerator 单元测试
 * 通过构造内联 WSDL/XSD 字符串验证生成结果。
 */
class SoapSkeletonGeneratorTest {

    private SoapSkeletonGenerator gen;

    @BeforeEach
    void setUp() {
        gen = new SoapSkeletonGenerator();
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder b = factory.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
    }

    @Test
    void generatesEnvelopeForStringResult() throws Exception {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://tempuri.org/\">\n" +
                "  <types><xs:schema targetNamespace=\"http://tempuri.org/\" elementFormDefault=\"qualified\">\n" +
                "    <xs:element name=\"GetNaviResponse\"><xs:complexType><xs:sequence>\n" +
                "      <xs:element name=\"GetNaviResult\" type=\"xs:string\" minOccurs=\"0\"/>\n" +
                "    </xs:sequence></xs:complexType></xs:element>\n" +
                "  </xs:schema></types>\n" +
                "</definitions>";

        String skeleton = gen.generate(parse(wsdl), "GetNavi", "http://tempuri.org/");

        assertNotNull(skeleton);
        assertTrue(skeleton.contains("<soap:Envelope"), "含 Envelope");
        assertTrue(skeleton.contains("<soap:Body"), "含 Body");
        assertTrue(skeleton.contains("GetNaviResponse"), "含响应元素");
        assertTrue(skeleton.contains("GetNaviResult"), "含 result 子元素");
        assertTrue(skeleton.contains("xmlns=\"http://tempuri.org/\""), "响应根元素带默认命名空间");
    }

    @Test
    void expandsComplexTypeReference() throws Exception {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:tns=\"http://tempuri.org/\" targetNamespace=\"http://tempuri.org/\">\n" +
                "  <types><xs:schema targetNamespace=\"http://tempuri.org/\" elementFormDefault=\"qualified\">\n" +
                "    <xs:complexType name=\"FtpFile\"><xs:sequence>\n" +
                "      <xs:element name=\"FileByte\" type=\"xs:base64Binary\" minOccurs=\"0\"/>\n" +
                "      <xs:element name=\"FileName\" type=\"xs:string\" minOccurs=\"0\"/>\n" +
                "    </xs:sequence></xs:complexType>\n" +
                "    <xs:element name=\"GetFileResponse\"><xs:complexType><xs:sequence>\n" +
                "      <xs:element name=\"GetFileResult\" type=\"tns:FtpFile\" minOccurs=\"0\"/>\n" +
                "    </xs:sequence></xs:complexType></xs:element>\n" +
                "  </xs:schema></types>\n" +
                "</definitions>";

        String skeleton = gen.generate(parse(wsdl), "GetFile", "http://tempuri.org/");

        assertNotNull(skeleton);
        assertTrue(skeleton.contains("GetFileResult"), "含 result");
        assertTrue(skeleton.contains("FileByte"), "递归展开 FtpFile.FileByte");
        assertTrue(skeleton.contains("FileName"), "递归展开 FtpFile.FileName");
    }

    @Test
    void unknownResponseElementReturnsNull() throws Exception {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://tempuri.org/\">\n" +
                "  <types><xs:schema targetNamespace=\"http://tempuri.org/\"></xs:schema></types>\n" +
                "</definitions>";

        String skeleton = gen.generate(parse(wsdl), "NotExist", "http://tempuri.org/");

        assertNull(skeleton);
    }

    @Test
    void cyclicTypeReferenceIsCappedByRecursionLimit() throws Exception {
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:tns=\"http://tempuri.org/\" targetNamespace=\"http://tempuri.org/\">\n" +
                "  <types><xs:schema targetNamespace=\"http://tempuri.org/\" elementFormDefault=\"qualified\">\n" +
                "    <xs:complexType name=\"Node\"><xs:sequence>\n" +
                "      <xs:element name=\"Child\" type=\"tns:Node\" minOccurs=\"0\"/>\n" +
                "    </xs:sequence></xs:complexType>\n" +
                "    <xs:element name=\"RootResponse\"><xs:complexType><xs:sequence>\n" +
                "      <xs:element name=\"RootResult\" type=\"tns:Node\" minOccurs=\"0\"/>\n" +
                "    </xs:sequence></xs:complexType></xs:element>\n" +
                "  </xs:schema></types>\n" +
                "</definitions>";

        String skeleton = gen.generate(parse(wsdl), "Root", "http://tempuri.org/");

        assertNotNull(skeleton);
        assertTrue(skeleton.contains("<Child>") || skeleton.contains("<Child/>"),
                "至少展开一层 Child");
        assertTrue(skeleton.length() < 50_000,
                "骨架长度应受递归深度上限控制，不应无限膨胀（实际长度=" + skeleton.length() + "）");
    }

    @Test
    void rejectsInvalidTagNameInResponseElement() throws Exception {
        // operationName 带特殊字符 → 拼出的 responseElementName 非合法 NCName → 返回 null
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://tempuri.org/\">\n" +
                "  <types><xs:schema targetNamespace=\"http://tempuri.org/\"></xs:schema></types>\n" +
                "</definitions>";

        // operationName 含 < → responseElementName=<EvilResponse 非合法 NCName
        String skeleton = gen.generate(parse(wsdl), "<Evil", "http://tempuri.org/");

        assertNull(skeleton);
    }

    @Test
    void skipsChildElementWithInvalidName() throws Exception {
        // 恶意 WSDL：子元素 name 含空格
        String wsdl = "<?xml version=\"1.0\"?>\n" +
                "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://tempuri.org/\">\n" +
                "  <types><xs:schema targetNamespace=\"http://tempuri.org/\" elementFormDefault=\"qualified\">\n" +
                "    <xs:element name=\"FooResponse\"><xs:complexType><xs:sequence>\n" +
                "      <xs:element name=\"Good\" type=\"xs:string\" minOccurs=\"0\"/>\n" +
                "      <xs:element name=\"bad name\" type=\"xs:string\" minOccurs=\"0\"/>\n" +
                "    </xs:sequence></xs:complexType></xs:element>\n" +
                "  </xs:schema></types>\n" +
                "</definitions>";

        String skeleton = gen.generate(parse(wsdl), "Foo", "http://tempuri.org/");

        assertNotNull(skeleton);
        assertTrue(skeleton.contains("<Good>"), "合法 name 保留");
        org.junit.jupiter.api.Assertions.assertFalse(skeleton.contains("bad name"),
                "非法 name（含空格）被跳过");
    }
}
