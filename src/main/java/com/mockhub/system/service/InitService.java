package com.mockhub.system.service;

import com.mockhub.common.model.enums.UserRole;
import com.mockhub.common.util.PasswordUtil;
import com.mockhub.system.model.entity.User;
import com.mockhub.system.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 首次启动初始化服务
 * <p>
 * 应用启动时检查 user 表是否为空，如果为空则创建默认的超级管理员账号。
 * 默认账号：admin / admin123，首次登录后强制修改密码。
 */
@Service
public class InitService {

    private static final Logger log = LoggerFactory.getLogger(InitService.class);

    /** 默认管理员用户名 */
    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    /** 默认管理员密码 */
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    /** 默认管理员显示名称 */
    private static final String DEFAULT_ADMIN_DISPLAY_NAME = "超级管理员";

    @Autowired
    private UserRepository userRepository;

    /**
     * 启动后检查并初始化默认管理员
     */
    @PostConstruct
    public void init() {
        long userCount = userRepository.count();
        if (userCount > 0) {
            log.info("用户表已有 {} 条记录，跳过初始化", userCount);
            return;
        }

        log.info("用户表为空，开始创建默认超级管理员...");
        createDefaultAdmin();
    }

    /**
     * 创建默认超级管理员账号
     */
    private void createDefaultAdmin() {
        String now = LocalDateTime.now().toString();

        User admin = new User();
        admin.setId(UUID.randomUUID().toString());
        admin.setUsername(DEFAULT_ADMIN_USERNAME);
        admin.setPasswordHash(PasswordUtil.hash(DEFAULT_ADMIN_PASSWORD));
        admin.setDisplayName(DEFAULT_ADMIN_DISPLAY_NAME);
        admin.setGlobalRole(UserRole.SUPER_ADMIN.name());
        admin.setFirstLogin(true);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);

        userRepository.insert(admin);
        log.info("默认超级管理员已创建：username={}, password={}", DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
    }
}
