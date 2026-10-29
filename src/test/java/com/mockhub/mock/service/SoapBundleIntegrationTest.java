package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mockhub.common.config.DataProperties;
import com.mockhub.common.model.BizException;
import com.mockhub.common.model.GlobalExceptionHandler;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.controller.ImportExportController;
import com.mockhub.mock.controller.MockDispatchController;
import com.mockhub.mock.controller.WsdlController;
import com.mockhub.mock.model.dto.*;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.repository.*;
import com.mockhub.mock.service.match.ResponseMatcher;
import com.mockhub.system.repository.*;
import com.mockhub.system.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real SQLite + transactional secure storage + actual export/import ZIP controllers; no server or network. */
class SoapBundleIntegrationTest {
    @TempDir Path temp;
    private Path data;
    private SQLiteDataSource ds;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private SoapService soap;
    private ApiServiceImpl api;
    private ApiRepository apis;
    private ApiResponseRepository responses;
    private MockFileStorageService files;
    private ImportExportService bundles;
    private ImportExportController controller;
    private MockMvc mvc;
    // Match the Spring MVC application mapper defaults, including forward-compatible SOAP metadata reads.
    private final ObjectMapper json=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
    private static final String WSDL="<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" targetNamespace=\"urn:test\"><portType name=\"P\"><operation name=\"Ping\"><documentation>Ping documentation</documentation></operation></portType><binding name=\"B\"><operation name=\"Ping\"><soap:operation soapAction=\"urn:ping\"/></operation><operation name=\"Pong\"><soap:operation soapAction=\"urn:pong\"/></operation></binding><service name=\"S\"><port name=\"P\"><soap:address location=\"http://original/service?a=1&amp;b=2\"/></port></service></definitions>";

    @BeforeEach void setup() throws Exception {
        data=temp.resolve("data");ds=new SQLiteDataSource();ds.setUrl("jdbc:sqlite:"+temp.resolve("bundle.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);jdbc=new JdbcTemplate(ds);
        java.lang.reflect.Method migration=com.mockhub.common.config.DataSourceConfig.class.getDeclaredMethod("executeMigrations",DataSource.class);
        migration.setAccessible(true);migration.invoke(new com.mockhub.common.config.DataSourceConfig(),ds);
        for(String id:Arrays.asList("admin","viewer"))jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,created_at,updated_at) VALUES(?,?,'hash',?,'USER','before','before')",id,id,id);
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('a','Alpha','AA','before'),('b','Beta','BB','before')");
        jdbc.update("INSERT INTO user_team VALUES('admin','a','TEAM_ADMIN'),('admin','b','TEAM_ADMIN'),('viewer','a','VIEWER'),('viewer','b','VIEWER')");
        jdbc.update("INSERT INTO api_group(id,team_id,name,created_at) VALUES('group-a','a','Group','before')");
        jdbc.update("INSERT INTO tag(id,team_id,name,color) VALUES('tag-a','a','Tagged','#123456')");
        jdbc.update("INSERT INTO global_header(id,team_id,header_name,header_value,enabled,sort_order) VALUES('header-a','a','X-Shared','source',1,0),('header-b','b','X-Shared','target',1,0)");
        DataProperties props=new DataProperties();props.setPath(data.toString());context=new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class,()->ds);context.registerBean(JdbcTemplate.class,()->jdbc);
        context.registerBean(DataProperties.class,()->props);context.registerBean(ObjectMapper.class,()->json);context.register(Config.class);context.refresh();
        soap=context.getBean(SoapService.class);apis=new ApiRepository(jdbc);responses=new ApiResponseRepository(jdbc);
        files=new MockFileStorageService(data.toString(),20*1024*1024L,new MockBlobRepository(jdbc));
        TeamService teams=context.getBean(TeamServiceImpl.class);PermissionChecker permission=context.getBean(PermissionChecker.class);
        api=new ApiServiceImpl(apis,responses,new ApiTagRepository(jdbc),new GroupRepository(jdbc),new TagRepository(jdbc),teams,permission,json,mock(LogService.class),context.getBean(UserRepository.class),files);
        ReflectionTestUtils.setField(api,"soapService",soap);
        bundles=new ImportExportService(apis,responses,new GroupRepository(jdbc),new TagRepository(jdbc),new ApiTagRepository(jdbc),new GlobalHeaderRepository(jdbc),teams,permission,files,api);
        ReflectionTestUtils.setField(bundles,"soapService",soap);controller=new ImportExportController(bundles,json);
        MockDispatchService dispatch=new MockDispatchService(teams,api,responses,mock(GlobalHeaderService.class),mock(LogService.class),json,mock(DynamicVariableResolver.class),mock(ResponseMatcher.class),soap,files);
        mvc=MockMvcBuilders.standaloneSetup(controller,new WsdlController(soap),new MockDispatchController(dispatch)).setControllerAdvice(new GlobalExceptionHandler()).build();login("admin");
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();if(context!=null)context.close();}
    private void login(String id){Map<String,Object> details=new HashMap<>();details.put("userId",id);details.put("username",id);details.put("globalRole","USER");UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(id,null,Collections.emptyList());auth.setDetails(details);SecurityContextHolder.getContext().setAuthentication(auth);}
    private String upload(String team){return soap.uploadWsdl(team,new MockMultipartFile("file","display.wsdl","text/xml",WSDL.getBytes(StandardCharsets.UTF_8))).getFileName();}
    private ApiDefinitionDTO dto(String team,String path,String key) throws Exception {
        ApiDefinitionDTO dto=new ApiDefinitionDTO();dto.setTeamId(team);dto.setName("Portable SOAP");dto.setDescription("API description");dto.setType("SOAP");dto.setMethod("POST");dto.setPath(path);dto.setEnabled(true);dto.setContentType("text/xml");dto.setResponseBody("<legacy/>");dto.setDelayMs(7);
        ObjectNode config=(ObjectNode)json.readTree("{\"operations\":[{\"operationName\":\"Ping\",\"soapAction\":\"urn:ping\",\"responseMode\":\"CONDITION\",\"description\":\"operation description\",\"future\":{\"opaque\":[1,2]}},{\"operationName\":\"Pong\",\"soapAction\":\"urn:pong\",\"responseMode\":\"RANDOM\"}],\"unknownExtension\":{\"keep\":true},\"namespace\":\"urn:test\"}");
        if(key!=null)config.put("wsdlFileName",key);dto.setSoapConfig(config);dto.setGlobalHeaderOverrides(Collections.singletonMap("X-Api","api-owned"));
        ApiResponseDTO rule=response("Ping","Rule","<rule/>");rule.setConditions("{\"conditions\":[{\"source\":\"QUERY\",\"path\":\"kind\",\"operator\":\"EQ\",\"value\":\"special\",\"valueType\":\"STRING\"}]}");
        dto.setResponses(Arrays.asList(rule,response("Ping","Default","<default/>"),response("Pong","Random one","<one/>"),response("Pong","Random two","<two/>")));
        return dto;
    }
    private ApiResponseDTO response(String operation,String name,String body){ApiResponseDTO r=new ApiResponseDTO();r.setSoapOperationName(operation);r.setName(name);r.setResponseBody(body);r.setContentType("text/xml");r.setResponseCode(201);r.setDelayMs(5);r.setSortOrder(3);r.setActive(true);return r;}
    private ApiDefinition source(String path,String key,boolean attachment) throws Exception {
        ApiDefinitionDTO dto=dto("a",path,key);dto.setTagIds(Collections.singletonList("tag-a"));dto.setGroupId("group-a");
        if(attachment){MockFileStorageService.StoredMockFile stored=files.store("a","payload.bin","application/octet-stream",4,new ByteArrayInputStream(new byte[]{0,1,2,3}));ApiResponseDTO r=response("Ping","Attachment",null);r.setActive(false);r.setBodyType("FILE");r.setFileName(stored.getFileName());r.setFilePath(stored.getFilePath());r.setFileSize(stored.getFileSize());r.setContentType("application/octet-stream");r.setDownloadName("download.bin");List<ApiResponseDTO> all=new ArrayList<>(dto.getResponses());all.add(r);dto.setResponses(all);}
        return api.create(dto);
    }
    private byte[] exportZip(String... ids) throws Exception {org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=get("/api/apis/export").param("teamId","a");if(ids.length>0)request.param("ids",ids);org.springframework.mock.web.MockHttpServletResponse result=mvc.perform(request).andReturn().getResponse();assertEquals(200,result.getStatus());assertTrue(result.getContentType().startsWith("application/zip"));return result.getContentAsByteArray();}
    private JsonNode importZip(byte[] bytes,String team,String mode,String expected) throws Exception {org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=multipart("/api/apis/import").file(new MockMultipartFile("file","bundle.zip","application/zip",bytes)).param("teamId",team).param("mode",mode);if(expected!=null)request.param("expectedRevisions",expected);return json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsString());}
    private Map<String,byte[]> unzip(byte[] bytes) throws Exception {Map<String,byte[]> entries=new LinkedHashMap<>();try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=in.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);entries.put(e.getName(),out.toByteArray());}}return entries;}
    private byte[] zip(Map<String,byte[]> entries) throws Exception {ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream out=new ZipOutputStream(bytes)){for(Map.Entry<String,byte[]> e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}return bytes.toByteArray();}
    private ImportExportData manifest(Map<String,byte[]> entries) throws Exception {return json.readValue(entries.get("mockhub-export.json"),ImportExportData.class);}
    private Map<String,byte[]> storedWsdls() throws Exception {Map<String,byte[]> result=new TreeMap<>();Path root=data.resolve("wsdl");if(Files.exists(root))try(java.util.stream.Stream<Path> stream=Files.walk(root)){for(Iterator<Path> it=stream.iterator();it.hasNext();){Path path=it.next();if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))result.put(root.relativize(path).toString(),Files.readAllBytes(path));}}return result;}
    private void assertWsdlsUnchanged(Map<String,byte[]> before) throws Exception {Map<String,byte[]> after=storedWsdls();assertEquals(before.keySet(),after.keySet());for(String key:before.keySet())assertArrayEquals(before.get(key),after.get(key),key);}
    private String key(ApiDefinition actual) throws Exception {return json.readTree(actual.getSoapConfig()).path("wsdlFileName").asText();}
    private Map<String,Object> targetState(){Map<String,Object> state=new LinkedHashMap<>();for(String table:Arrays.asList("api_definition","api_revision","api_group","tag","global_header","operation_log"))state.put(table,jdbc.queryForList("SELECT * FROM "+table+" WHERE team_id='b' ORDER BY id"));state.put("responses",jdbc.queryForList("SELECT r.* FROM api_response r JOIN api_definition a ON r.api_id=a.id WHERE a.team_id='b' ORDER BY r.id"));state.put("tags",jdbc.queryForList("SELECT t.* FROM api_tag t JOIN api_definition a ON t.api_id=a.id WHERE a.team_id='b' ORDER BY t.api_id,t.tag_id"));return state;}

    @Test void controllerZipRoundTripPreservesTheWholeAggregateAndRawWsdlBytes() throws Exception {
        String original=upload("a");ApiDefinition source=source("/soap",original,true);Map<String,byte[]> entries=unzip(exportZip());ImportExportData pack=manifest(entries);
        assertEquals("2.3",pack.getVersion());String entry=pack.getApiWsdlFiles().get(source.getId());assertTrue(entry.startsWith("wsdl/"));assertArrayEquals(WSDL.getBytes(StandardCharsets.UTF_8),entries.get("files/"+entry));assertEquals(original,key(pack.getApis().get(0)));
        JsonNode result=importZip(zip(entries),"b","merge",null);assertEquals(0,result.path("code").asInt(),result.toString());assertEquals(1,result.path("data").path("imported").asInt());
        ApiDefinition target=apis.findByTeamId("b").get(0);String owned=key(target);assertNotEquals(original,owned);assertTrue(owned.startsWith("v2/"+SoapService.teamHash("b")+"/"));assertArrayEquals(WSDL.getBytes(StandardCharsets.UTF_8),Files.readAllBytes(data.resolve("wsdl").resolve(owned)));
        ObjectNode expected=(ObjectNode)json.readTree(source.getSoapConfig());expected.put("wsdlFileName",owned);assertEquals(expected,json.readTree(target.getSoapConfig()));assertEquals(source.getDescription(),target.getDescription());assertEquals(source.getGlobalHeaderOverrides(),target.getGlobalHeaderOverrides());assertEquals(7,target.getDelayMs());assertEquals(1,target.getRevision());
        List<ApiResponse> actual=responses.findByApiId(target.getId());assertEquals(5,actual.size());assertTrue(actual.stream().anyMatch(r->r.getConditions()!=null&&r.getConditions().contains("special")));assertEquals(2,actual.stream().filter(r->"Pong".equals(r.getSoapOperationName())).count());
        for(ApiResponse r:actual){assertEquals(201,r.getResponseCode());assertEquals(5,r.getDelayMs());assertEquals(3,r.getSortOrder());}
        ApiResponse file=actual.stream().filter(r->"FILE".equals(r.getBodyType())).findFirst().get();assertTrue(file.getFilePath().startsWith("b/"));assertEquals("download.bin",file.getDownloadName());assertFalse(file.isActive());try(InputStream in=files.loadAsResource(file.getFilePath()).getInputStream()){assertEquals(0,in.read());assertEquals(1,in.read());assertEquals(2,in.read());assertEquals(3,in.read());assertEquals(-1,in.read());}
        List<String> tags=new ApiTagRepository(jdbc).findTagIdsByApiId(target.getId());assertEquals(1,tags.size());assertNotEquals("tag-a",tags.get(0));assertEquals("Tagged",new TagRepository(jdbc).findById(tags.get(0)).getName());assertEquals("Group",new GroupRepository(jdbc).findById(target.getGroupId()).getName());assertEquals("target",jdbc.queryForObject("SELECT header_value FROM global_header WHERE team_id='b'",String.class));assertEquals("source",jdbc.queryForObject("SELECT header_value FROM global_header WHERE team_id='a'",String.class));assertEquals("IMPORT",api.revisions(target.getId()).get(0).get("source"));
        assertEquals(original,key(apis.findById(source.getId())));assertArrayEquals(WSDL.getBytes(StandardCharsets.UTF_8),Files.readAllBytes(data.resolve("wsdl").resolve(original)));
        SecurityContextHolder.clearContext();String served=mvc.perform(get("/mock/BB/soap?wsdl")).andReturn().getResponse().getContentAsString();assertTrue(served.contains("http://localhost/mock/BB/soap"),served);assertTrue(served.contains("urn:pong"));assertFalse(served.contains("http://original/service"));login("viewer");JsonNode parsed=json.readTree(mvc.perform(get("/api/soap/wsdl/operations").param("teamId","b").param("fileName",owned)).andReturn().getResponse().getContentAsString());assertEquals(0,parsed.path("code").asInt());assertEquals(2,parsed.path("data").path("operations").size());assertEquals("Ping documentation",parsed.path("data").path("operations").get(0).path("description").asText());
    }
    @Test void sharedSourceWsdlIsDeduplicatedInZipAndTargetStorage() throws Exception {
        String original=upload("a");ApiDefinition one=source("/one",original,false),two=source("/two",original,false);Map<String,byte[]> entries=unzip(exportZip());ImportExportData pack=manifest(entries);assertEquals(pack.getApiWsdlFiles().get(one.getId()),pack.getApiWsdlFiles().get(two.getId()));assertEquals(2,entries.size());
        assertEquals(0,importZip(zip(entries),"b","merge",null).path("code").asInt());List<ApiDefinition> imported=apis.findByTeamId("b");assertEquals(2,imported.size());assertEquals(key(imported.get(0)),key(imported.get(1)));assertEquals(2,storedWsdls().size());
    }
    @Test void overrideUsesFrozenTargetRevisionsBeforeAnyDiskOrMetadataWrite() throws Exception {
        source("/soap",upload("a"),true);ApiDefinition target=api.create(dto("b","/soap",upload("b")));byte[] archive=exportZip();Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();
        assertEquals(40920,importZip(archive,"b","override",null).path("code").asInt());assertEquals(40921,importZip(archive,"b","override",json.writeValueAsString(Collections.singletonMap(target.getId(),2L))).path("code").asInt());assertEquals(before,targetState());assertWsdlsUnchanged(stored);
        assertEquals(0,importZip(archive,"b","override",json.writeValueAsString(Collections.singletonMap(target.getId(),1L))).path("code").asInt());assertEquals(2,apis.findById(target.getId()).getRevision());assertNotEquals(key(target),key(apis.findById(target.getId())));assertTrue(Files.exists(data.resolve("wsdl").resolve(key(target))),"old snapshot WSDL must remain intact");
    }
    @Test void laterHistoryFailureRollsBackAllAggregatesAndOnlyDeletesNewImportedKeys() throws Exception {
        String sourceKey=upload("a");ApiDefinition first=source("/existing",sourceKey,false),second=source("/new",sourceKey,false);String retained=upload("b");ApiDefinition target=api.create(dto("b","/existing",retained));ApiDefinitionDTO update=dto("b","/existing",upload("b"));update.setExpectedRevision(1L);api.update(target.getId(),update);
        byte[] archive=exportZip(first.getId(),second.getId());Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();jdbc.execute("CREATE TRIGGER reject_import_history BEFORE INSERT ON api_revision WHEN NEW.source='IMPORT' AND NEW.action='CREATE' BEGIN SELECT RAISE(ABORT,'later history failed'); END");
        assertThrows(DataAccessException.class,()->controller.importApis(new MockMultipartFile("file","bundle.zip","application/zip",archive),"b","override",json.writeValueAsString(Collections.singletonMap(target.getId(),2L))));assertEquals(before,targetState());assertWsdlsUnchanged(stored);assertTrue(Files.exists(data.resolve("wsdl").resolve(retained)));assertEquals(2,api.revisions(target.getId()).size());
    }
    @Test void enclosingTransactionRollbackAlsoRemovesFreshImportedWsdl() throws Exception {
        source("/soap",upload("a"),false);byte[] archive=exportZip();Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();new TransactionTemplate(new DataSourceTransactionManager(ds)).execute(status->{controller.importApis(new MockMultipartFile("file","bundle.zip","application/zip",archive),"b","merge",null);assertEquals(1,apis.countByTeamId("b"));status.setRollbackOnly();return null;});assertEquals(before,targetState());assertWsdlsUnchanged(stored);
    }
    @Test void missingMappedBytesOrManifestCannotSilentlyImportAnIncompleteNewPackage() throws Exception {
        source("/soap",upload("a"),false);Map<String,byte[]> entries=unzip(exportZip());ImportExportData pack=manifest(entries);Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();String entry=pack.getApiWsdlFiles().values().iterator().next();entries.remove("files/"+entry);JsonNode missing=importZip(zip(entries),"b","merge",null);assertEquals(40927,missing.path("code").asInt());assertTrue(missing.path("msg").asText().contains("WSDL"));assertEquals(before,targetState());assertWsdlsUnchanged(stored);
        pack.setApiWsdlFiles(null);entries.put("mockhub-export.json",json.writeValueAsBytes(pack));assertEquals(40927,importZip(zip(entries),"b","merge",null).path("code").asInt());assertEquals(before,targetState());assertWsdlsUnchanged(stored);
    }
    @Test void corruptDtdDeepWrongNamespaceAndOversizedWsdlCannotCreateFiles() throws Exception {
        source("/soap",upload("a"),false);Map<String,byte[]> entries=unzip(exportZip());String entry="files/"+manifest(entries).getApiWsdlFiles().values().iterator().next();Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();StringBuilder deep=new StringBuilder("<definitions>");for(int i=0;i<130;i++)deep.append("<x>");for(int i=0;i<130;i++)deep.append("</x>");deep.append("</definitions>");
        for(String invalid:Arrays.asList("not xml","<definitions><broken>","<!DOCTYPE definitions [<!ENTITY x SYSTEM 'file:///never-read'>]><definitions>&x;</definitions>","<definitions xmlns='urn:foreign'/>",deep.toString())){entries.put(entry,invalid.getBytes(StandardCharsets.UTF_8));assertEquals(40701,importZip(zip(entries),"b","merge",null).path("code").asInt());assertEquals(before,targetState());assertWsdlsUnchanged(stored);}
        entries.put(entry,new byte[SoapService.MAX_WSDL_BYTES+1]);assertEquals(40000,importZip(zip(entries),"b","merge",null).path("code").asInt());assertEquals(before,targetState());assertWsdlsUnchanged(stored);
    }
    @Test void oldJsonOwnKeyWorksButForeignAndTraversalKeysStillFail40702() throws Exception {
        String own=upload("a");source("/old",own,false);ImportExportData old=bundles.exportTeam("a");assertEquals("2.2",old.getVersion());assertNull(old.getApiWsdlFiles());old.getApis().get(0).setPath("/old-copy");assertEquals(1,bundles.importApis("a",old,"merge").getImported());assertEquals(own,key(apis.findByTeamIdAndPathAndMethod("a","/old-copy","POST").get(0)));
        Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();for(String invalid:Arrays.asList(own,"../../sentinel.wsdl",temp.resolve("external.wsdl").toString())){ObjectNode config=(ObjectNode)json.readTree(old.getApis().get(0).getSoapConfig());config.put("wsdlFileName",invalid);old.getApis().get(0).setSoapConfig(json.writeValueAsString(config));JsonNode result=json.readTree(mvc.perform(multipart("/api/apis/import").file(new MockMultipartFile("file","old.json","application/json",json.writeValueAsBytes(old))).param("teamId","b").param("mode","merge")).andReturn().getResponse().getContentAsString());assertEquals(40702,result.path("code").asInt());assertEquals(before,targetState());assertWsdlsUnchanged(stored);}
    }
    @Test void legacyOwnBasenameSurvivesJsonAndBecomesPortableInCompleteZip() throws Exception {
        Files.createDirectories(data.resolve("wsdl"));Files.write(data.resolve("wsdl/legacy own.wsdl"),WSDL.getBytes(StandardCharsets.UTF_8));jdbc.update("INSERT INTO api_definition(id,team_id,name,type,method,path,soap_config,enabled,created_at,updated_at) VALUES('legacy','a','Legacy','SOAP','POST','/legacy',?,1,'before','before')","{\"wsdlFileName\":\"legacy own.wsdl\",\"operations\":[]}");ImportExportData old=bundles.exportTeam("a");old.getApis().get(0).setPath("/legacy-copy");assertEquals(1,bundles.importApis("a",old,"merge").getImported());assertEquals("legacy own.wsdl",key(apis.findByTeamIdAndPathAndMethod("a","/legacy-copy","POST").get(0)));assertEquals(0,importZip(exportZip(),"b","merge",null).path("code").asInt());assertEquals(2,apis.countByTeamId("b"));assertArrayEquals(WSDL.getBytes(StandardCharsets.UTF_8),Files.readAllBytes(data.resolve("wsdl/legacy own.wsdl")));
    }
    @Test void viewerCanExportButViewerAndArchivedTeamCannotImport() throws Exception {
        source("/soap",upload("a"),false);login("viewer");byte[] archive=exportZip();Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();assertEquals(40104,importZip(archive,"b","merge",null).path("code").asInt());assertEquals(40101,importZip(archive,"b","override",null).path("code").asInt());login("admin");jdbc.update("UPDATE team SET status='ARCHIVED' WHERE id='b'");assertEquals(40903,importZip(archive,"b","merge",null).path("code").asInt());assertEquals(40903,importZip(archive,"b","override",null).path("code").asInt());assertEquals(before,targetState());assertWsdlsUnchanged(stored);
    }
    @Test void missingCorruptForeignAndSymlinkedSourceWsdlFailExport40927() throws Exception {
        String own=upload("a");ApiDefinition source=source("/soap",own,false);Path path=data.resolve("wsdl").resolve(own);Files.delete(path);assertEquals(40927,assertThrows(BizException.class,()->controller.exportApis("a",null)).getCode());Files.write(path,"<broken>".getBytes(StandardCharsets.UTF_8));assertEquals(40927,assertThrows(BizException.class,()->controller.exportApis("a",null)).getCode());Files.delete(path);Path outside=temp.resolve("outside.wsdl");Files.write(outside,WSDL.getBytes(StandardCharsets.UTF_8));Files.createSymbolicLink(path,outside);assertEquals(40927,assertThrows(BizException.class,()->controller.exportApis("a",null)).getCode());String foreign=upload("b");jdbc.update("UPDATE api_definition SET soap_config=? WHERE id=?",json.writeValueAsString(Collections.singletonMap("wsdlFileName",foreign)),source.getId());assertEquals(40927,assertThrows(BizException.class,()->controller.exportApis("a",null)).getCode());assertArrayEquals(WSDL.getBytes(StandardCharsets.UTF_8),Files.readAllBytes(outside));
    }
    @Test void maliciousManifestAndZipPathsAreRejectedWithoutWrites() throws Exception {
        source("/soap",upload("a"),false);Map<String,byte[]> entries=unzip(exportZip());ImportExportData pack=manifest(entries);Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();pack.getApiWsdlFiles().replaceAll((id,key)->"../../sentinel.wsdl");entries.put("mockhub-export.json",json.writeValueAsBytes(pack));assertEquals(40000,importZip(zip(entries),"b","merge",null).path("code").asInt());entries.put("files/../../sentinel.wsdl",WSDL.getBytes(StandardCharsets.UTF_8));assertEquals(40000,importZip(zip(entries),"b","merge",null).path("code").asInt());assertEquals(before,targetState());assertWsdlsUnchanged(stored);
    }
    @Test void wsdlEntriesShareTheExistingEntryCountAndExpandedTotalLimits() throws Exception {
        source("/soap",upload("a"),false);Map<String,byte[]> entries=unzip(exportZip());Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();ReflectionTestUtils.setField(controller,"maxImportBytes",2048L);entries.put("files/ignored",new byte[1800]);assertEquals(40000,importZip(zip(entries),"b","merge",null).path("code").asInt());ReflectionTestUtils.setField(controller,"maxImportBytes",104857600L);entries=unzip(exportZip());for(int i=0;i<1000;i++)entries.put("files/ignored-"+i,new byte[0]);assertEquals(40000,importZip(zip(entries),"b","merge",null).path("code").asInt());assertEquals(before,targetState());assertWsdlsUnchanged(stored);
    }

    @Test void validFirstAttachmentIsCleanedWhenALaterWsdlCannotBeParsed() throws Exception {
        ApiDefinition first=source("/first",upload("a"),false),second=source("/second",upload("a"),false);
        Map<String,byte[]> entries=unzip(exportZip(first.getId(),second.getId()));
        entries.put("files/"+manifest(entries).getApiWsdlFiles().get(second.getId()),"<broken>".getBytes(StandardCharsets.UTF_8));
        Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();
        assertEquals(40701,importZip(zip(entries),"b","merge",null).path("code").asInt());
        assertEquals(before,targetState());assertWsdlsUnchanged(stored);
    }
    @Test void targetTeamDirectorySymlinkCannotRedirectImportedWsdlWrites() throws Exception {
        source("/soap",upload("a"),false);byte[] archive=exportZip();
        Path external=temp.resolve("external");Files.createDirectories(external);
        Path targetDirectory=data.resolve("wsdl/v2").resolve(SoapService.teamHash("b"));Files.createSymbolicLink(targetDirectory,external);
        Map<String,Object> before=targetState();Map<String,byte[]> stored=storedWsdls();
        assertEquals(50001,importZip(archive,"b","merge",null).path("code").asInt());
        assertEquals(before,targetState());assertWsdlsUnchanged(stored);
        try(java.util.stream.Stream<Path> paths=Files.list(external)){assertEquals(0,paths.count());}
    }
    @Test void old22ZipKeepsOwnExistingWsdlAndStillImportsFileResponseBytes() throws Exception {
        String original=upload("a");source("/soap",original,true);Map<String,byte[]> entries=unzip(exportZip());ImportExportData pack=manifest(entries);
        String wsdlEntry=pack.getApiWsdlFiles().values().iterator().next();entries.remove("files/"+wsdlEntry);pack.setApiWsdlFiles(null);pack.setVersion("2.2");pack.getApis().get(0).setPath("/old-zip-copy");entries.put("mockhub-export.json",json.writeValueAsBytes(pack));
        assertEquals(0,importZip(zip(entries),"a","merge",null).path("code").asInt());
        ApiDefinition imported=apis.findByTeamIdAndPathAndMethod("a","/old-zip-copy","POST").get(0);assertEquals(original,key(imported));assertEquals(1,storedWsdls().size());
        ApiResponse attachment=responses.findByApiId(imported.getId()).stream().filter(response->"FILE".equals(response.getBodyType())).findFirst().get();
        assertTrue(attachment.getFilePath().startsWith("a/"));assertNotEquals(pack.getApiResponses().stream().filter(response->"FILE".equals(response.getBodyType())).findFirst().get().getFilePath(),attachment.getFilePath());
        try(InputStream in=files.loadAsResource(attachment.getFilePath()).getInputStream()){assertEquals(0,in.read());assertEquals(1,in.read());assertEquals(2,in.read());assertEquals(3,in.read());assertEquals(-1,in.read());}
    }

    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({SoapService.class,SoapSkeletonGenerator.class,WsdlReferenceRepository.class,TeamRepository.class,UserRepository.class,PermissionCheckerImpl.class,AdminSafetyService.class,AdminSafetyRepository.class,TeamServiceImpl.class})
    static class Config {@Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}}
}
