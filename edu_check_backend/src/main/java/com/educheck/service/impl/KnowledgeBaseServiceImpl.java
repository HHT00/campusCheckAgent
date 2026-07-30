package com.educheck.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.educheck.entity.KnowledgeBase;
import com.educheck.mapper.KnowledgeBaseMapper;
import com.educheck.service.KnowledgeBaseService;
import com.educheck.agent.KnowledgeBaseRag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;

/**
 * 知识库 Service——增删改后自动触发 RAG 索引重建
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl
        extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    private final KnowledgeBaseRag knowledgeBaseRag;

    public KnowledgeBaseServiceImpl(KnowledgeBaseRag knowledgeBaseRag) {
        this.knowledgeBaseRag = knowledgeBaseRag;
    }

    @Override
    public boolean save(KnowledgeBase entity) {
        boolean result = super.save(entity);
        if (result) knowledgeBaseRag.rebuild();
        return result;
    }

    @Override
    public boolean updateById(KnowledgeBase entity) {
        boolean result = super.updateById(entity);
        if (result) knowledgeBaseRag.rebuild();
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        if (result) knowledgeBaseRag.rebuild();
        return result;
    }
}
