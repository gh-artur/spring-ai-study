package com.ghartur.spring_ai_study.config;

import com.ghartur.spring_ai_study.advisors.TokenUsageAuditAdvisor;
import org.springframework.ai.chat.cache.semantic.SemanticCacheAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration
@Profile("rag")
public class OpenChatClientConfig {

    @Bean("openChatClient")
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 SemanticCacheAdvisor semanticCacheAdvisor) {

        return chatClientBuilder
                .defaultAdvisors(List.of(new SimpleLoggerAdvisor(), new TokenUsageAuditAdvisor(), semanticCacheAdvisor))
                .build();
    }

}
