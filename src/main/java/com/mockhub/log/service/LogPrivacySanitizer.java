package com.mockhub.log.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.mockhub.log.model.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.*;

/** Read/prompt copies only: never mutate stored snapshots or the live HTTP matcher inputs. */
public final class LogPrivacySanitizer {
    public static final String REDACTED="[REDACTED]";
    public static final String OPAQUE_BODY="[REDACTED_UNSTRUCTURED_BODY]";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Set<String> SECRET=new HashSet<String>(Arrays.asList("authorization","proxyauthorization","cookie","setcookie","xapikey","apikey","xauthtoken","xtoken","password","passwd","pwd","secret","clientsecret","secretkey","privatekey","token","accesstoken","refreshtoken","idtoken","authtoken","sessiontoken","sessionid","jwt","sid","auth"));
    private static final String LABEL="authorization|proxy[-_ ]?authorization|cookie|set[-_ ]?cookie|x[-_ ]?api[-_ ]?key|api[-_ ]?key|x[-_ ]?auth[-_ ]?token|x[-_ ]?token|password|passwd|pwd|client[-_ ]?secret|secret[-_ ]?key|private[-_ ]?key|secret|(?:access|refresh|id|auth|session)[-_ ]?token|session[-_ ]?id|token|jwt|sid|auth";
    private static final Pattern AUTH=Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=\\-]+(?![A-Za-z0-9])");
    private static final Pattern HEADERS=Pattern.compile("(?im)(\\b(?:Authorization|Proxy-Authorization|Cookie|Set-Cookie|X-API-Key|API-Key)\\s*:\\s*)[^\\r\\n]+");
    private static final Pattern ASSIGN=Pattern.compile("(?i)((?:[\\\"']?)(?:"+LABEL+")(?:[\\\"']?)\\s*[:=]\\s*)(\\\"(?:\\\\.|[^\\\"\\\\])*(?:\\\"|\\z)|'(?:\\\\.|[^'\\\\])*(?:'|\\z)|[^\\s&;,}\\r\\n]+)");
    private static final Pattern QUERY=Pattern.compile("([?&#])([^=&#\\s]+)=([^&#\\s]*)");
    private static final Pattern USERINFO=Pattern.compile("(?i)(https?://)[^/@\\s]+:[^/@\\s]+@");
    private LogPrivacySanitizer(){}
    public static boolean sensitiveKey(String key){
        if(key==null)return false;String value=key;
        try{for(int i=0;i<3 && value.indexOf('%')>=0;i++)value=URLDecoder.decode(value,"UTF-8");}catch(Exception malformed){return true;}
        return SECRET.contains(value.replaceAll("[^A-Za-z0-9]","").toLowerCase(Locale.ROOT));
    }
    public static boolean sensitivePath(String path){if(sensitiveKey(path))return true;if(path!=null)for(String field:path.split("[.\\[\\]]"))if(sensitiveKey(field))return true;return false;}
    public static boolean hasRedaction(String value){return value!=null && (value.contains("[REDACTED") || value.contains("%5BREDACTED"));}
    public static String redactText(String value){
        if(value==null)return null;
        String text=USERINFO.matcher(value).replaceAll("$1"+REDACTED+"@");
        Matcher query=QUERY.matcher(text);StringBuffer clean=new StringBuffer();
        while(query.find())query.appendReplacement(clean,Matcher.quoteReplacement(sensitiveKey(query.group(2))?query.group(1)+query.group(2)+"="+REDACTED:query.group()));query.appendTail(clean);text=clean.toString();
        text=AUTH.matcher(text).replaceAll("$1 "+REDACTED);
        text=HEADERS.matcher(text).replaceAll("$1"+REDACTED);
        Matcher fields=ASSIGN.matcher(text);clean=new StringBuffer();
        while(fields.find()){String old=fields.group(2),replacement=old.startsWith("\"")?"\""+REDACTED+"\"":old.startsWith("'")?"'"+REDACTED+"'":REDACTED;fields.appendReplacement(clean,Matcher.quoteReplacement(fields.group(1)+replacement));}fields.appendTail(clean);return clean.toString();
    }
    private static JsonNode parseJson(String value)throws java.io.IOException {
        try(com.fasterxml.jackson.core.JsonParser parser=JSON.getFactory().createParser(value)){JsonNode root=JSON.readTree(parser);if(parser.nextToken()!=null)throw new java.io.IOException("Trailing JSON input");return root;}
    }
    public static String redactJson(String value,String opaque){
        if(value==null || value.trim().isEmpty())return value;
        try{JsonNode root=parseJson(value);if(root==null)return opaque;return JSON.writeValueAsString(redactTree(root));}catch(Exception malformed){return opaque;}
    }
    private static JsonNode redactTree(JsonNode node){
        if(node.isObject()){ObjectNode out=JSON.createObjectNode();Iterator<Map.Entry<String,JsonNode>> it=node.fields();while(it.hasNext()){Map.Entry<String,JsonNode> e=it.next();out.set(e.getKey(),sensitiveKey(e.getKey())?TextNode.valueOf(REDACTED):redactTree(e.getValue()));}return out;}
        if(node.isArray()){ArrayNode out=JSON.createArrayNode();for(JsonNode item:node)out.add(redactTree(item));return out;}
        return node.isTextual()?TextNode.valueOf(redactPrompt(node.asText())):node.deepCopy();
    }
    public static String redactParams(String value){
        if(value==null || value.trim().isEmpty())return value;
        try{JsonNode root=parseJson(value);if(root!=null && root.isObject())return JSON.writeValueAsString(redactTree(root));}catch(Exception ignored){}
        try{
            if(value.indexOf('=')<0)throw new IllegalArgumentException();ObjectNode out=JSON.createObjectNode();
            for(String pair:value.replaceFirst("^[?]","").split("&")){String[] part=pair.split("=",2);String key=URLDecoder.decode(part[0],"UTF-8");out.put(key,sensitiveKey(key)?REDACTED:redactText(URLDecoder.decode(part.length>1?part[1]:"","UTF-8")));}
            return JSON.writeValueAsString(out);
        }catch(Exception invalid){return "{\"_redacted\":\"[REDACTED_INVALID_QUERY]\"}";}
    }
    public static String redactPrompt(String value){
        if(value==null)return null;
        try{JsonNode parsed=parseJson(value);if(parsed!=null && (parsed.isObject()||parsed.isArray()))return JSON.writeValueAsString(redactTree(parsed));}catch(Exception ignored){}
        StringBuilder out=new StringBuilder();int cursor=0;
        while(cursor<value.length()){
            char first=value.charAt(cursor);
            if(first=='{' || first=='['){
                int end=jsonEnd(value,cursor);
                if(end>=cursor){try{JsonNode tree=parseJson(value.substring(cursor,end+1));if(tree!=null && (tree.isObject()||tree.isArray())){out.append(JSON.writeValueAsString(redactTree(tree)));cursor=end+1;continue;}}catch(Exception ignored){}}
            }
            out.append(first);cursor++;
        }
        return redactText(out.toString());
    }
    private static int jsonEnd(String value,int start){
        java.util.Deque<Character> stack=new java.util.ArrayDeque<Character>();boolean quoted=false,escaped=false;
        for(int i=start;i<value.length();i++){char c=value.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;continue;}if(c=='\"'){quoted=true;continue;}if(c=='{'||c=='[')stack.push(c);else if(c=='}'||c==']'){if(stack.isEmpty() || (c=='}'&&stack.pop()!='{') || (c==']'&&stack.pop()!='['))return -1;if(stack.isEmpty())return i;}}
        return -1;
    }
    public static RequestLog requestCopy(RequestLog raw){
        RequestLog out=new RequestLog();out.setId(raw.getId());out.setTeamId(raw.getTeamId());out.setApiId(raw.getApiId());out.setMethod(raw.getMethod());out.setResponseCode(raw.getResponseCode());out.setDurationMs(raw.getDurationMs());out.setCreatedAt(raw.getCreatedAt());
        out.setApiPath(redactText(raw.getApiPath()));out.setRequestHeaders(redactJson(raw.getRequestHeaders(),"{\"_redacted\":\"[REDACTED_INVALID_HEADERS]\"}"));out.setRequestParams(redactParams(raw.getRequestParams()));out.setRequestBody(redactJson(raw.getRequestBody(),OPAQUE_BODY));out.setRedacted(true);return out;
    }
    public static OperationLog operationCopy(OperationLog raw){
        OperationLog out=new OperationLog();out.setId(raw.getId());out.setTeamId(raw.getTeamId());out.setUserId(raw.getUserId());out.setUsername(redactText(raw.getUsername()));out.setAction(raw.getAction());out.setTargetType(raw.getTargetType());out.setTargetId(raw.getTargetId());out.setTargetName(redactText(raw.getTargetName()));out.setDetail(redactPrompt(raw.getDetail()));out.setCreatedAt(raw.getCreatedAt());return out;
    }
}
