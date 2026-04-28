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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

    /**
     * 上传 WSDL 文件并解析操作列表
     * <p>
     * 文件保存到 {data.path}/wsdl/ 目录，然后解析返回操作列表。
     *
     * @param file 上传的 WSDL 文件
     * @return 解析结果，包含文件名和操作列表
     * @throws BizException code=40701，文件解析失败时抛出
     */
    public WsdlParseResult uploadWsdl(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(40701, "WSDL 文件不能为空");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "unknown.wsdl";
        }

        // 确保 wsdl 目录存在
        Path wsdlDir = getWsdlDir();
        try {
            Files.createDirectories(wsdlDir);
        } catch (IOException e) {
            log.error("创建 WSDL 目录失败: {}", wsdlDir, e);
            throw new BizException(50001, "创建 WSDL 存储目录失败");
        }

        // 保存文件
        Path targetPath = wsdlDir.resolve(fileName);
        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("WSDL 文件保存成功: {}", targetPath);
        } catch (IOException e) {
            log.error("保存 WSDL 文件失败: {}", targetPath, e);
            throw new BizException(50001, "保存 WSDL 文件失败");
        }

        // 解析操作列表
        List<WsdlParseResult.WsdlOperation> operations = parseWsdlFile(targetPath);
        log.info("WSDL 文件解析成功: fileName={}, operations={}", fileName, operations.size());

        return new WsdlParseResult(fileName, operations);
    }

    /**
     * 解析已上传的 WSDL 文件，返回操作列表
     *
     * @param fileName WSDL 文件名
     * @return 解析结果
     * @throws BizException code=40701，文件不存在或解析失败时抛出
     */
    public WsdlParseResult parseOperations(String fileName) {
        Path filePath = getWsdlDir().resolve(fileName);
        if (!Files.exists(filePath)) {
            throw new BizException(40701, "WSDL 文件不存在: " + fileName);
        }

        List<WsdlParseResult.WsdlOperation> operations = parseWsdlFile(filePath);
        log.info("WSDL 文件重新解析: fileName={}, operations={}", fileName, operations.size());

        return new WsdlParseResult(fileName, operations);
    }

    /**
     * 读取 WSDL 文件内容，并动态替换 soap:address location 为指定的 mock URL。
     *
     * @param fileName WSDL 文件名
     * @param mockUrl  完整的 Mock 接口 URL（如 http://host:8080/mock/EFB/ck/release）
     * @return 替换后的 WSDL 文件内容
     * @throws BizException code=40701，文件不存在时抛出
     */
    public String getWsdlContent(String fileName, String mockUrl) {
        Path filePath = getWsdlDir().resolve(fileName);
        if (!Files.exists(filePath)) {
            throw new BizException(40701, "WSDL 文件不存在: " + fileName);
        }

        try {
            String content = new String(Files.readAllBytes(filePath), "UTF-8");

            // 动态替换 <soap:address location="..."/> 或 <soap12:address location="..."/>
            // 正则捕获组 1 = location="，组 2 = "；替换为 location="{mockUrl}"
            // 用 Matcher.quoteReplacement 防止 mockUrl 里的 $ 或 \ 干扰正则替换
            content = content.replaceAll(
                    "(location=\")[^\"]*?(\")",
                    "$1" + java.util.regex.Matcher.quoteReplacement(mockUrl) + "$2"
            );

            log.debug("WSDL 托管: fileName={}, mockUrl={}", fileName, mockUrl);
            return content;
        } catch (IOException e) {
            log.error("读取 WSDL 文件失败: {}", filePath, e);
            throw new BizException(50001, "读取 WSDL 文件失败");
        }
    }

    /**
     * 使用 DOM 解析 WSDL 文件，提取 operation 名称 + soapAction +（可选）响应体骨架。
     *
     * @param filePath WSDL 文件路径
     * @return 操作列表（含 suggestedResponseBody，可为 null）
     * @throws BizException code=40701，解析失败时抛出
     */
    private List<WsdlParseResult.WsdlOperation> parseWsdlFile(Path filePath) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 安全配置：禁用外部实体
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream is = Files.newInputStream(filePath);
            try {
                doc = builder.parse(is);
            } finally {
                is.close();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 WSDL 文件失败: {}", filePath, e);
            throw new BizException(40701, "WSDL 文件解析失败: " + e.getMessage());
        }

        // 提取 operation 名 + soapAction
        List<WsdlParseResult.WsdlOperation> operations = extractOperations(doc);

        // 提取 targetNamespace 供骨架生成使用
        String tns = doc.getDocumentElement().getAttribute("targetNamespace");

        // 为每个 operation 生成响应体骨架（失败时 suggestedResponseBody 保持 null，不致命）
        for (WsdlParseResult.WsdlOperation op : operations) {
            try {
                String skeleton = skeletonGenerator.generate(doc, op.getOperationName(), tns);
                op.setSuggestedResponseBody(skeleton);
            } catch (Exception e) {
                log.warn("生成 operation 骨架失败: {}, error={}",
                        op.getOperationName(), e.getMessage());
            }
        }

        return operations;
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
                    log.debug("解析到 SOAP operation: name={}, soapAction={}, hasDoc={}",
                            operationName, soapAction, newOp.getDescription() != null);
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
