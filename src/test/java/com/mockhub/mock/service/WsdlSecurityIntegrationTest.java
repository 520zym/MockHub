package com.mockhub.mock.service;

import com.fasterxml.jackson.databind.*;
import com.mockhub.common.config.DataProperties;
import com.mockhub.common.model.*;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.log.service.LogService;
import com.mockhub.mock.controller.WsdlController;
import com.mockhub.mock.model.dto.*;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.repository.*;
import com.mockhub.mock.service.match.*;
import com.mockhub.system.repository.*;
import com.mockhub.system.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.sqlite.SQLiteDataSource;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real SQLite, Spring transactional upload proxy, true authorization, real temporary filesystem. */
class WsdlSecurityIntegrationTest {
    @TempDir Path temp;
    Path data;AnnotationConfigApplicationContext context;JdbcTemplate jdbc;SoapService soap;ApiServiceImpl api;ApiRepository apis;ApiResponseRepository responses;MockDispatchService dispatch;MockMvc mvc;
    final ObjectMapper json=new ObjectMapper();
    static final String WSDL="<?xml version=\"1.0\"?><definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" xmlns:soap=\"http://schemas.xmlsoap.org/wsdl/soap/\" targetNamespace=\"urn:test\"><binding name=\"B\"><operation name=\"Ping\"><soap:operation soapAction=\"urn:ping\"/></operation></binding><service name=\"S\"><port name=\"P\"><soap:address location=\"http://original/service\"/></port></service></definitions>";
    @BeforeEach void setup(){
        data=temp.resolve("data");SQLiteDataSource ds=new SQLiteDataSource();ds.setUrl("jdbc:sqlite:"+temp.resolve("wsdl.db"));new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);jdbc=new JdbcTemplate(ds);
        for(String id:Arrays.asList("editor","viewer","outsider","disabled","root"))jdbc.update("INSERT INTO user(id,username,password_hash,display_name,global_role,status,created_at,updated_at) VALUES(?,?,'hash',?,?,?,'before','before')",id,id,id,id.equals("root")?"SUPER_ADMIN":"USER",id.equals("disabled")?"DISABLED":"ACTIVE");
        jdbc.update("INSERT INTO team(id,name,identifier,created_at) VALUES('a','Alpha','AA','before'),('b','Beta','BB','before')");
        jdbc.update("INSERT INTO user_team VALUES('editor','a','EDITOR'),('viewer','a','VIEWER'),('disabled','a','EDITOR')");
        DataProperties props=new DataProperties();props.setPath(data.toString());context=new AnnotationConfigApplicationContext();context.registerBean(DataSource.class,()->ds);context.registerBean(JdbcTemplate.class,()->jdbc);context.registerBean(DataProperties.class,()->props);context.registerBean(ObjectMapper.class,()->json);context.register(Config.class);context.refresh();soap=context.getBean(SoapService.class);
        apis=new ApiRepository(jdbc);responses=new ApiResponseRepository(jdbc);MockFileStorageService files=new MockFileStorageService(data.toString());
        TeamService teams=context.getBean(TeamServiceImpl.class);PermissionChecker permission=context.getBean(PermissionChecker.class);
        api=new ApiServiceImpl(apis,responses,new ApiTagRepository(jdbc),new GroupRepository(jdbc),new TagRepository(jdbc),teams,permission,json,mock(LogService.class),context.getBean(UserRepository.class),files);ReflectionTestUtils.setField(api,"soapService",soap);
        dispatch=new MockDispatchService(teams,api,responses,mock(GlobalHeaderService.class),mock(LogService.class),json,mock(DynamicVariableResolver.class),mock(ResponseMatcher.class),soap,files);
        mvc=MockMvcBuilders.standaloneSetup(new WsdlController(soap)).setControllerAdvice(new GlobalExceptionHandler()).build();login("editor");
    }
    @AfterEach void close(){SecurityContextHolder.clearContext();context.close();}
    void login(String id){Map<String,Object>d=new HashMap<>();d.put("userId",id);d.put("username",id);d.put("globalRole",id.equals("root")?"SUPER_ADMIN":"USER");UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(id,null,Collections.emptyList());auth.setDetails(d);SecurityContextHolder.getContext().setAuthentication(auth);}
    MockMultipartFile file(String name,String body){return new MockMultipartFile("file",name,"text/xml",body.getBytes(StandardCharsets.UTF_8));}
    WsdlParseResult upload(String team,String name){return soap.uploadWsdl(team,file(name,WSDL));}
    long storedFiles()throws Exception{if(!Files.exists(data.resolve("wsdl")))return 0;try(java.util.stream.Stream<Path>s=Files.walk(data.resolve("wsdl"))){return s.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).count();}}
    ApiDefinitionDTO dto(String path,String key){ApiDefinitionDTO dto=new ApiDefinitionDTO();dto.setTeamId("a");dto.setName("SOAP");dto.setType("SOAP");dto.setMethod("POST");dto.setPath(path);dto.setEnabled(true);dto.setResponseBody("<ok/>");dto.setContentType("text/xml");Map<String,Object>config=new LinkedHashMap<>();if(key!=null)config.put("wsdlFileName",key);config.put("operations",Collections.emptyList());dto.setSoapConfig(config);return dto;}
    ApiDefinition legacy(String id,String team,String name)throws Exception{Files.createDirectories(data.resolve("wsdl"));Files.write(data.resolve("wsdl").resolve(name),WSDL.getBytes(StandardCharsets.UTF_8));jdbc.update("INSERT INTO api_definition(id,team_id,name,type,method,path,soap_config,created_by,created_at,updated_at) VALUES(?,?,'Legacy','SOAP','POST',?,?,'editor','before','before')",id,team,"/"+id,json.writeValueAsString(Collections.singletonMap("wsdlFileName",name)));return apis.findById(id);}
    @Test void viewerNonMemberDisabledAndMissingOrArchivedTeamCannotUpload()throws Exception{
        assertTrue(AopUtils.isCglibProxy(soap));for(String id:Arrays.asList("viewer","outsider","disabled")){login(id);assertThrows(BizException.class,()->upload("a","same.wsdl"));}
        login("editor");assertThrows(BizException.class,()->upload(null,"same.wsdl"));assertThrows(BizException.class,()->upload("missing","same.wsdl"));assertThrows(BizException.class,()->upload("b","same.wsdl"));jdbc.update("UPDATE team SET status='ARCHIVED' WHERE id='a'");assertThrows(BizException.class,()->upload("a","same.wsdl"));assertEquals(0,storedFiles());
    }
    @Test void maliciousOriginalNamesNeverBecomeWriteTargetsAndSameNameNeverOverwrites()throws Exception{
        Path sentinel=temp.resolve("sentinel.wsdl");Files.write(sentinel,"EXTERNAL_SENTINEL".getBytes(StandardCharsets.UTF_8));
        WsdlParseResult first=upload("a","../../sentinel.wsdl"),second=upload("a",sentinel.toString());assertEquals("EXTERNAL_SENTINEL",new String(Files.readAllBytes(sentinel),StandardCharsets.UTF_8));assertNotEquals(first.getFileName(),second.getFileName());assertEquals(first.getFileName(),first.getWsdlFileName());assertEquals("sentinel.wsdl",first.getOriginalFileName());assertTrue(first.getFileName().matches("v2/[0-9a-f]{64}/[0-9a-f-]{36}[.]wsdl"));
        WsdlParseResult same1=upload("a","same.wsdl"),same2=upload("a","same.wsdl");assertNotEquals(same1.getFileName(),same2.getFileName());assertEquals(4,storedFiles());assertArrayEquals(WSDL.getBytes(StandardCharsets.UTF_8),Files.readAllBytes(data.resolve("wsdl").resolve(first.getFileName())));
    }
    @Test void invalidXmlDtdOversizeAndDeepXmlCannotReplaceOrLeaveFiles()throws Exception{
        WsdlParseResult safe=upload("a","same.wsdl");Path owned=data.resolve("wsdl").resolve(safe.getFileName());byte[] before=Files.readAllBytes(owned);
        for(String body:Arrays.asList("not xml","<definitions><unclosed>","<!DOCTYPE definitions [<!ENTITY secret SYSTEM 'file:///never-read'>]><definitions>&secret;</definitions>","<not-wsdl/>"))assertThrows(BizException.class,()->soap.uploadWsdl("a",file(safe.getFileName(),body)));
        char[] large=new char[SoapService.MAX_WSDL_BYTES+1];Arrays.fill(large,'x');assertThrows(BizException.class,()->soap.uploadWsdl("a",file("big.wsdl",new String(large))));
        StringBuilder deep=new StringBuilder("<definitions>");for(int i=0;i<130;i++)deep.append("<x>");for(int i=0;i<130;i++)deep.append("</x>");deep.append("</definitions>");assertThrows(BizException.class,()->soap.uploadWsdl("a",file("deep.wsdl",deep.toString())));
        assertEquals(1,storedFiles());assertArrayEquals(before,Files.readAllBytes(owned));
    }
    @Test void managedUploadRequiresTeamAndQueryOperationsHandlesSafeNestedKeys()throws Exception{
        String body=mvc.perform(multipart("/api/soap/wsdl/upload").file(file("display.wsdl",WSDL)).param("teamId","a")).andReturn().getResponse().getContentAsString();JsonNode parsed=json.readTree(body);assertEquals(0,parsed.path("code").asInt());String key=parsed.path("data").path("fileName").asText();assertEquals(key,parsed.path("data").path("wsdlFileName").asText());
        login("viewer");JsonNode operations=json.readTree(mvc.perform(get("/api/soap/wsdl/operations").param("teamId","a").param("fileName",key)).andReturn().getResponse().getContentAsString());assertEquals(0,operations.path("code").asInt());assertEquals(1,operations.path("data").path("operations").size());
        long files=storedFiles();mvc.perform(multipart("/api/soap/wsdl/upload").file(file("no-team.wsdl",WSDL)));assertEquals(files,storedFiles());
    }
    @Test void publicWsdlUsesResolvedTeamAndArchiveDoesNotStopExistingMockRead()throws Exception{
        String key=upload("a","same.wsdl").getFileName();ApiDefinition actual=api.create(dto("/soap",key));SecurityContextHolder.clearContext();MockHttpServletRequest request=new MockHttpServletRequest("GET","/mock/AA/soap");request.setServerName("mock.local");request.setServerPort(8080);assertTrue(dispatch.serveWsdl("AA","/soap",request).getBody().toString().contains("http://mock.local:8080/mock/AA/soap"));
        jdbc.update("UPDATE team SET status='ARCHIVED' WHERE id='a'");assertEquals(200,dispatch.serveWsdl("AA","/soap",request).getStatusCodeValue());login("editor");assertThrows(BizException.class,()->upload("a","new.wsdl"));jdbc.update("UPDATE team SET status='ACTIVE' WHERE id='a'");login("root");String foreign=upload("b","foreign.wsdl").getFileName();jdbc.update("UPDATE api_definition SET soap_config=? WHERE id=?",json.writeValueAsString(Collections.singletonMap("wsdlFileName",foreign)),actual.getId());SecurityContextHolder.clearContext();assertThrows(BizException.class,()->dispatch.serveWsdl("AA","/soap",request));
        for(String unsafe:Arrays.asList("../../sentinel.wsdl",temp.resolve("outside.wsdl").toString(),"v2/"+SoapService.teamHash("b")+"/00000000-0000-0000-0000-000000000000.wsdl"))assertThrows(BizException.class,()->soap.getWsdlContent("a",unsafe,"http://mock"));
    }
    @Test void fileAndParentSymlinksCannotEscapeStorageForReadOrUpload()throws Exception{
        String key=upload("a","safe.wsdl").getFileName();Path owned=data.resolve("wsdl").resolve(key),outside=temp.resolve("outside.wsdl");Files.write(outside,"SECRET_SENTINEL".getBytes(StandardCharsets.UTF_8));Files.delete(owned);Files.createSymbolicLink(owned,outside);assertThrows(BizException.class,()->soap.getWsdlContent("a",key,"http://mock"));assertEquals("SECRET_SENTINEL",new String(Files.readAllBytes(outside),StandardCharsets.UTF_8));
        Path root=data.resolve("wsdl"),moved=data.resolve("wsdl-retained");Files.move(root,moved);Path external=temp.resolve("external");Files.createDirectories(external);Files.createSymbolicLink(root,external);assertThrows(BizException.class,()->upload("a","bad.wsdl"));assertThrows(BizException.class,()->soap.getWsdlContent("a",key,"http://mock"));try(java.util.stream.Stream<Path>s=Files.walk(external)){assertEquals(1,s.count());}
    }
    @Test void apiCreateUpdateAndRollbackCannotReferenceOtherTeamOrUnsafePaths()throws Exception{
        String own=upload("a","own.wsdl").getFileName();login("root");String foreign=upload("b","other.wsdl").getFileName();login("editor");for(String invalid:Arrays.asList(foreign,"../other.wsdl",temp.resolve("absolute.wsdl").toString(),"unowned.wsdl"))assertThrows(BizException.class,()->api.create(dto("/new",invalid)));assertEquals(0,apis.countByTeamId("a"));
        ApiDefinition actual=api.create(dto("/valid",own));ApiDefinitionDTO update=dto("/valid",foreign);update.setExpectedRevision(1L);assertThrows(BizException.class,()->api.update(actual.getId(),update));assertEquals(1,apis.findById(actual.getId()).getRevision());assertTrue(apis.findById(actual.getId()).getSoapConfig().contains(own));
        ApiDefinition manual=api.create(dto("/manual",null));assertNotNull(manual);assertEquals(1,api.revisions(actual.getId()).size());
    }
    @Test void externalApiJsonCannotSmuggleForeignWsdlReference()throws Exception{
        login("root");String foreign=upload("b","other.wsdl").getFileName();login("editor");
        org.springframework.test.web.servlet.MockMvc apiMvc=MockMvcBuilders.standaloneSetup(new com.mockhub.mock.controller.ApiController(api,new MockFileStorageService(data.toString()),context.getBean(PermissionChecker.class))).setControllerAdvice(new GlobalExceptionHandler()).build();
        String response=apiMvc.perform(post("/api/apis").contentType("application/json").content(json.writeValueAsString(dto("/json",foreign)))).andReturn().getResponse().getContentAsString();
        assertEquals(40702,json.readTree(response).path("code").asInt());assertEquals(0,apis.countByTeamId("a"));
    }
    @Test void importCannotUseForeignWsdlAndRollsBackAllDatabaseChanges()throws Exception{
        login("root");String foreign=upload("b","other.wsdl").getFileName();login("editor");
        ImportExportService importer=new ImportExportService(apis,responses,new GroupRepository(jdbc),new TagRepository(jdbc),new ApiTagRepository(jdbc),new GlobalHeaderRepository(jdbc),context.getBean(TeamServiceImpl.class),context.getBean(PermissionChecker.class),new MockFileStorageService(data.toString()),api);
        ImportExportData bundle=new ImportExportData();ApiDefinition source=new ApiDefinition();source.setId("foreign");source.setTeamId("b");source.setName("Imported");source.setType("SOAP");source.setMethod("POST");source.setPath("/imported");source.setContentType("text/xml");source.setResponseBody("<ok/>");source.setSoapConfig(json.writeValueAsString(Collections.singletonMap("wsdlFileName",foreign)));bundle.setApis(Collections.singletonList(source));
        assertEquals(40702,assertThrows(BizException.class,()->importer.importApis("a",bundle,"merge")).getCode());assertEquals(0,apis.countByTeamId("a"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM api_revision",Integer.class));
    }

    @Test void legacyBasenameNeedsExactSameTeamSoapFieldNotBodyOrSnapshotSubstring()throws Exception{
        Files.createDirectories(data.resolve("wsdl"));Files.write(data.resolve("wsdl/body-only.wsdl"),WSDL.getBytes(StandardCharsets.UTF_8));ApiDefinitionDTO fake=dto("/fake",null);fake.setResponseBody("{\"wsdlFileName\":\"body-only.wsdl\"}");ApiDefinition body=api.create(fake);assertThrows(BizException.class,()->soap.assertReference("a","body-only.wsdl"));assertThrows(BizException.class,()->api.create(dto("/steal","body-only.wsdl")));
        legacy("old-b","b","only-b.wsdl");assertThrows(BizException.class,()->soap.assertReference("a","only-b.wsdl"));assertFalse(context.getBean(WsdlReferenceRepository.class).hasLegacyReference("a","body-only.wsdl"));assertTrue(api.revisions(body.getId()).size()>0);
        jdbc.update("UPDATE api_definition SET soap_config=? WHERE id=?",json.writeValueAsString("{\"wsdlFileName\":\"body-only.wsdl\"}"),body.getId());
        assertFalse(context.getBean(WsdlReferenceRepository.class).hasLegacyReference("a","body-only.wsdl"));
    }
    @Test void legacyCurrentTrashAndRetainedConfigSnapshotRemainReadableAndRollbackKeepsCreator()throws Exception{
        ApiDefinition old=legacy("legacy-a","a","old.wsdl");apis.write(()->{apis.appendRevision(old.getId(),"BASELINE","MIGRATION",null);return null;});String baseline=api.revisions(old.getId()).get(0).get("id").toString();assertTrue(soap.getWsdlContent("a","old.wsdl","http://mock").contains("http://mock"));
        ApiDefinitionDTO current=dto("/legacy-a",null);current.setExpectedRevision(1L);api.update(old.getId(),current);assertTrue(context.getBean(WsdlReferenceRepository.class).hasLegacyReference("a","old.wsdl"));assertNotNull(soap.parseOperations("a","old.wsdl"));
        ApiDefinition restored=api.rollback(old.getId(),baseline,2L);assertTrue(restored.getSoapConfig().contains("old.wsdl"));assertEquals("editor",restored.getCreatedBy());api.delete(old.getId(),3L);assertTrue(soap.getWsdlContent("a","old.wsdl","http://mock").contains("http://mock"));ApiDefinition recovered=api.restore(old.getId(),4L,null);assertEquals("editor",recovered.getCreatedBy());assertTrue(recovered.getSoapConfig().contains("old.wsdl"));
    }
    @Test void randomizedUploadedVersionsRemainAvailableForRealHistoryRollback()throws Exception{
        String first=upload("a","same.wsdl").getFileName();ApiDefinition actual=api.create(dto("/versioned",first));String version=api.revisions(actual.getId()).get(0).get("id").toString();
        String second=upload("a","same.wsdl").getFileName();ApiDefinitionDTO change=dto("/versioned",second);change.setExpectedRevision(1L);api.update(actual.getId(),change);
        ApiDefinition rolled=api.rollback(actual.getId(),version,2L);assertTrue(rolled.getSoapConfig().contains(first));assertEquals(2,storedFiles());assertTrue(Files.isRegularFile(data.resolve("wsdl").resolve(second)));assertEquals("editor",rolled.getCreatedBy());
    }

    @Test void unsafeHistoricalWsdlReferenceCannotBeRestoredEvenIfLegacyRowAlreadyContainsIt()throws Exception{
        ApiDefinition old=legacy("unsafe-a","a","ok.wsdl");jdbc.update("UPDATE api_definition SET soap_config=? WHERE id=?","{\"wsdlFileName\":\"../../sentinel.wsdl\"}",old.getId());apis.write(()->{apis.appendRevision(old.getId(),"BASELINE","MIGRATION",null);return null;});String unsafe=api.revisions(old.getId()).get(0).get("id").toString();ApiDefinitionDTO fixed=dto("/unsafe-a",null);fixed.setExpectedRevision(1L);api.update(old.getId(),fixed);assertEquals(40702,assertThrows(BizException.class,()->api.rollback(old.getId(),unsafe,2L)).getCode());assertEquals(2,apis.findById(old.getId()).getRevision());assertEquals(2,api.revisions(old.getId()).size());
    }
    @Configuration @EnableTransactionManagement(proxyTargetClass=true)
    @Import({SoapService.class,SoapSkeletonGenerator.class,WsdlReferenceRepository.class,TeamRepository.class,UserRepository.class,PermissionCheckerImpl.class,AdminSafetyService.class,AdminSafetyRepository.class,TeamServiceImpl.class})
    static class Config{@Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}}
}
