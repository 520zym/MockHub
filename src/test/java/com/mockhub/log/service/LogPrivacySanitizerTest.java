package com.mockhub.log.service;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogPrivacySanitizerTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void headersAreCaseInsensitiveAndStructuredBodyKeepsOrdinaryPayload()throws Exception{
        String headers="{\"authorization\":\"Bearer HEADER_SECRET\",\"Proxy-Authorization\":\"Basic PROXY_SECRET\",\"Cookie\":\"COOKIE_SECRET\",\"Set-Cookie\":[\"SET_COOKIE_SECRET\"],\"X-API-Key\":\"XKEY_SECRET\",\"API-Key\":\"APIKEY_SECRET\",\"X-Business\":\"kept\"}";
        String out=LogPrivacySanitizer.redactJson(headers,"FAIL");for(String secret:new String[]{"HEADER_SECRET","PROXY_SECRET","COOKIE_SECRET","SET_COOKIE_SECRET","XKEY_SECRET","APIKEY_SECRET"})assertFalse(out.contains(secret),out);assertEquals("kept",json.readTree(out).path("X-Business").asText());
        String body=LogPrivacySanitizer.redactJson("{\"password\":\"BODY_SECRET\",\"nested\":{\"access_token\":\"TOKEN_SECRET\",\"orderId\":123},\"items\":[{\"secret\":\"NESTED_SECRET\",\"name\":\"ok\"}],\"tokenCount\":42,\"passwordPolicy\":\"strict\"}","FAIL");
        assertFalse(body.contains("BODY_SECRET"));assertFalse(body.contains("TOKEN_SECRET"));assertFalse(body.contains("NESTED_SECRET"));assertEquals(123,json.readTree(body).path("nested").path("orderId").asInt());assertEquals(42,json.readTree(body).path("tokenCount").asInt());assertEquals("strict",json.readTree(body).path("passwordPolicy").asText());
    }
    @Test void urlAndQueryMaskEncodedCommonSecretFieldsWithoutDestroyingNormalValues()throws Exception{
        String path=LogPrivacySanitizer.redactText("https://person:USERINFO_SECRET@example.test/path?access%5Ftoken=URL_SECRET&password=PASSWORD_SECRET&amount=99");assertFalse(path.contains("USERINFO_SECRET"));assertFalse(path.contains("URL_SECRET"));assertFalse(path.contains("PASSWORD_SECRET"));assertTrue(path.contains("amount=99"));
        String params=LogPrivacySanitizer.redactParams("token=QUERY_SECRET&name=ordinary&client_secret=CLIENT_SECRET");assertFalse(params.contains("QUERY_SECRET"));assertFalse(params.contains("CLIENT_SECRET"));assertEquals("ordinary",json.readTree(params).path("name").asText());
    }
    @Test void parseFailuresNeverReturnOriginalAndNonJsonBodyHasReadableOpaqueMarker(){
        assertEquals(LogPrivacySanitizer.OPAQUE_BODY,LogPrivacySanitizer.redactJson("password=UNSTRUCTURED_SECRET",LogPrivacySanitizer.OPAQUE_BODY));
        assertFalse(LogPrivacySanitizer.redactParams("%ZZ=BAD_QUERY_SECRET").contains("BAD_QUERY_SECRET"));
        assertEquals("HIDDEN",LogPrivacySanitizer.redactJson("{broken:HEADER_SECRET}","HIDDEN"));
        assertFalse(LogPrivacySanitizer.redactJson("{\"message\":\"{\\\"token\\\":[\\\"EMBEDDED_SECRET\\\",\\\"SECOND_EMBEDDED_SECRET\\\"]}\"}","HIDDEN").contains("EMBEDDED_SECRET"));
        assertEquals("HIDDEN",LogPrivacySanitizer.redactJson("{\"valid\":true} trailing secret=TRAILING_SECRET","HIDDEN"));
    }
    @Test void freeFormAndEmbeddedJsonPromptsAreMaskedAndIdempotent(){
        String text="分析日志\nAuthorization: Bearer PROMPT_HEADER\nCookie: sid=COOKIE_PROMPT; other=too\n输入：{\"token\":[\"ARRAY_SECRET\",\"SECOND_SECRET\"],\"orderId\":123}\n请求：https://x/path?password=QUERY_PROMPT&order=77\npassword='TWO WORD SECRET'";
        String clean=LogPrivacySanitizer.redactPrompt(text);for(String secret:new String[]{"PROMPT_HEADER","COOKIE_PROMPT","ARRAY_SECRET","SECOND_SECRET","QUERY_PROMPT","TWO WORD SECRET"})assertFalse(clean.contains(secret),clean);
        assertFalse(LogPrivacySanitizer.redactPrompt("日志：{\"password\":\"TRUNCATED_SECRET with spaces").contains("TRUNCATED_SECRET"));
        assertTrue(clean.contains("orderId"));assertTrue(clean.contains("123"));assertTrue(clean.contains("order=77"));assertEquals(clean,LogPrivacySanitizer.redactPrompt(clean));
    }
}
