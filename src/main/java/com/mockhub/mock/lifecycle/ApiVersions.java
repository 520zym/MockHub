package com.mockhub.mock.lifecycle;
import com.mockhub.common.model.BizException;
public final class ApiVersions {
    private ApiVersions() { }
    public static long require(Long revision) {
        if (revision == null || revision < 1) throw new BizException(40920, "EXPECTED_REVISION_REQUIRED: 请刷新页面升级后重试");
        return revision;
    }
    public static BizException conflict() { return new BizException(40921, "REVISION_CONFLICT: 接口已被修改，请刷新后重试"); }
}
