package com.educheck.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.educheck.common.Result;
import com.educheck.common.TokenContextHolder;
import com.educheck.entity.Announcement;
import com.educheck.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.jdbc.core.JdbcTemplate;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@Tag(name = "公告管理", description = "公告CRUD接口")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final TokenContextHolder tokenContextHolder;
    private final JdbcTemplate jdbcTemplate;

    private static final Map<String, String> TYPE_NAME_MAP = Map.of(
            "notice", "通知公告",
            "activity", "活动预告",
            "academic", "学术讲座",
            "policy", "思政教育"
    );

    /** 获取公告列表（分页+分类+搜索） */
    @GetMapping
    @Operation(summary = "获取公告列表")
    public Result<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .orderByDesc(Announcement::getIsTop)
                .orderByDesc(Announcement::getCreatedAt);

        if (type != null && !type.isEmpty()) {
            wrapper.eq(Announcement::getType, type);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Announcement::getTitle, keyword);
        }

        Page<Announcement> p = announcementService.page(new Page<>(page, size), wrapper);
        return Result.success(toPageResult(p));
    }

    /** 获取置顶公告 */
    @GetMapping("/top")
    @Operation(summary = "获取置顶公告")
    public Result<Map<String, Object>> getTop() {
        Announcement top = announcementService.lambdaQuery()
                .eq(Announcement::getIsTop, 1)
                .eq(Announcement::getStatus, 1)
                .orderByDesc(Announcement::getCreatedAt)
                .last("LIMIT 1")
                .one();
        if (top == null) return Result.success(null);
        return Result.success(toMap(top));
    }

    /** 获取公告详情（自动记录阅读） */
    @GetMapping("/{id}")
    @Operation(summary = "获取公告详情")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Announcement a = announcementService.getById(id);
        if (a == null) return Result.error("公告不存在");
        // 增加阅读数
        jdbcTemplate.update("UPDATE announcement SET read_count = read_count + 1 WHERE id = ?", id);
        a.setReadCount((a.getReadCount() != null ? a.getReadCount() : 0) + 1);
        return Result.success(toMap(a));
    }

    /** 标记已读（带用户记录） */
    @PostMapping("/{id}/read")
    @Operation(summary = "标记公告已读")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = tokenContextHolder.requireCurrentUserId();
        jdbcTemplate.update(
            "INSERT IGNORE INTO announcement_read (announcement_id, user_id) VALUES (?, ?)", id, userId);
        jdbcTemplate.update("UPDATE announcement SET read_count = read_count + 1 WHERE id = ?", id);
        return Result.success(null);
    }

    /** 发布公告（教师） */
    @PostMapping
    @Operation(summary = "发布公告")
    public Result<Void> create(@RequestBody Map<String, Object> body) {
        Long userId = tokenContextHolder.requireCurrentUserId();
        String type = (String) body.getOrDefault("type", "notice");

        Announcement a = new Announcement();
        a.setUserId(userId);
        a.setTitle((String) body.get("title"));
        a.setType(type);
        a.setTypeName(TYPE_NAME_MAP.getOrDefault(type, type));
        a.setSummary((String) body.get("summary"));
        a.setContent((String) body.get("content"));
        a.setDepartment((String) body.getOrDefault("department", ""));
        a.setIsTop(body.get("isTop") != null && (Boolean) body.get("isTop") ? 1 : 0);
        a.setStatus(1);
        announcementService.save(a);
        return Result.success("发布成功", null);
    }

    /** 更新公告 */
    @PutMapping("/{id}")
    @Operation(summary = "更新公告")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Announcement a = announcementService.getById(id);
        if (a == null) return Result.error("公告不存在");

        if (body.containsKey("title")) a.setTitle((String) body.get("title"));
        if (body.containsKey("type")) {
            String type = (String) body.get("type");
            a.setType(type);
            a.setTypeName(TYPE_NAME_MAP.getOrDefault(type, type));
        }
        if (body.containsKey("summary")) a.setSummary((String) body.get("summary"));
        if (body.containsKey("content")) a.setContent((String) body.get("content"));
        if (body.containsKey("department")) a.setDepartment((String) body.get("department"));
        if (body.containsKey("isTop")) a.setIsTop((Boolean) body.get("isTop") ? 1 : 0);
        a.setUpdatedAt(LocalDateTime.now());
        announcementService.updateById(a);
        return Result.success("更新成功", null);
    }

    /** 删除公告 */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除公告")
    public Result<Void> delete(@PathVariable Long id) {
        if (!announcementService.removeById(id)) {
            return Result.error("公告不存在");
        }
        return Result.success("删除成功", null);
    }

    /** 教师获取所有公告 */
    @GetMapping("/teacher/list-all")
    @Operation(summary = "教师获取所有公告")
    public Result<Page<Map<String, Object>>> teacherList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Announcement> p = announcementService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<Announcement>()
                        .orderByDesc(Announcement::getCreatedAt));
        return Result.success(toPageResult(p));
    }

    // ====== 转换 ======

    private Map<String, Object> toMap(Announcement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("type", a.getType());
        m.put("typeName", a.getTypeName());
        m.put("summary", a.getSummary());
        m.put("content", a.getContent());
        m.put("department", a.getDepartment());
        m.put("isTop", a.getIsTop());
        m.put("readCount", a.getReadCount() != null ? a.getReadCount() : 0);
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private Page<Map<String, Object>> toPageResult(Page<Announcement> p) {
        List<Map<String, Object>> records = p.getRecords().stream()
                .map(this::toMap).collect(java.util.stream.Collectors.toList());
        Page<Map<String, Object>> r = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        r.setRecords(records);
        return r;
    }
}
