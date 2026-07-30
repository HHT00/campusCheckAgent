package com.educheck.agent;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    /** 每个用户独立保持最近 20 条对话记忆 */
    @Bean
    ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(20);
    }

    @Bean
    public ContentRetriever contentRetriever(KnowledgeBaseRag kbRag) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(kbRag.getEmbeddingStore())
                .embeddingModel(kbRag.getEmbeddingModel())
                .maxResults(3)
                .minScore(0.0)
                .build();
    }
}
