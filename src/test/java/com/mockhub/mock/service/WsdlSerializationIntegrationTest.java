package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.common.config.DataProperties;
import com.mockhub.common.model.BizException;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.controller.MockDispatchController;
import com.mockhub.mock.model.dto.ApiMatchResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.repository.WsdlReferenceRepository;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.repository.TeamRepository;
import com.mockhub.system.repository.UserRepository;
import com.mockhub.system.service.PermissionCheckerImpl;
import com.mockhub.system.service.TeamService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.sqlite.SQLiteDataSource;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Exercises standalone raw reads, safe DOM rewriting, immutable raw exports and the real HTTP charset contract. */
class WsdlSerializationIntegrationTest {
    static final String WSDL = "http://schemas.xmlsoap.org/wsdl/";
    static final String SOAP = "http://schemas.xmlsoap.org/wsdl/soap/";
    static final String SOAP12 = "http://schemas.xmlsoap.org/wsdl/soap12/";
    @TempDir Path temp;
    SoapService soap;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        SQLiteDataSource ds = new SQLiteDataSource(); ds.setUrl("jdbc:sqlite:" + temp.resolve("serialization.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('a','Team','AA','before')");
        jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES('u','u','hash','User','USER','before','before')");
        jdbc.update("INSERT INTO user_team VALUES('u','a','EDITOR')");
        TeamRepository teams = new TeamRepository(); ReflectionTestUtils.setField(teams, "jdbcTemplate", jdbc);
        UserRepository users = new UserRepository(); ReflectionTestUtils.setField(users, "jdbcTemplate", jdbc);
        PermissionCheckerImpl permission = new PermissionCheckerImpl();
        ReflectionTestUtils.setField(permission, "teamRepository", teams); ReflectionTestUtils.setField(permission, "userRepository", users);
        DataProperties props = new DataProperties(); props.setPath(temp.toString());
        soap = new SoapService(props, new SoapSkeletonGenerator());
        ReflectionTestUtils.setField(soap, "teamRepository", teams); ReflectionTestUtils.setField(soap, "permissionChecker", permission);
        ReflectionTestUtils.setField(soap, "references", new WsdlReferenceRepository(jdbc, json));
        Map<String,Object> details = new HashMap<>(); details.put("userId", "u"); details.put("username", "u"); details.put("globalRole", "USER");
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("u", null, Collections.emptyList());
        auth.setDetails(details); SecurityContextHolder.getContext().setAuthentication(auth);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void quotesWhitespacePrefixesDefaultNamespaceAndImportsAreXmlAware() throws Exception {
        String url = "https://mock.local/中文/😀?a=1&b=<x>&q=\"'&d=$1\\tail";
        for (String prefix : Arrays.asList("", "w:")) for (String quote : Arrays.asList("'", "\"")) {
            String source = wsdl(prefix, quote, "UTF-8"); byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
            String key = store(bytes); String output = soap.getWsdlContent("a", key, url);
            Document doc = parse(output.getBytes(StandardCharsets.UTF_8));
            assertEquals("UTF-8", doc.getXmlEncoding());
            assertEquals(url, address(doc, SOAP, "one").getAttribute("location"));
            assertEquals(url, address(doc, SOAP12, "two").getAttribute("location"));
            assertEquals("relative/dependency.wsdl?a=1&b=2", ((Element)doc.getElementsByTagNameNS(WSDL, "import").item(0)).getAttribute("location"));
            assertEquals("relative/types.xsd", ((Element)doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import").item(0)).getAttribute("schemaLocation"));
            assertEquals("urn:unicode", doc.getDocumentElement().getAttribute("targetNamespace"));
            assertEquals("中文 café 😀 & <literal>", doc.getElementsByTagNameNS(WSDL, "documentation").item(0).getTextContent());
            assertEquals("urn:ping", ((Element)doc.getElementsByTagNameNS(SOAP12, "operation").item(0)).getAttribute("soapAction"));
            assertTrue(output.contains("<!--keep-comment-->")); assertTrue(output.contains("<?business keep?>"));
            assertArrayEquals(bytes, soap.readWsdlForExport("a", key));
            assertArrayEquals(bytes, Files.readAllBytes(temp.resolve("wsdl").resolve(key)));
        }
    }

    @Test void acceptedUtf16AndDeclaredLegacyEncodingBecomeValidUtf8WithoutChangingRawExport() throws Exception {
        for (Charset charset : Arrays.asList(StandardCharsets.UTF_16, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE, Charset.forName("ISO-8859-1"))) {
            String source = wsdl("w:", "'", charset.name());
            if (charset.name().equals("ISO-8859-1")) source = source.replace("中文 café 😀", "café");
            byte[] bytes = source.getBytes(charset); String key = store(bytes);
            soap.assertReference("a", key);
            String output = soap.getWsdlContent("a", key, "https://mock.local/服务?x=1&y=2");
            Document actual = parse(output.getBytes(StandardCharsets.UTF_8));
            assertEquals("UTF-8", actual.getXmlEncoding()); assertFalse(output.contains("encoding=\"UTF-16"));
            assertEquals(parse(bytes).getElementsByTagNameNS(WSDL, "documentation").item(0).getTextContent(), actual.getElementsByTagNameNS(WSDL, "documentation").item(0).getTextContent());
            assertEquals("https://mock.local/服务?x=1&y=2", address(actual, SOAP12, "two").getAttribute("location"));
            assertArrayEquals(bytes, soap.readWsdlForExport("a", key));
        }
    }

    @Test void onlyCorrectNamespaceAndDirectDefinitionsServicePortStructureAreRewritten() throws Exception {
        String source = "<w:definitions xmlns:w='" + WSDL + "' xmlns:s='" + SOAP + "' xmlns:x='urn:extension'>"
                + "<w:binding><s:address id='binding' location='binding-original'/></w:binding>"
                + "<w:service><w:port><s:address id='valid' location='valid-original'/><x:address id='foreign' location='foreign-original'/>"
                + "<address id='unqualified' location='unqualified-original'/><w:documentation><s:address id='nested' location='nested-original'/></w:documentation><s:address id='absent'/></w:port>"
                + "<port><s:address id='wrong-port' location='wrong-port-original'/></port></w:service>"
                + "<x:service><w:port><s:address id='wrong-service' location='wrong-service-original'/></w:port></x:service>"
                + "<w:documentation><w:service><w:port><s:address id='deep' location='deep-original'/></w:port></w:service></w:documentation></w:definitions>";
        String output = soap.getWsdlContent("a", store(source.getBytes(StandardCharsets.UTF_8)), "https://mock");
        Document doc = parse(output.getBytes(StandardCharsets.UTF_8));
        assertEquals("https://mock", address(doc, SOAP, "valid").getAttribute("location"));
        for (String id : Arrays.asList("binding", "nested", "wrong-port", "wrong-service", "deep"))
            assertEquals(id + "-original", address(doc, SOAP, id).getAttribute("location"));
        assertEquals("foreign-original", address(doc, "urn:extension", "foreign").getAttribute("location"));
        assertEquals("unqualified-original", address(doc, null, "unqualified").getAttribute("location"));
        assertFalse(address(doc, SOAP, "absent").hasAttribute("location"));
        String legacy = "<definitions xmlns:s='" + SOAP + "'><service><port><s:address id='legacy' location='legacy-original'/></port></service></definitions>";
        Document unqualified = parse(soap.getWsdlContent("a", store(legacy.getBytes(StandardCharsets.UTF_8)), "https://mock").getBytes(StandardCharsets.UTF_8));
        assertEquals("legacy-original", address(unqualified, SOAP, "legacy").getAttribute("location"));
    }

    @Test void publicWsdlHttpBytesAndDeclarationBothUseUtf8ForUtf16Source() throws Exception {
        byte[] raw = wsdl("", "'", "UTF-16").getBytes(StandardCharsets.UTF_16); String key = store(raw);
        TeamService teams = mock(TeamService.class); Team team = new Team(); team.setId("a"); team.setIdentifier("AA"); when(teams.findByIdentifier("AA")).thenReturn(team);
        ApiService apis = mock(ApiService.class); ApiDefinition api = new ApiDefinition(); api.setType("SOAP"); api.setSoapConfig(json.writeValueAsString(Collections.singletonMap("wsdlFileName", key)));
        when(apis.findMatch("a", "POST", "/service")).thenReturn(new ApiMatchResult(api, Collections.emptyMap()));
        MockDispatchService dispatch = new MockDispatchService(teams, apis, mock(ApiResponseRepository.class), mock(GlobalHeaderService.class), mock(LogService.class), json,
                mock(DynamicVariableResolver.class), mock(ResponseMatcher.class), soap, new MockFileStorageService(temp.toString()));
        org.springframework.mock.web.MockHttpServletResponse response = MockMvcBuilders.standaloneSetup(new MockDispatchController(dispatch)).build()
                .perform(get("/mock/AA/service?wsdl")).andReturn().getResponse();
        assertEquals(200, response.getStatus()); assertEquals("text/xml;charset=UTF-8", response.getContentType().replace(" ", ""));
        Document doc = parse(response.getContentAsByteArray()); assertEquals("UTF-8", doc.getXmlEncoding());
        assertEquals("中文 café 😀 & <literal>", doc.getElementsByTagNameNS(WSDL, "documentation").item(0).getTextContent());
        assertEquals("http://localhost/mock/AA/service", address(doc, SOAP12, "two").getAttribute("location"));
        assertArrayEquals(raw, soap.readWsdlForExport("a", key));
    }

    @Test void standaloneReadRetainsExistingDtdDepthAndSizeGuards() throws Exception {
        String dtd = "<!DOCTYPE definitions [<!ENTITY x SYSTEM 'file:///never-read'>]><definitions>&x;</definitions>";
        StringBuilder deep = new StringBuilder("<definitions>"); for (int i=0;i<130;i++) deep.append("<x>"); for (int i=0;i<130;i++) deep.append("</x>"); deep.append("</definitions>");
        for (byte[] invalid : Arrays.asList(dtd.getBytes(StandardCharsets.UTF_8), deep.toString().getBytes(StandardCharsets.UTF_8), new byte[SoapService.MAX_WSDL_BYTES+1])) {
            String key = store(invalid); assertThrows(BizException.class, () -> soap.getWsdlContent("a", key, "https://mock"));
        }
    }

    private String store(byte[] bytes) throws Exception {
        String key = "v2/" + SoapService.teamHash("a") + "/" + UUID.randomUUID() + ".wsdl";
        Path path = temp.resolve("wsdl").resolve(key); Files.createDirectories(path.getParent()); Files.write(path, bytes); return key;
    }
    private static Document parse(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }
    private static Element address(Document doc, String ns, String id) {
        NodeList nodes = doc.getElementsByTagNameNS(ns, "address");
        for (int i=0;i<nodes.getLength();i++) if (id.equals(((Element)nodes.item(i)).getAttribute("id"))) return (Element) nodes.item(i);
        throw new AssertionError("Missing address " + id);
    }
    private static String wsdl(String prefix, String quote, String encoding) {
        String namespace = prefix.isEmpty() ? "xmlns='" + WSDL + "'" : "xmlns:w='" + WSDL + "'";
        return "<?xml version='1.0' encoding='" + encoding + "'?><?business keep?><" + prefix + "definitions " + namespace
                + " xmlns:s='" + SOAP + "' xmlns:q='" + SOAP12 + "' xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:unicode'>"
                + "<!--keep-comment--><" + prefix + "import namespace='urn:dependency' location='relative/dependency.wsdl?a=1&amp;b=2'/>"
                + "<" + prefix + "types><xs:schema><xs:import namespace='urn:types' schemaLocation='relative/types.xsd'/></xs:schema></" + prefix + "types>"
                + "<" + prefix + "documentation><![CDATA[中文 café 😀 & <literal>]]></" + prefix + "documentation>"
                + "<" + prefix + "binding name='B'><" + prefix + "operation name='Ping'><q:operation soapAction='urn:ping'/></" + prefix + "operation></" + prefix + "binding>"
                + "<" + prefix + "service name='S'><" + prefix + "port name='P'><s:address id='one' location \n = " + quote + "http://old/one" + quote + "/>"
                + "<q:address id='two' location\t=\n" + quote + "http://old/two" + quote + "/></" + prefix + "port></" + prefix + "service></" + prefix + "definitions>";
    }
}
