package com.educheck.agent;

import com.educheck.entity.KnowledgeBase;
import com.educheck.mapper.KnowledgeBaseMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzh.BgeSmallZhEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库 RAG 索引管理。
 *
 * 企业级同步策略：
 * 1. 事件驱动：API 增删改时立即重建（KnowledgeBaseServiceImpl 调用 rebuild()）
 * 2. 定时对账：每 5 分钟检测 (count, max_updated_at) 是否一致，不一致则重建
 *    — count 变化 → 检测到增/删
 *    — max_updated_at 变化 → 检测到修改
 * 3. 持久化：全量重建后将序列化索引存入 MySQL，重启时恢复
 */
@Slf4j
@Service
public class KnowledgeBaseRag {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final JdbcTemplate jdbcTemplate;

    @Getter
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;

    @Getter
    private EmbeddingModel embeddingModel;

    /** 最近一次重建时的快照状态，用于对账检测变更 */
    private volatile int lastCount;
    private volatile String lastMaxUpdated;

    public KnowledgeBaseRag(KnowledgeBaseMapper knowledgeBaseMapper, JdbcTemplate jdbcTemplate) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        this.embeddingModel = new BgeSmallZhEmbeddingModel();
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        int actualCount = knowledgeBaseMapper.selectCount(null).intValue();

        // 尝试从 MySQL 恢复
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT kb_count, index_json FROM rag_index WHERE id = 1");

        if (!rows.isEmpty() && actualCount > 0) {
            Integer savedCount = (Integer) rows.get(0).get("kb_count");
            if (savedCount != null && savedCount == actualCount) {
                String json = (String) rows.get(0).get("index_json");
                this.embeddingStore = InMemoryEmbeddingStore.fromJson(json);
                log.info("RAG 索引从 MySQL 恢复: {} 条", actualCount);
                takeSnapshot();
                return;
            }
        }

        // 首次启动或数量不匹配，全量重建
        if (actualCount > 0) {
            rebuildInternal();
            persistAndSnapshot(actualCount);
        } else {
            log.warn("知识库为空，跳过 RAG 初始化");
        }
    }

    /** 定时对账：每 5 分钟检测知识库是否有变 */
    @Scheduled(fixedRate = 300_000)
    public void reconcile() {
        int count = knowledgeBaseMapper.selectCount(null).intValue();
        String maxUpdated = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(updated_at), '1970-01-01') FROM knowledge_base", String.class);

        if (count != lastCount || !java.util.Objects.equals(maxUpdated, lastMaxUpdated)) {
            log.info("知识库对账不一致: count={}→{}, updated={}→{}, 自动重建",
                    lastCount, count, lastMaxUpdated, maxUpdated);
            rebuildInternal();
            persistAndSnapshot(count);
        }
    }

    /** 全量重建（线程安全，供 Service 层和手动调用） */
    public synchronized void rebuild() {
        rebuildInternal();
        persistAndSnapshot(knowledgeBaseMapper.selectCount(null).intValue());
    }

    // ==================== 内部 ====================

    private synchronized void rebuildInternal() {
        long start = System.currentTimeMillis();
        if (embeddingStore != null) embeddingStore.removeAll();
        List<KnowledgeBase> list = knowledgeBaseMapper.selectList(null);
        if (list.isEmpty()) return;

        List<Document> documents = list.stream().map(kb -> {
            String text = kb.getQuestion() + "\n" + kb.getAnswer();
            Document doc = Document.from(text);
            doc.metadata().put("knowledge_id", kb.getId().toString());
            doc.metadata().put("category", kb.getCategory() != null ? kb.getCategory() : "");
            return doc;
        }).toList();

        EmbeddingStoreIngestor.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .documentSplitter(new DocumentBySentenceSplitter(500, 50))
                .build()
                .ingest(documents);

        log.info("RAG 重建完成: {} 条, {}ms", list.size(), System.currentTimeMillis() - start);
    }

    private void persistAndSnapshot(int count) {
        try {
            String json = embeddingStore.serializeToJson();
            jdbcTemplate.update(
                "REPLACE INTO rag_index (id, kb_count, index_json, updated_at) VALUES (1, ?, ?, NOW())",
                count, json);
        } catch (Exception e) {
            log.warn("RAG 索引持久化失败: {}", e.getMessage());
        }
        takeSnapshot();
    }

    private void takeSnapshot() {
        int count = knowledgeBaseMapper.selectCount(null).intValue();
        String maxUpdated = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(updated_at), '1970-01-01') FROM knowledge_base", String.class);
        this.lastCount = count;
        this.lastMaxUpdated = maxUpdated;
    }
}
