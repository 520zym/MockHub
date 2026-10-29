package com.mockhub.mock.lifecycle;
import com.mockhub.common.model.BizException;
/** Shared route normalization for manual writes, imports and restoration. */
public final class ApiPaths {
    private ApiPaths() { }
    public static String normalize(String path) {
        String value=forCheck(path);if(value==null)throw new BizException(40400,"接口路径不能为空");return value;
    }
    public static String forCheck(String path) {
        if(path==null)return null;String value=path.trim();
        int query=value.indexOf('?');if(query>=0)value=value.substring(0,query);
        int fragment=value.indexOf('#');if(fragment>=0)value=value.substring(0,fragment);
        value=value.trim();if(value.isEmpty())return null;return value.startsWith("/")?value:"/"+value;
    }
}
