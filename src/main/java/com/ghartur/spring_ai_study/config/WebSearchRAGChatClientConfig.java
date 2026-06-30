package com.ghartur.spring_ai_study.config;

import com.ghartur.spring_ai_study.advisors.TokenUsageAuditAdvisor;
import com.ghartur.spring_ai_study.rag.WebSearchDocumentRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;

@Configuration
public class WebSearchRAGChatClientConfig {

    @Bean(name = "webSearchRAGChatClient")
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 ChatMemory chatMemory,
                                 RestClient.Builder restClientBuilder) {

        SimpleLoggerAdvisor loggerAdvisor = SimpleLoggerAdvisor.builder().build();
        TokenUsageAuditAdvisor tokenAdvisor = new TokenUsageAuditAdvisor();
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        RetrievalAugmentationAdvisor webSearchRAGAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(WebSearchDocumentRetriever.builder()
                        .restClientBuilder(restClientBuilder)
                        .maxResults(5)
                        .build())
                .build();

        return chatClientBuilder
                .defaultAdvisors(List.of(loggerAdvisor, memoryAdvisor, tokenAdvisor, webSearchRAGAdvisor))
                .build();
    }

}
