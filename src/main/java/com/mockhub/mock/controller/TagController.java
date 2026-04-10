package com.mockhub.mock.controller;

import com.mockhub.common.model.Result;
import com.mockhub.mock.model.entity.Tag;
import com.mockhub.mock.service.TagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签管理 Controller
 * <p>
 * 提供标签的 CRUD 端点。
 * 创建：普通成员可创建；修改/删除：需要团队管理员权限。
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private static final Logger log = LoggerFactory.getLogger(TagController.class);

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /**
     * 查询团队的标签列表
     *
     * @param teamId 团队 ID
     * @return 标签列表
     */
    @GetMapping
    public Result<List<Tag>> list(@RequestParam String teamId) {
        List<Tag> tags = tagService.findByTeamId(teamId);
        return Result.ok(tags);
    }

    /**
     * 创建标签
     *
     * @param tag 标签对象（需包含 teamId、name、color）
     * @return 创建后的标签对象
     */
    @PostMapping
    public Result<Tag> create(@RequestBody Tag tag) {
        Tag created = tagService.create(tag);
        return Result.ok(created);
    }

    /**
     * 更新标签（需团队管理员权限）
     *
     * @param id  标签 ID
     * @param tag 更新内容（name、color）
     * @return 更新后的标签对象
     */
    @PutMapping("/{id}")
    public Result<Tag> update(@PathVariable String id, @RequestBody Tag tag) {
        Tag updated = tagService.update(id, tag);
        return Result.ok(updated);
    }

    /**
     * 删除标签（需团队管理员权限）
     * <p>
     * 自动清理 api_tag 关联记录。
     *
     * @param id 标签 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        tagService.delete(id);
        return Result.ok();
    }
}
