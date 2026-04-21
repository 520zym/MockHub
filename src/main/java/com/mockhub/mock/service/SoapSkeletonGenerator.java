package com.mockhub.mock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SOAP 响应体骨架生成器
 * <p>
 * 基于 WSDL 内嵌 XSD schema 递归生成 SOAP Envelope 骨架，
 * 用于新建 SOAP 接口时作为用户填写响应体的起始模板。
 * <p>
 * 设计决策：
 * <ul>
 *   <li>独立成 Component，便于单元测试和后续扩展</li>
 *   <li>递归深度上限 10 层，防御 XSD 循环引用</li>
 *   <li>未识别类型留空元素而非抛异常——整体不致命</li>
 * </ul>
 */
@Component
public class SoapSkeletonGenerator {

    private static final Logger log = LoggerFactory.getLogger(SoapSkeletonGenerator.class);

    /** XSD 递归展开深度上限（防循环引用） */
    private static final int MAX_DEPTH = 10;

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    /** 合法 XML NCName：[A-Za-z_][A-Za-z0-9_\-.]* （不允许冒号） */
    private static final Pattern NCNAME_PATTERN =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_\\-.]*$");

    /**
     * 判断字符串是否为合法 XML NCName（non-colonized name）。
     * 用于在写出 XML 前校验元素名，拦截来自 WSDL 的畸形 name 属性。
     *
     * @param s 待校验的名称字符串
     * @return true 表示合法
     */
    private boolean isValidNCName(String s) {
        return s != null && !s.isEmpty() && NCNAME_PATTERN.matcher(s).matches();
    }

    /**
     * 为指定 operation 生成响应体骨架。
     *
     * @param wsdlDoc          已解析的 WSDL Document
     * @param operationName    operation 名称（响应元素名为 {name}Response）
     * @param targetNamespace  WSDL 的 targetNamespace，用作响应元素默认命名空间
     * @return SOAP Envelope XML 字符串；响应元素未找到时返回 null
     */
    public String generate(Document wsdlDoc, String operationName, String targetNamespace) {
        try {
            String responseElementName = operationName + "Response";
            Element responseElement = findElementByName(wsdlDoc, responseElementName);
            if (responseElement == null) {
                log.warn("未找到响应元素，跳过骨架生成: operationName={}, responseElement={}",
                        operationName, responseElementName);
                return null;
            }

            Map<String, Element> complexTypes = collectComplexTypes(wsdlDoc);

            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n");
            sb.append("  <soap:Body>\n");

            // 校验 operationName 生成的元素名合法
            if (!isValidNCName(responseElementName)) {
                log.warn("响应元素名非法，跳过骨架生成: operationName={}", operationName);
                return null;
            }

            String rootIndent = "    ";
            sb.append(rootIndent).append("<").append(responseElementName);
            if (targetNamespace != null && !targetNamespace.isEmpty()) {
                sb.append(" xmlns=\"").append(escapeAttr(targetNamespace)).append("\"");
            }
            sb.append(">\n");

            expandElement(responseElement, complexTypes, sb, rootIndent + "  ", 0);

            sb.append(rootIndent).append("</").append(responseElementName).append(">\n");
            sb.append("  </soap:Body>\n");
            sb.append("</soap:Envelope>");

            return sb.toString();
        } catch (Exception e) {
            log.warn("生成响应体骨架失败: operationName={}, error={}", operationName, e.getMessage());
            return null;
        }
    }

    /**
     * 在整个 WSDL Document 中查找 name=xxx 的 xs:element 顶层声明。
     * 顶层声明定义为直接父节点为 xs:schema 的 element。
     */
    private Element findElementByName(Document doc, String name) {
        NodeList elements = doc.getElementsByTagNameNS(XSD_NS, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            if (name.equals(el.getAttribute("name"))) {
                Node parent = el.getParentNode();
                if (parent != null && "schema".equals(parent.getLocalName())
                        && XSD_NS.equals(parent.getNamespaceURI())) {
                    return el;
                }
            }
        }
        return null;
    }

    /**
     * 收集所有命名 xs:complexType（放在 schema 顶层），供 type 引用查找。
     */
    private Map<String, Element> collectComplexTypes(Document doc) {
        Map<String, Element> result = new HashMap<String, Element>();
        NodeList types = doc.getElementsByTagNameNS(XSD_NS, "complexType");
        for (int i = 0; i < types.getLength(); i++) {
            Element t = (Element) types.item(i);
            String name = t.getAttribute("name");
            if (name == null || name.isEmpty()) continue;
            // 只收集 xs:schema 顶层的命名 complexType，防止匿名内嵌的污染 map
            Node parent = t.getParentNode();
            if (parent != null && "schema".equals(parent.getLocalName())
                    && XSD_NS.equals(parent.getNamespaceURI())) {
                result.put(name, t);
            }
        }
        return result;
    }

    /**
     * 递归展开一个元素的内容：
     * 优先检查匿名内嵌 complexType，其次按 type 属性查命名 complexType 表。
     * 简单类型由父层负责输出占位符，此方法只处理复杂类型展开。
     *
     * @param element      xs:element 节点
     * @param complexTypes 命名 complexType 表
     * @param sb           输出 StringBuilder
     * @param indent       当前缩进
     * @param depth        当前递归深度（0 起）
     */
    private void expandElement(Element element, Map<String, Element> complexTypes,
                               StringBuilder sb, String indent, int depth) {
        if (depth > MAX_DEPTH) {
            log.warn("XSD 递归深度超限（可能循环引用），截断: depth={}", depth);
            return;
        }

        // 优先使用匿名内嵌 complexType
        Element innerComplex = getChildNS(element, XSD_NS, "complexType");
        if (innerComplex != null) {
            expandComplexType(innerComplex, complexTypes, sb, indent, depth);
            return;
        }

        // 按 type 属性查命名 complexType
        String typeAttr = element.getAttribute("type");
        if (typeAttr == null || typeAttr.isEmpty()) {
            return;
        }

        String localType = typeAttr.contains(":") ? typeAttr.substring(typeAttr.indexOf(':') + 1) : typeAttr;

        Element refType = complexTypes.get(localType);
        if (refType != null) {
            expandComplexType(refType, complexTypes, sb, indent, depth);
        }
        // 简单类型无子内容，由调用方已经输出了占位符
    }

    /**
     * 展开一个 complexType 的 xs:sequence 子元素，逐一生成 XML 标签。
     *
     * @param complexType  xs:complexType 节点
     * @param complexTypes 命名 complexType 表
     * @param sb           输出 StringBuilder
     * @param indent       当前缩进
     * @param depth        当前递归深度
     */
    private void expandComplexType(Element complexType, Map<String, Element> complexTypes,
                                   StringBuilder sb, String indent, int depth) {
        Element sequence = getChildNS(complexType, XSD_NS, "sequence");
        if (sequence == null) {
            return;
        }
        NodeList children = sequence.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) continue;
            if (!XSD_NS.equals(child.getNamespaceURI())) continue;
            if (!"element".equals(child.getLocalName())) continue;

            Element childEl = (Element) child;
            String name = childEl.getAttribute("name");
            if (name == null || name.isEmpty()) continue;
            // 校验 name 是合法 XML NCName，否则跳过（来自不可信 WSDL 的畸形输入）
            if (!isValidNCName(name)) {
                log.warn("跳过非法 XML 元素名: {}", name);
                continue;
            }

            String type = childEl.getAttribute("type");
            String localType = (type != null && type.contains(":"))
                    ? type.substring(type.indexOf(':') + 1) : type;

            // 判断是否有子结构需要递归展开
            Element innerComplex = getChildNS(childEl, XSD_NS, "complexType");
            boolean hasChildren = innerComplex != null
                    || (localType != null && !localType.isEmpty() && complexTypes.containsKey(localType));

            if (hasChildren) {
                sb.append(indent).append("<").append(name).append(">\n");
                expandElement(childEl, complexTypes, sb, indent + "  ", depth + 1);
                sb.append(indent).append("</").append(name).append(">\n");
            } else {
                // 简单类型输出占位符
                String placeholder = placeholderFor(localType);
                if (placeholder.isEmpty()) {
                    sb.append(indent).append("<").append(name).append("></").append(name).append(">\n");
                } else {
                    sb.append(indent).append("<").append(name).append(">")
                            .append(placeholder).append("</").append(name).append(">\n");
                }
            }
        }
    }

    /**
     * 根据 XSD 内置简单类型返回占位符字符串。
     * <ul>
     *   <li>string / base64Binary / hexBinary → ""（空字符串）</li>
     *   <li>int / integer / long / short / byte → "0"</li>
     *   <li>decimal / double / float → "0"</li>
     *   <li>boolean → "false"</li>
     *   <li>dateTime / date / time 及其他 → ""</li>
     * </ul>
     *
     * @param xsdType XSD 类型本地名（不含命名空间前缀），可为 null
     * @return 占位符字符串，未识别类型返回 ""
     */
    private String placeholderFor(String xsdType) {
        if (xsdType == null) return "";
        String t = xsdType.toLowerCase();
        if (t.endsWith("string") || t.endsWith("base64binary") || t.endsWith("hexbinary")) return "";
        if (t.endsWith("int") || t.endsWith("integer") || t.endsWith("long")
                || t.endsWith("short") || t.endsWith("byte")) return "0";
        if (t.endsWith("decimal") || t.endsWith("double") || t.endsWith("float")) return "0";
        if (t.endsWith("boolean")) return "false";
        return "";
    }

    /**
     * 找到第一个命名空间和本地名匹配的直接子元素。
     *
     * @param parent    父元素
     * @param ns        目标命名空间 URI
     * @param localName 目标本地名
     * @return 匹配的子元素，未找到返回 null
     */
    private Element getChildNS(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE
                    && ns.equals(n.getNamespaceURI())
                    && localName.equals(n.getLocalName())) {
                return (Element) n;
            }
        }
        return null;
    }

    /**
     * XML attribute 值最小化转义（仅处理 &amp;、引号、尖括号）。
     *
     * @param s 原始字符串
     * @return 转义后字符串
     */
    private String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
