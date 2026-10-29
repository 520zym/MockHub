package com.mockhub.common.util;

import com.mockhub.common.model.CurrentUser;

/**
 * 为认证提供当前数据库身份；由 system 模块实现，common 不直接依赖业务仓储。
 */
public interface CurrentUserProvider {

    /**
     * 每次请求重新读取账号及当前团队范围，账号不存在或身份无效时返回 null。
     * 数据库读取异常应向上传播，不允许回退到 JWT 中的旧角色/团队。
     */
    CurrentUser findById(String userId);
}
