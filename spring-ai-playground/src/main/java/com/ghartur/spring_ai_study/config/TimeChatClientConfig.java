package com.ghartur.spring_ai_study.config;

import com.ghartur.spring_ai_study.advisors.TokenUsageAuditAdvisor;
import com.ghartur.spring_ai_study.rag.PIIMaskingDocumentPostProcessor;
import com.ghartur.spring_ai_study.tools.TimeTools;
import org.springframework.ai.chat.cache.semantic.SemanticCacheAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class TimeChatClientConfig {

    @Bean(name = "timeChatClient")
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 ChatMemory chatMemory,
                                 TimeTools timeTools) {

        SimpleLoggerAdvisor loggerAdvisor = SimpleLoggerAdvisor.builder().build();
        TokenUsageAuditAdvisor tokenAdvisor = new TokenUsageAuditAdvisor();
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        return chatClientBuilder
                .defaultTools(timeTools)
                .defaultAdvisors(List.of(loggerAdvisor, memoryAdvisor, tokenAdvisor))
                .build();
    }

}
