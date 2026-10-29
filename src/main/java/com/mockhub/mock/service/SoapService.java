package com.mockhub.mock.service;

import com.mockhub.common.config.DataProperties;
import com.mockhub.common.model.BizException;
import com.mockhub.mock.model.dto.WsdlParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * SOAP/WSDL 服务
 * <p>
 * 提供 WSDL 文件上传、解析和托管功能：
 * <ul>
 *   <li>上传 WSDL 文件到 data/wsdl/ 目录</li>
 *   <li>使用标准 DOM 解析 WSDL 中的 operation 和 soapAction</li>
 *   <li>托管 WSDL 文件时动态替换 soap:address location</li>
 * </ul>
 */
@Service
public class SoapService {

    private static final Logger log = LoggerFactory.getLogger(SoapService.class);

    private final DataProperties dataProperties;
    private final SoapSkeletonGenerator skeletonGenerator;

    public SoapService(DataProperties dataProperties, SoapSkeletonGenerator skeletonGenerator) {
        this.dataProperties = dataProperties;
        this.skeletonGenerator = skeletonGenerator;
    }

    public static final int MAX_WSDL_BYTES=10*1024*1024;
    private static final java.util.regex.Pattern V2=java.util.regex.Pattern.compile("v2/[0-9a-f]{64}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.wsdl");
    private static final java.util.regex.Pattern LEGACY=java.util.regex.Pattern.compile("[\\p{L}\\p{N}_-][\\p{L}\\p{N}\\p{M} _.()-]{0,180}\\.(?i:wsdl|xml)");
    @org.springframework.beans.factory.annotation.Autowired private com.mockhub.common.util.PermissionChecker permissionChecker;
    @org.springframework.beans.factory.annotation.Autowired private com.mockhub.system.repository.TeamRepository teamRepository;
    @org.springframework.beans.factory.annotation.Autowired private com.mockhub.mock.repository.WsdlReferenceRepository references;
    @org.springframework.beans.factory.annotation.Autowired private com.mockhub.system.repository.UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired private com.mockhub.system.service.AdminSafetyService adminSafety;

    /** Filename is display metadata only. Parse a bounded buffer BEFORE creating any stored file. */
    @org.springframework.transaction.annotation.Transactional
    public WsdlParseResult uploadWsdl(String teamId,MultipartFile file) {
        adminSafety.acquireWriteLock();
        requireTeam(teamId);
        com.mockhub.system.model.entity.User actor=users.findById(com.mockhub.common.util.SecurityContextUtil.getCurrentUserId());
        if(actor==null || !actor.isActive())throw new BizException(40101,"账号不存在或已停用");
        if(!"SUPER_ADMIN".equals(actor.getGlobalRole())){
            com.mockhub.common.model.enums.TeamRoleType role=com.mockhub.common.model.enums.TeamRoleType.fromStored(users.findUserTeamRole(actor.getId(),teamId));
            if(role==null || !role.canWrite())throw new BizException(40101,"当前账号无团队写入权限");
        }
        permissionChecker.checkTeamWrite(teamId);
        if(file==null || file.isEmpty() || file.getSize()>MAX_WSDL_BYTES)throw new BizException(40701,"WSDL文件不能为空且不能超过10MiB");
        byte[] bytes;
        try(InputStream in=file.getInputStream()){bytes=readBounded(in);}
        catch(IOException e){throw new BizException(40701,"无法读取WSDL上传内容");}
        Document doc=parseDocument(bytes);
        List<WsdlParseResult.WsdlOperation> operations=operations(doc);
        String key="v2/"+teamHash(teamId)+"/"+java.util.UUID.randomUUID().toString()+".wsdl";
        boolean created=false;
        try{
            createStorageDirectories(teamId);
            try(java.nio.channels.SeekableByteChannel out=openSecure(key,true)){
                created=true;
                java.nio.ByteBuffer buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())out.write(buffer);
            }
        }catch(IOException e){
            if(created)deletePartial(key);
            throw new BizException(50001,"无法安全保存WSDL，请检查存储目录权限");
        }
        WsdlParseResult result=new WsdlParseResult(key,operations);result.setOriginalFileName(displayName(file.getOriginalFilename()));
        log.info("WSDL上传完成，操作数={}",operations.size());return result;
    }
    /** Explicit managed read: a viewer may inspect its own team's safe WSDL, never another team's. */
    public WsdlParseResult parseOperations(String teamId,String key){
        requireTeam(teamId);permissionChecker.checkTeamAccess(teamId);
        return new WsdlParseResult(key,operations(parseDocument(readOwned(teamId,key))));
    }
    /** Public Mock WSDL uses only the team resolved from the persisted API's real route. */
    public String getWsdlContent(String teamId,String key,String mockUrl){
        byte[] bytes=readOwned(teamId,key);parseDocument(bytes);
        String safeUrl=mockUrl.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
        return new String(bytes,java.nio.charset.StandardCharsets.UTF_8).replaceAll("(location=\")[^\"]*?(\")","$1"+java.util.regex.Matcher.quoteReplacement(safeUrl)+"$2");
    }
    /** Called under API aggregate transactions, without substituting DTO team claims for actual ownership. */
    public void assertReference(String teamId,String key){parseDocument(readOwned(teamId,key));}
    private byte[] readOwned(String teamId,String key){
        requireTeam(teamId);validateKey(teamId,key);
        try(java.nio.channels.SeekableByteChannel channel=openSecure(key,false);InputStream in=java.nio.channels.Channels.newInputStream(channel)){return readBounded(in);}
        catch(IOException e){throw new BizException(40702,"WSDL引用不存在或不是安全的团队文件，请重新上传");}
    }
    private void validateKey(String teamId,String key){
        if(key==null || key.isEmpty())throw invalidReference();
        boolean modern=V2.matcher(key).matches() && key.startsWith("v2/"+teamHash(teamId)+"/");
        boolean legacy=LEGACY.matcher(key).matches() && references!=null && references.hasLegacyReference(teamId,key);
        if(!modern && !legacy)throw invalidReference();
        Path root=getWsdlDir().toAbsolutePath().normalize(),target=root.resolve(key).normalize();
        if(!target.startsWith(root) || target.equals(root))throw invalidReference();
    }
    private void requireTeam(String teamId){if(teamId==null || teamId.trim().isEmpty())throw new BizException(40000,"必须显式指定团队");if(teamRepository==null || teamRepository.findById(teamId)==null)throw new BizException(40403,"团队不存在");}
    private static BizException invalidReference(){return new BizException(40702,"WSDL引用无效或不属于当前团队，请重新上传");}
    public static String teamHash(String teamId){
        try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(teamId.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format("%02x",b&255));return out.toString();}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void createStorageDirectories(String teamId)throws IOException{
        Path data=Paths.get(dataProperties.getPath()).toAbsolutePath().normalize();Files.createDirectories(data);
        Path path=data;
        for(String part:new String[]{"wsdl","v2",teamHash(teamId)}){
            path=path.resolve(part);
            try{Files.createDirectory(path);}catch(java.nio.file.FileAlreadyExistsException exists){ /* Verify without following links. */ }
            if(!Files.isDirectory(path,java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))throw new IOException("Unsafe WSDL directory");
        }
    }
    /** Prefer directory-relative NOFOLLOW opens. Providers without them recheck canonical, non-link parents around a NOFOLLOW leaf open. */
    private java.nio.channels.SeekableByteChannel openSecure(String key,boolean create)throws IOException{
        Path data=Paths.get(dataProperties.getPath()).toAbsolutePath().normalize().toRealPath();
        java.util.List<java.nio.file.DirectoryStream<Path>> streams=new ArrayList<java.nio.file.DirectoryStream<Path>>();
        try{
            java.nio.file.DirectoryStream<Path> base=Files.newDirectoryStream(data);streams.add(base);
            if(!(base instanceof java.nio.file.SecureDirectoryStream))return openCheckedPath(data,key,create);
            java.nio.file.SecureDirectoryStream<Path> dir=(java.nio.file.SecureDirectoryStream<Path>)base;
            String[] parts=("wsdl/"+key).split("/");
            for(int i=0;i<parts.length-1;i++){dir=dir.newDirectoryStream(Paths.get(parts[i]),java.nio.file.LinkOption.NOFOLLOW_LINKS);streams.add(dir);}
            java.util.Set<java.nio.file.OpenOption> options=new java.util.HashSet<java.nio.file.OpenOption>();options.add(java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if(create){options.add(java.nio.file.StandardOpenOption.CREATE_NEW);options.add(java.nio.file.StandardOpenOption.WRITE);}else options.add(java.nio.file.StandardOpenOption.READ);
            Path leaf=Paths.get(parts[parts.length-1]);
            if(!create && !dir.getFileAttributeView(leaf,java.nio.file.attribute.BasicFileAttributeView.class,java.nio.file.LinkOption.NOFOLLOW_LINKS).readAttributes().isRegularFile())throw new IOException("Not a regular WSDL file");
            return dir.newByteChannel(leaf,options);
        }finally{for(int i=streams.size()-1;i>=0;i--)try{streams.get(i).close();}catch(IOException ignored){}}
    }
    private java.nio.channels.SeekableByteChannel openCheckedPath(Path data,String key,boolean create)throws IOException{
        Path target=checkedPath(data,key,create);
        java.util.Set<java.nio.file.OpenOption> options=new java.util.HashSet<java.nio.file.OpenOption>();options.add(java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if(create){options.add(java.nio.file.StandardOpenOption.CREATE_NEW);options.add(java.nio.file.StandardOpenOption.WRITE);}else options.add(java.nio.file.StandardOpenOption.READ);
        java.nio.channels.SeekableByteChannel channel=Files.newByteChannel(target,options);
        try{checkedPath(data,key,false);return channel;}catch(IOException e){channel.close();throw e;}
    }
    private Path checkedPath(Path data,String key,boolean create)throws IOException{
        Path root=data.resolve("wsdl").normalize(),target=root.resolve(key).normalize();
        if(!target.startsWith(root) || target.equals(root))throw new IOException("Invalid WSDL key");
        Path current=data;
        for(Path component:data.relativize(target.getParent())){
            current=current.resolve(component);
            if(!Files.isDirectory(current,java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(current) || !current.toRealPath().equals(current))throw new IOException("Unsafe WSDL parent");
        }
        if(!create && (!Files.isRegularFile(target,java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)))throw new IOException("Unsafe WSDL file");
        return target;
    }
    private void deletePartial(String key){
        java.util.List<java.nio.file.DirectoryStream<Path>> streams=new ArrayList<java.nio.file.DirectoryStream<Path>>();
        try{
            java.nio.file.DirectoryStream<Path> base=Files.newDirectoryStream(Paths.get(dataProperties.getPath()).toAbsolutePath().normalize().toRealPath());streams.add(base);
            if(!(base instanceof java.nio.file.SecureDirectoryStream)){Files.deleteIfExists(checkedPath(Paths.get(dataProperties.getPath()).toAbsolutePath().normalize().toRealPath(),key,false));return;}
            java.nio.file.SecureDirectoryStream<Path> dir=(java.nio.file.SecureDirectoryStream<Path>)base;String[] parts=("wsdl/"+key).split("/");
            for(int i=0;i<parts.length-1;i++){dir=dir.newDirectoryStream(Paths.get(parts[i]),java.nio.file.LinkOption.NOFOLLOW_LINKS);streams.add(dir);}
            dir.deleteFile(Paths.get(parts[parts.length-1]));
        }catch(IOException cleanupFailure){log.warn("清理未完成的WSDL上传失败，需要后续人工核查");}
        finally{for(int i=streams.size()-1;i>=0;i--)try{streams.get(i).close();}catch(IOException ignored){}}
    }
    private static byte[] readBounded(InputStream in)throws IOException{
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){if(bytes.size()+n>MAX_WSDL_BYTES)throw new BizException(40701,"WSDL文件不能超过10MiB");bytes.write(buffer,0,n);}return bytes.toByteArray();
    }
    private static String displayName(String raw){if(raw==null)return "upload.wsdl";String value=raw.replace('\\','/');value=value.substring(value.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","").trim();return value.isEmpty()?"upload.wsdl":value.substring(0,Math.min(120,value.length()));}
    private Document parseDocument(byte[] bytes){
        try{
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth","128");
            DocumentBuilder builder=factory.newDocumentBuilder();builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){
                @Override public void error(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}
                @Override public void fatalError(org.xml.sax.SAXParseException e)throws org.xml.sax.SAXException{throw e;}
            });
            Document doc=builder.parse(new java.io.ByteArrayInputStream(bytes));Element root=doc.getDocumentElement();
            if(root==null || !("definitions".equals(root.getLocalName()) || "definitions".equals(root.getNodeName()))
                    || (root.getNamespaceURI()!=null && !root.getNamespaceURI().isEmpty() && !"http://schemas.xmlsoap.org/wsdl/".equals(root.getNamespaceURI())))throw new IllegalArgumentException("Not WSDL");
            return doc;
        }catch(Exception e){throw new BizException(40701,"WSDL内容无效或包含不允许的XML结构");}
    }
    private List<WsdlParseResult.WsdlOperation> operations(Document doc){
        List<WsdlParseResult.WsdlOperation> result=extractOperations(doc);String tns=doc.getDocumentElement().getAttribute("targetNamespace");
        for(WsdlParseResult.WsdlOperation op:result){try{op.setSuggestedResponseBody(skeletonGenerator.generate(doc,op.getOperationName(),tns));}catch(Exception e){log.warn("WSDL响应体骨架生成失败");}}
        return result;
    }

    /**
     * 从已解析的 WSDL Document 中提取 operation 列表。
     * 原 parseWsdlFile 的提取逻辑，从 Document 解析拆离，与骨架生成解耦。
     */
    private List<WsdlParseResult.WsdlOperation> extractOperations(Document doc) {
        List<WsdlParseResult.WsdlOperation> operations = new ArrayList<WsdlParseResult.WsdlOperation>();

        // 查找 binding 下的 operation 元素
        // WSDL 1.1 的 binding/operation 中包含 soap:operation
        NodeList bindingOps = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "operation");
        if (bindingOps.getLength() == 0) {
            // 尝试不带命名空间
            bindingOps = doc.getElementsByTagName("operation");
        }

        for (int i = 0; i < bindingOps.getLength(); i++) {
            Element opElement = (Element) bindingOps.item(i);
            String operationName = opElement.getAttribute("name");
            if (operationName == null || operationName.isEmpty()) {
                continue;
            }

            // 提取 soapAction
            String soapAction = extractSoapAction(opElement);

            // 只收集有 soapAction 的操作（即 binding 中的 operation）
            if (soapAction != null) {
                // 避免重复添加（portType 和 binding 中的同名 operation）
                boolean exists = false;
                for (WsdlParseResult.WsdlOperation existing : operations) {
                    if (operationName.equals(existing.getOperationName())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    WsdlParseResult.WsdlOperation newOp = new WsdlParseResult.WsdlOperation(operationName, soapAction);
                    // 从 portType/operation 下提取 <wsdl:documentation>（v1.4.4）
                    newOp.setDescription(extractDocumentation(doc, operationName));
                    operations.add(newOp);
                }
            }
        }

        // 如果通过命名空间方式没找到带 soapAction 的，尝试直接查找 soap:operation 元素
        if (operations.isEmpty()) {
            parseSoapOperationElements(doc, operations);
        }

        return operations;
    }

    /**
     * 从 operation 元素的子元素中提取 soapAction
     */
    private String extractSoapAction(Element opElement) {
        // 尝试 SOAP 1.1 命名空间
        NodeList soapOps = opElement.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap/", "operation");
        if (soapOps.getLength() > 0) {
            return ((Element) soapOps.item(0)).getAttribute("soapAction");
        }

        // 尝试 SOAP 1.2 命名空间
        soapOps = opElement.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap12/", "operation");
        if (soapOps.getLength() > 0) {
            return ((Element) soapOps.item(0)).getAttribute("soapAction");
        }

        return null;
    }

    /**
     * 备选解析：直接查找所有 soap:operation 元素，通过父元素获取 operation name
     */
    private void parseSoapOperationElements(Document doc, List<WsdlParseResult.WsdlOperation> operations) {
        // SOAP 1.1
        NodeList soapOps = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap/", "operation");
        for (int i = 0; i < soapOps.getLength(); i++) {
            Element soapOp = (Element) soapOps.item(i);
            String soapAction = soapOp.getAttribute("soapAction");

            // 父元素应该是 wsdl:operation
            if (soapOp.getParentNode() instanceof Element) {
                Element parent = (Element) soapOp.getParentNode();
                String operationName = parent.getAttribute("name");
                if (operationName != null && !operationName.isEmpty() && soapAction != null) {
                    WsdlParseResult.WsdlOperation op = new WsdlParseResult.WsdlOperation(operationName, soapAction);
                    op.setDescription(extractDocumentation(doc, operationName));
                    operations.add(op);
                }
            }
        }

        // SOAP 1.2
        if (operations.isEmpty()) {
            soapOps = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/soap12/", "operation");
            for (int i = 0; i < soapOps.getLength(); i++) {
                Element soapOp = (Element) soapOps.item(i);
                String soapAction = soapOp.getAttribute("soapAction");

                if (soapOp.getParentNode() instanceof Element) {
                    Element parent = (Element) soapOp.getParentNode();
                    String operationName = parent.getAttribute("name");
                    if (operationName != null && !operationName.isEmpty() && soapAction != null) {
                        WsdlParseResult.WsdlOperation op = new WsdlParseResult.WsdlOperation(operationName, soapAction);
                        op.setDescription(extractDocumentation(doc, operationName));
                        operations.add(op);
                    }
                }
            }
        }
    }

    /**
     * 从 WSDL 文档中查找指定 operation 的 &lt;wsdl:documentation&gt; 文本（v1.4.4 引入）。
     * <p>
     * 优先查 portType 下的 operation（标准做法），兜底查 binding 下的 operation。
     * 文本会 trim 后返回；未找到或为空时返回 null。
     *
     * @param doc           已解析的 WSDL Document
     * @param operationName 目标 operation 名
     * @return documentation 文本，或 null
     */
    private String extractDocumentation(Document doc, String operationName) {
        // 1. 先查 portType 下的 operation
        String doc1 = findDocumentationInContainer(doc, "portType", operationName);
        if (doc1 != null) {
            return doc1;
        }
        // 2. 兜底查 binding 下的 operation
        return findDocumentationInContainer(doc, "binding", operationName);
    }

    /**
     * 在指定容器元素（portType 或 binding）下查找目标 operation 的 documentation 文本
     */
    private String findDocumentationInContainer(Document doc, String containerTag, String operationName) {
        NodeList containers = doc.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", containerTag);
        if (containers.getLength() == 0) {
            containers = doc.getElementsByTagName(containerTag);
        }
        for (int i = 0; i < containers.getLength(); i++) {
            Element container = (Element) containers.item(i);
            NodeList ops = container.getElementsByTagNameNS("http://schemas.xmlsoap.org/wsdl/", "operation");
            if (ops.getLength() == 0) {
                ops = container.getElementsByTagName("operation");
            }
            for (int j = 0; j < ops.getLength(); j++) {
                Element op = (Element) ops.item(j);
                if (!operationName.equals(op.getAttribute("name"))) {
                    continue;
                }
                // 只查直接子元素的 documentation，避免嵌套 operation 干扰
                NodeList children = op.getChildNodes();
                for (int k = 0; k < children.getLength(); k++) {
                    if (children.item(k) instanceof Element) {
                        Element child = (Element) children.item(k);
                        if ("documentation".equals(child.getLocalName())
                                && "http://schemas.xmlsoap.org/wsdl/".equals(child.getNamespaceURI())) {
                            String text = child.getTextContent();
                            if (text != null && !text.trim().isEmpty()) {
                                return text.trim();
                            }
                        } else if ("documentation".equals(child.getTagName())) {
                            // 无命名空间兜底
                            String text = child.getTextContent();
                            if (text != null && !text.trim().isEmpty()) {
                                return text.trim();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取 WSDL 文件存储目录
     */
    private Path getWsdlDir() {
        return Paths.get(dataProperties.getPath(), "wsdl");
    }
}
