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

    public SoapService(DataProperties dataProperties) {
        this.dataProperties = dataProperties;
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
     * 读取 WSDL 文件内容，并动态替换 soap:address location 为实际服务地址
     *
     * @param fileName  WSDL 文件名
     * @param serverUrl 当前服务器 URL（如 http://192.168.1.100:8080）
     * @return 替换后的 WSDL 文件内容
     * @throws BizException code=40701，文件不存在时抛出
     */
    public String getWsdlContent(String fileName, String serverUrl) {
        Path filePath = getWsdlDir().resolve(fileName);
        if (!Files.exists(filePath)) {
            throw new BizException(40701, "WSDL 文件不存在: " + fileName);
        }

        try {
            String content = new String(Files.readAllBytes(filePath), "UTF-8");

            // 动态替换 soap:address location
            // 匹配 <soap:address location="..."/> 或 <soap12:address location="..."/>
            content = content.replaceAll(
                    "(location=\")[^\"]*?(\")",
                    "$1" + serverUrl + "/mock/$2"
            );

            log.debug("WSDL 托管: fileName={}, serverUrl={}", fileName, serverUrl);
            return content;
        } catch (IOException e) {
            log.error("读取 WSDL 文件失败: {}", filePath, e);
            throw new BizException(50001, "读取 WSDL 文件失败");
        }
    }

    /**
     * 使用 DOM 解析 WSDL 文件，提取 operation 名称和 soapAction
     * <p>
     * 解析逻辑：
     * <ol>
     *   <li>查找所有 wsdl:operation（或 operation）元素</li>
     *   <li>从子元素 soap:operation（或 soap12:operation）中提取 soapAction 属性</li>
     * </ol>
     *
     * @param filePath WSDL 文件路径
     * @return 操作列表
     * @throws BizException code=40701，解析失败时抛出
     */
    private List<WsdlParseResult.WsdlOperation> parseWsdlFile(Path filePath) {
        List<WsdlParseResult.WsdlOperation> operations = new ArrayList<WsdlParseResult.WsdlOperation>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 安全配置：禁用外部实体
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream is = Files.newInputStream(filePath);
            Document doc;
            try {
                doc = builder.parse(is);
            } finally {
                is.close();
            }

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
                        operations.add(new WsdlParseResult.WsdlOperation(operationName, soapAction));
                        log.debug("解析到 SOAP operation: name={}, soapAction={}", operationName, soapAction);
                    }
                }
            }

            // 如果通过命名空间方式没找到带 soapAction 的，尝试直接查找 soap:operation 元素
            if (operations.isEmpty()) {
                parseSoapOperationElements(doc, operations);
            }

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 WSDL 文件失败: {}", filePath, e);
            throw new BizException(40701, "WSDL 文件解析失败: " + e.getMessage());
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
                    operations.add(new WsdlParseResult.WsdlOperation(operationName, soapAction));
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
                        operations.add(new WsdlParseResult.WsdlOperation(operationName, soapAction));
                    }
                }
            }
        }
    }

    /**
     * 获取 WSDL 文件存储目录
     */
    private Path getWsdlDir() {
        return Paths.get(dataProperties.getPath(), "wsdl");
    }
}
