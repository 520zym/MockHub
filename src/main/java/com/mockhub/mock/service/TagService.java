package com.mockhub.mock.service;

import com.mockhub.common.model.BizException;
import com.mockhub.common.util.PermissionChecker;
import com.mockhub.mock.model.entity.Tag;
import com.mockhub.mock.repository.ApiTagRepository;
import com.mockhub.mock.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 标签 Service
 * <p>
 * 提供标签的 CRUD 操作。
 * 权限规则：
 * <ul>
 *   <li>创建：普通成员可创建</li>
 *   <li>修改/删除：需要团队管理员权限</li>
 *   <li>删除时自动清理 api_tag 关联记录</li>
 * </ul>
 */
@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;
    private final ApiTagRepository apiTagRepository;
    private final PermissionChecker permissionChecker;

    public TagService(TagRepository tagRepository,
                      ApiTagRepository apiTagRepository,
                      PermissionChecker permissionChecker) {
        this.tagRepository = tagRepository;
        this.apiTagRepository = apiTagRepository;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 查询团队的所有标签
     *
     * @param teamId 团队 ID
     * @return 标签列表（按名称升序）
     */
    public List<Tag> findByTeamId(String teamId) {
        permissionChecker.checkTeamAccess(teamId);
        return tagRepository.findByTeamId(teamId);
    }

    /**
     * 创建标签
     * <p>
     * 普通成员可创建标签，只需团队访问权限。
     *
     * @param tag 标签对象（需包含 teamId、name、color）
     * @return 创建后的标签对象（含生成的 ID）
     */
    public Tag create(Tag tag) {
        permissionChecker.checkTeamAccess(tag.getTeamId());

        tag.setId(UUID.randomUUID().toString());

        tagRepository.insert(tag);
        log.info("创建标签: id={}, teamId={}, name={}, color={}", tag.getId(), tag.getTeamId(), tag.getName(), tag.getColor());

        return tag;
    }

    /**
     * 更新标签
     * <p>
     * 需要团队管理员权限。
     *
     * @param id  标签 ID
     * @param tag 更新内容（name、color）
     * @return 更新后的标签对象
     */
    public Tag update(String id, Tag tag) {
        Tag existing = tagRepository.findById(id);
        if (existing == null) {
            throw new BizException(40601, "标签不存在");
        }

        // 修改标签需要团队管理员权限
        permissionChecker.checkTeamAdmin(existing.getTeamId());

        existing.setName(tag.getName());
        existing.setColor(tag.getColor());

        tagRepository.update(existing);
        log.info("更新标签: id={}, name={}, color={}", id, existing.getName(), existing.getColor());

        return existing;
    }

    /**
     * 删除标签
     * <p>
     * 需要团队管理员权限。删除时自动清理 api_tag 关联记录。
     *
     * @param id 标签 ID
     */
    public void delete(String id) {
        Tag tag = tagRepository.findById(id);
        if (tag == null) {
            throw new BizException(40601, "标签不存在");
        }

        // 删除标签需要团队管理员权限
        permissionChecker.checkTeamAdmin(tag.getTeamId());

        // 清理 api_tag 关联记录
        apiTagRepository.deleteByTagId(id);
        log.info("清除标签关联: tagId={}", id);

        tagRepository.deleteById(id);
        log.info("删除标签: id={}, name={}", id, tag.getName());
    }
}
