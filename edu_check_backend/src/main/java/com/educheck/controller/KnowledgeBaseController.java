package com.educheck.controller;

import com.educheck.common.Result;
import com.educheck.entity.KnowledgeBase;
import com.educheck.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/kb")
@RequiredArgsConstructor
@Tag(name = "知识库管理", description = "知识库增删改（自动触发 RAG 重建）")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @Operation(summary = "新增知识库条目（自动同步到 RAG 索引）")
    public Result<KnowledgeBase> add(@RequestBody Map<String, String> body) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setCategory(body.getOrDefault("category", "通用"));
        kb.setQuestion(body.get("question"));
        kb.setAnswer(body.get("answer"));
        kb.setKeywords(body.getOrDefault("keywords", ""));
        kb.setSortOrder(999);

        knowledgeBaseService.save(kb);
        return Result.success(kb);
    }

    @DeleteMapping("/base/{id}")
    @Operation(summary = "删除知识库条目（自动同步到 RAG 索引）")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.removeById(id);
        return Result.success(null);
    }
}
