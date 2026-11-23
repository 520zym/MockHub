package com.mockhub.mock.service;

import com.mockhub.common.util.PermissionChecker;
import com.mockhub.common.util.SecurityContextUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockhub.mock.model.dto.ImportExportData;
import com.mockhub.mock.model.dto.ImportResult;
import com.mockhub.mock.model.entity.ApiDefinition;
import com.mockhub.mock.model.entity.ApiGroup;
import com.mockhub.mock.model.entity.ApiResponse;
import com.mockhub.mock.model.entity.GlobalHeader;
import com.mockhub.mock.model.entity.Tag;
import com.mockhub.mock.repository.ApiRepository;
import com.mockhub.mock.repository.ApiResponseRepository;
import com.mockhub.mock.repository.ApiTagRepository;
import com.mockhub.mock.repository.GlobalHeaderRepository;
import com.mockhub.mock.repository.GroupRepository;
import com.mockhub.mock.repository.TagRepository;
import com.mockhub.mock.service.match.ResponseValidator;
import com.mockhub.system.model.entity.Team;
import com.mockhub.system.service.TeamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 接口导入导出 Service
 * <p>
 * 导出：将团队的全部分组、标签、接口、全局响应头组装为 ImportExportData JSON。
 * 导入：根据合并（merge）或覆盖（override）模式将数据导入到目标团队。
 * <ul>
 *   <li>合并模式：同团队内 path+method 已存在的跳过，新接口追加</li>
 *   <li>覆盖模式：同团队内 path+method 已存在的覆盖，新接口追加</li>
 * </ul>
 */
@Service
public class ImportExportService {

    private static final Logger log = LoggerFactory.getLogger(ImportExportService.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final ApiRepository apiRepository;
    private final ApiResponseRepository apiResponseRepository;
    private final GroupRepository groupRepository;
    private final TagRepository tagRepository;
    private final ApiTagRepository apiTagRepository;
    private final GlobalHeaderRepository globalHeaderRepository;
    private final TeamService teamService;
    private final PermissionChecker permissionChecker;
    private final MockFileStorageService mockFileStorageService;
    private final ApiService apiService;
    @org.springframework.beans.factory.annotation.Autowired
    private SoapService soapService;

    public ImportExportService(ApiRepository apiRepository,
                               ApiResponseRepository apiResponseRepository,
                               GroupRepository groupRepository,
                               TagRepository tagRepository,
                               ApiTagRepository apiTagRepository,
                               GlobalHeaderRepository globalHeaderRepository,
                               TeamService teamService,
                               PermissionChecker permissionChecker,
                               MockFileStorageService mockFileStorageService,ApiService apiService) {
        this.apiService=apiService;
        this.apiRepository = apiRepository;
        this.apiResponseRepository = apiResponseRepository;
        this.groupRepository = groupRepository;
        this.tagRepository = tagRepository;
        this.apiTagRepository = apiTagRepository;
        this.globalHeaderRepository = globalHeaderRepository;
        this.teamService = teamService;
        this.permissionChecker = permissionChecker;
        this.mockFileStorageService = mockFileStorageService;
    }

    /**
     * 导出团队的全部数据
     *
     * @param teamId 团队 ID
     * @return 导出数据结构
     */
    public ImportExportData exportTeam(String teamId) {
        permissionChecker.checkTeamAccess(teamId);

        Team team = teamService.getById(teamId);

        ImportExportData data = new ImportExportData();
        data.setVersion("2.2");
        data.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        data.setTeamName(team != null ? team.getName() : "");
        data.setGroups(groupRepository.findByTeamId(teamId));
        data.setTags(tagRepository.findByTeamId(teamId));

        List<ApiDefinition> apis = apiRepository.findByTeamId(teamId);
        data.setApis(apis);

        // 导出所有接口的返回体
        List<ApiResponse> allResponses = new ArrayList<ApiResponse>();
        for (ApiDefinition api : apis) {
            allResponses.addAll(apiResponseRepository.findByApiId(api.getId()));
        }
        data.setApiResponses(allResponses);

        data.setGlobalHeaders(globalHeaderRepository.findByTeamId(teamId));
        fillTagMapping(data);

        log.info("导出团队数据: teamId={}, apis={}, responses={}, groups={}, tags={}, globalHeaders={}",
                teamId,
                data.getApis() != null ? data.getApis().size() : 0,
                allResponses.size(),
                data.getGroups() != null ? data.getGroups().size() : 0,
                data.getTags() != null ? data.getTags().size() : 0,
                data.getGlobalHeaders() != null ? data.getGlobalHeaders().size() : 0);

        return data;
    }

    public ExportPackage exportTeamPackage(String teamId) {
        return buildExportPackage(exportTeam(teamId));
    }

    public ExportPackage exportApisPackage(String teamId, List<String> apiIds) {
        permissionChecker.checkTeamAccess(teamId);
        if (apiIds == null || apiIds.isEmpty()) {
            return exportTeamPackage(teamId);
        }

        Team team = teamService.getById(teamId);
        List<ApiDefinition> apis = new ArrayList<ApiDefinition>();
        for (String apiId : apiIds) {
            ApiDefinition api = apiRepository.findById(apiId);
            if (api != null && teamId.equals(api.getTeamId())) {
                apis.add(api);
            }
        }

        ImportExportData data = new ImportExportData();
        data.setVersion("2.2");
        data.setExportedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));
        data.setTeamName(team != null ? team.getName() : "");
        data.setGroups(groupRepository.findByTeamId(teamId));
        data.setTags(tagRepository.findByTeamId(teamId));
        data.setApis(apis);

        List<ApiResponse> allResponses = new ArrayList<ApiResponse>();
        for (ApiDefinition api : apis) {
            allResponses.addAll(apiResponseRepository.findByApiId(api.getId()));
        }
        data.setApiResponses(allResponses);
        data.setGlobalHeaders(globalHeaderRepository.findByTeamId(teamId));
        fillTagMapping(data);
        return buildExportPackage(data);
    }

    private void fillTagMapping(ImportExportData data) {
        Map<String,List<String>> mapping=new HashMap<String,List<String>>();
        for(ApiDefinition api:data.getApis())mapping.put(api.getId(),apiTagRepository.findTagIdsByApiId(api.getId()));
        data.setApiTags(mapping);
    }

    private ExportPackage buildExportPackage(ImportExportData data) {
        data.setVersion("2.3");
        Map<String, Resource> files = new HashMap<String, Resource>();
        Map<String, String> wsdlFiles = new HashMap<String, String>();
        Map<String, String> exportedWsdls = new HashMap<String, String>();
        for (ApiDefinition api : data.getApis()) {
            if (!"SOAP".equalsIgnoreCase(api.getType())) continue;
            try {
                String key = wsdlReference(api);
                if (key == null) continue;
                String identity = api.getTeamId() + "\n" + key;
                String entry = exportedWsdls.get(identity);
                if (entry == null) {
                    byte[] bytes = soapService.readWsdlForExport(api.getTeamId(), key);
                    entry = "wsdl/" + UUID.randomUUID().toString() + ".wsdl";
                    files.put(entry, new org.springframework.core.io.ByteArrayResource(bytes));
                    exportedWsdls.put(identity, entry);
                }
                wsdlFiles.put(api.getId(), entry);
            } catch (Exception invalid) {
                throw new com.mockhub.common.model.BizException(40927,"EXPORT_WSDL_MISSING: WSDL缺失、不安全或损坏，不能生成不完整导出包");
            }
        }
        if (!wsdlFiles.isEmpty()) data.setApiWsdlFiles(wsdlFiles);
        if (data.getApiResponses() != null) {
            Set<String> exported = new HashSet<String>();
            for (ApiResponse response : data.getApiResponses()) {
                if (response == null
                        || !"FILE".equalsIgnoreCase(response.getBodyType())
                        || response.getFilePath() == null
                        || response.getFilePath().trim().isEmpty()
                        || exported.contains(response.getFilePath())) {
                    continue;
                }
                try {
                    files.put(response.getFilePath(), mockFileStorageService.loadAsResource(response.getFilePath()));
                    exported.add(response.getFilePath());
                } catch (Exception e) {
                    throw new com.mockhub.common.model.BizException(40927,"EXPORT_ATTACHMENT_MISSING: 附件缺失，不能生成不完整导出包");
                }
            }
        }
        return new ExportPackage(data, files);
    }

    /**
     * 导入接口数据到目标团队
     *
     * @param teamId 目标团队 ID
     * @param data   导入数据
     * @param mode   导入模式："merge"（合并）或 "override"（覆盖）
     * @return 导入结果统计
     */
    public void checkImportPermission(String teamId,String mode) {
        if("override".equalsIgnoreCase(mode))permissionChecker.checkTeamAdmin(teamId);
        else if("merge".equalsIgnoreCase(mode))permissionChecker.checkTeamWrite(teamId);
        else throw new com.mockhub.common.model.BizException(40000,"导入模式仅支持 merge 或 override");
    }
    public ImportResult importApis(String teamId,ImportExportData data,String mode) {
        return importApis(teamId,data,mode,Collections.emptyMap(),Collections.emptyMap());
    }
    public ImportResult importApis(String teamId,ImportExportData data,String mode,Map<String,FileBundleEntry> files) {
        return importApis(teamId,data,mode,files,Collections.emptyMap());
    }
    public ImportResult importApis(String teamId,ImportExportData data,String mode,Map<String,FileBundleEntry> files,Map<String,Long> expectedRevisions) {
        return com.mockhub.mock.lifecycle.ApiMutationSource.withSource("IMPORT",() -> apiRepository.write(() ->
                importInternal(teamId,data,mode,files,expectedRevisions)));
    }
    private ImportResult importInternal(String teamId,ImportExportData data,String mode,Map<String,FileBundleEntry> files,Map<String,Long> expectedRevisions) {
        checkImportPermission(teamId,mode);
        if(data==null)throw new com.mockhub.common.model.BizException(40000,"导入数据不能为空");
        boolean override="override".equalsIgnoreCase(mode);int skipped=0,imported=0,overridden=0;
        List<ImportItem> items=new ArrayList<ImportItem>();Set<String> routes=new HashSet<String>(),sourceIds=new HashSet<String>();
        // Prevalidate EVERY target/version/configuration while holding the write transaction, before any metadata or disk write.
        for(ApiDefinition source:data.getApis()==null?Collections.<ApiDefinition>emptyList():data.getApis()) {
            if(source==null||source.getId()==null||!sourceIds.add(source.getId()))throw new com.mockhub.common.model.BizException(40000,"导入接口ID缺失或重复");
            String path=com.mockhub.mock.lifecycle.ApiPaths.normalize(source.getPath());
            if(source.getMethod()==null||source.getMethod().trim().isEmpty())throw new com.mockhub.common.model.BizException(40000,"导入接口方法不能为空");
            String method=source.getMethod().trim().toUpperCase(java.util.Locale.ROOT);
            if(!routes.add(method+" "+path))throw new com.mockhub.common.model.BizException(40000,"导入包存在规范化后重复的路径与方法");
            List<ApiDefinition> found=apiRepository.findByTeamIdAndPathAndMethod(teamId,path,method);
            ApiDefinition target=found.isEmpty()?null:found.get(0);
            if(target!=null&&!override) { skipped++;continue; }
            com.mockhub.mock.model.dto.ApiDefinitionDTO dto=toDto(source,teamId,path,method);
            dto.setResponses(importResponses(source,data));
            if(target!=null) {
                Long expected=expectedRevisions==null?null:expectedRevisions.get(target.getId());
                if(com.mockhub.mock.lifecycle.ApiVersions.require(expected)!=target.getRevision())throw com.mockhub.mock.lifecycle.ApiVersions.conflict();
                dto.setExpectedRevision(expected);
            }
            ResponseValidator.validateDtos(dto.getResponses(),responseModes(source));
            items.add(new ImportItem(source,target,dto));
        }
        validateWsdlManifest(data,files);
        // All target revisions were checked above. Store each mapped attachment once, replacing only copied DTO config.
        Map<String,String> importedWsdls=new HashMap<String,String>();
        for(ImportItem item:items) {
            String entry=data.getApiWsdlFiles()==null?null:data.getApiWsdlFiles().get(item.source.getId());
            if(entry==null)continue;
            String key=importedWsdls.get(entry);
            if(key==null) { key=soapService.storeImportedWsdl(teamId,files.get(entry).getData());importedWsdls.put(entry,key); }
            ((com.fasterxml.jackson.databind.node.ObjectNode)item.dto.getSoapConfig()).put("wsdlFileName",key);
        }
        Map<String,String> groupMap=new HashMap<String,String>(),tagMap=new HashMap<String,String>();
        if(!items.isEmpty()) {
            for(ApiGroup source:data.getGroups()==null?Collections.<ApiGroup>emptyList():data.getGroups()) {
                if(source.getId()==null||groupMap.containsKey(source.getId()))throw new com.mockhub.common.model.BizException(40000,"导入分组ID缺失或重复");
                ApiGroup group=new ApiGroup();group.setId(UUID.randomUUID().toString());group.setTeamId(teamId);group.setName(source.getName());
                group.setSortOrder(source.getSortOrder());group.setCreatedAt(java.time.Instant.now().toString());groupRepository.insert(group);groupMap.put(source.getId(),group.getId());
            }
            for(Tag source:data.getTags()==null?Collections.<Tag>emptyList():data.getTags()) {
                if(source.getId()==null||tagMap.containsKey(source.getId()))throw new com.mockhub.common.model.BizException(40000,"导入标签ID缺失或重复");
                Tag tag=new Tag();tag.setId(UUID.randomUUID().toString());tag.setTeamId(teamId);tag.setName(source.getName());tag.setColor(source.getColor()==null?"#3B6D11":source.getColor());
                tagRepository.insert(tag);tagMap.put(source.getId(),tag.getId());
            }
        }
        for(ImportItem item:items) {
            item.dto.setGroupId(groupMap.get(item.source.getGroupId()));
            List<String> tags=new ArrayList<String>();
            if(data.getApiTags()!=null&&data.getApiTags().containsKey(item.source.getId())) {
                for(String old:data.getApiTags().get(item.source.getId())) {
                    if(!tagMap.containsKey(old))throw new com.mockhub.common.model.BizException(40000,"导入标签关联缺少标签定义");
                    tags.add(tagMap.get(old));
                }
            } else if(item.target!=null)tags.addAll(apiTagRepository.findTagIdsByApiId(item.target.getId()));
            item.dto.setTagIds(tags);
            for(com.mockhub.mock.model.dto.ApiResponseDTO response:item.dto.getResponses()) {
                if(!"FILE".equalsIgnoreCase(response.getBodyType()))continue;
                FileBundleEntry bundled=files==null?null:files.get(response.getFilePath());
                if(bundled!=null) {
                    MockFileStorageService.StoredMockFile stored=mockFileStorageService.store(teamId,
                            response.getFileName()==null?bundled.getFileName():response.getFileName(),response.getContentType(),
                            bundled.getData().length,new ByteArrayInputStream(bundled.getData()));
                    response.setFilePath(stored.getFilePath());response.setFileName(stored.getFileName());response.setFileSize(stored.getFileSize());
                } else {
                    apiRepository.requireFileReference(teamId,response.getFilePath());mockFileStorageService.loadAsResource(response.getFilePath());
                }
            }
            if(item.target==null) { apiService.create(item.dto);imported++; }
            else { apiService.update(item.target.getId(),item.dto);overridden++; }
        }
        ImportResult result=new ImportResult(imported,skipped,overridden);
        if(data.getApiTags()==null&&data.getTags()!=null&&!data.getTags().isEmpty())
            result.getWarnings().add("TAG_ASSOCIATIONS_UNAVAILABLE: 旧包没有标签关联映射，新接口不推测标签，覆盖接口保留原标签");
        if(data.getGlobalHeaders()!=null&&!data.getGlobalHeaders().isEmpty())
            result.getWarnings().add("TEAM_HEADERS_NOT_IMPORTED: 团队全局响应头为共享配置，本次未修改，请由管理员单独核对");
        return result;
    }
    /** A nonempty WSDL reference must be a string in an object; never silently discard malformed config. */
    private String wsdlReference(ApiDefinition api) {
        if(api.getSoapConfig()==null)return null;
        try {
            JsonNode config=JSON_MAPPER.readTree(api.getSoapConfig());
            if(config==null || config.isNull())return null;
            if(!config.isObject())throw new IOException("SOAP config is not an object");
            JsonNode key=config.get("wsdlFileName");
            if(key==null || key.isNull() || (key.isTextual() && key.asText().isEmpty()))return null;
            if(!key.isTextual())throw new IOException("WSDL reference is not a string");
            return key.asText();
        } catch(IOException invalid) { throw new com.mockhub.common.model.BizException(40000,"导入接口SOAP配置损坏"); }
    }
    private void validateWsdlManifest(ImportExportData data,Map<String,FileBundleEntry> files) {
        Map<String,String> manifest=data.getApiWsdlFiles()==null?Collections.<String,String>emptyMap():data.getApiWsdlFiles();
        if(manifest.isEmpty() && !"2.3".equals(data.getVersion()))return; // Legacy JSON keeps aggregate reference validation unchanged.
        Set<String> referenced=new HashSet<String>();
        for(ApiDefinition api:data.getApis()==null?Collections.<ApiDefinition>emptyList():data.getApis()) {
            if(!"SOAP".equalsIgnoreCase(api.getType()))continue;
            String key=wsdlReference(api);
            if(key==null)continue;
            referenced.add(api.getId());
            if("2.3".equals(data.getVersion()) && !manifest.containsKey(api.getId()))
                throw new com.mockhub.common.model.BizException(40927,"IMPORT_WSDL_MISSING: 完整导入包缺少WSDL附件映射");
        }
        for(Map.Entry<String,String> mapping:manifest.entrySet()) {
            String entry=mapping.getValue();
            if(!referenced.contains(mapping.getKey()) || entry==null
                    || !entry.matches("wsdl/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}[.]wsdl"))
                throw new com.mockhub.common.model.BizException(40000,"WSDL附件映射无效");
            FileBundleEntry bundled=files==null?null:files.get(entry);
            if(bundled==null || bundled.getData()==null || bundled.getData().length==0)
                throw new com.mockhub.common.model.BizException(40927,"IMPORT_WSDL_MISSING: 完整导入包缺少映射的WSDL附件内容");
            if(bundled.getData().length>SoapService.MAX_WSDL_BYTES)
                throw new com.mockhub.common.model.BizException(40701,"WSDL文件不能超过10MiB");
        }
    }
    private com.mockhub.mock.model.dto.ApiDefinitionDTO toDto(ApiDefinition source,String team,String path,String method) {
        com.mockhub.mock.model.dto.ApiDefinitionDTO dto=new com.mockhub.mock.model.dto.ApiDefinitionDTO();
        dto.setTeamId(team);dto.setName(source.getName());dto.setType(source.getType()==null?"REST":source.getType());dto.setDescription(source.getDescription());
        dto.setPath(path);dto.setMethod(method);dto.setEnabled(source.isEnabled());dto.setResponseCode(source.getResponseCode());dto.setContentType(source.getContentType());
        dto.setResponseBody(source.getResponseBody());dto.setDelayMs(source.getDelayMs());dto.setResponseMode(normalizeResponseMode(source.getResponseMode()));
        try {
            if(source.getGlobalHeaderOverrides()!=null)dto.setGlobalHeaderOverrides(JSON_MAPPER.readValue(source.getGlobalHeaderOverrides(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){}));
            if(source.getSoapConfig()!=null)dto.setSoapConfig(JSON_MAPPER.readTree(source.getSoapConfig()));
        } catch(IOException invalid) { throw new com.mockhub.common.model.BizException(40000,"导入接口JSON配置损坏"); }
        return dto;
    }
    private List<com.mockhub.mock.model.dto.ApiResponseDTO> importResponses(ApiDefinition source,ImportExportData data) {
        List<com.mockhub.mock.model.dto.ApiResponseDTO> result=new ArrayList<com.mockhub.mock.model.dto.ApiResponseDTO>();
        if(data.getApiResponses()!=null)for(ApiResponse response:data.getApiResponses())if(source.getId().equals(response.getApiId())) {
            com.mockhub.mock.model.dto.ApiResponseDTO dto=new com.mockhub.mock.model.dto.ApiResponseDTO();
            dto.setName(response.getName());dto.setSoapOperationName(response.getSoapOperationName());dto.setActive(response.isActive());
            dto.setResponseCode(response.getResponseCode());dto.setContentType(response.getContentType());dto.setResponseBody(response.getResponseBody());
            dto.setBodyType(response.getBodyType());dto.setFilePath(response.getFilePath());dto.setFileName(response.getFileName());dto.setFileSize(response.getFileSize());
            dto.setDownloadName(response.getDownloadName());dto.setDelayMs(response.getDelayMs());dto.setSortOrder(response.getSortOrder());dto.setConditions(response.getConditions());result.add(dto);
        }
        if(result.isEmpty()&&"CONDITION".equals(normalizeResponseMode(source.getResponseMode()))&&"REST".equalsIgnoreCase(source.getType()==null?"REST":source.getType())) {
            com.mockhub.mock.model.dto.ApiResponseDTO dto=new com.mockhub.mock.model.dto.ApiResponseDTO();dto.setName("Default");dto.setActive(true);
            dto.setResponseCode(source.getResponseCode());dto.setContentType(source.getContentType());dto.setResponseBody(source.getResponseBody());dto.setDelayMs(source.getDelayMs());result.add(dto);
        }
        return result;
    }
    private String normalizeResponseMode(String mode) {
        if(mode==null||mode.trim().isEmpty())return "CONDITION";
        if("CONDITION".equalsIgnoreCase(mode)||"RANDOM".equalsIgnoreCase(mode))return mode.toUpperCase(java.util.Locale.ROOT);
        throw new com.mockhub.common.model.BizException(40418,"返回体选择模式仅支持 CONDITION 或 RANDOM");
    }
    private Map<String,String> responseModes(ApiDefinition api) {
        Map<String,String> modes=new HashMap<String,String>();modes.put("__REST__",normalizeResponseMode(api.getResponseMode()));
        if(api.getSoapConfig()!=null)try {
            for(JsonNode operation:JSON_MAPPER.readTree(api.getSoapConfig()).path("operations")) {
                if(operation.hasNonNull("operationName"))modes.put(operation.path("operationName").asText(),normalizeResponseMode(operation.path("responseMode").asText(null)));
            }
        } catch(IOException invalid) { throw new com.mockhub.common.model.BizException(40418,"SOAP配置无法解析"); }
        return modes;
    }
    private static final class ImportItem {
        final ApiDefinition source,target;final com.mockhub.mock.model.dto.ApiDefinitionDTO dto;
        ImportItem(ApiDefinition source,ApiDefinition target,com.mockhub.mock.model.dto.ApiDefinitionDTO dto) { this.source=source;this.target=target;this.dto=dto; }
    }

    public static class ExportPackage {
        private final ImportExportData data;
        private final Map<String, Resource> files;

        public ExportPackage(ImportExportData data, Map<String, Resource> files) {
            this.data = data;
            this.files = files;
        }

        public ImportExportData getData() {
            return data;
        }

        public Map<String, Resource> getFiles() {
            return files;
        }

        public boolean hasFiles() {
            return files != null && !files.isEmpty();
        }
    }

    public static class FileBundleEntry {
        private final String fileName;
        private final byte[] data;

        public FileBundleEntry(String fileName, byte[] data) {
            this.fileName = fileName;
            this.data = data;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getData() {
            return data;
        }
    }
}
