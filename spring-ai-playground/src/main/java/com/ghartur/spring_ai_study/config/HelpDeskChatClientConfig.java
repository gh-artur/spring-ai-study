package com.ghartur.spring_ai_study.config;

import com.ghartur.spring_ai_study.advisors.TokenUsageAuditAdvisor;
import com.ghartur.spring_ai_study.tools.TimeTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.List;

@Configuration
public class HelpDeskChatClientConfig {

    @Value("classpath:/promptTemplates/helpDeskSystemPromptTemplate.st")
    Resource helpDeskPromptTemplate;

    @Bean(name = "helpDeskChatClient")
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder,
                                 ChatMemory chatMemory,
                                 TimeTools timeTools) {

        SimpleLoggerAdvisor loggerAdvisor = SimpleLoggerAdvisor.builder().build();
        TokenUsageAuditAdvisor tokenAdvisor = new TokenUsageAuditAdvisor();
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();

        return chatClientBuilder
                .defaultSystem(helpDeskPromptTemplate)
                .defaultTools(timeTools)
                .defaultAdvisors(List.of(loggerAdvisor, memoryAdvisor, tokenAdvisor))
                .build();
    }

}
