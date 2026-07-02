package com.ghartur.spring_ai_study.config;

import com.ghartur.spring_ai_study.advisors.TokenUsageAuditAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
         OpenAiChatOptions.Builder openAiChatOptions = OpenAiChatOptions.builder()
                .model("gpt-5.4-mini")
                .temperature(0.8);

        return chatClientBuilder
                .defaultOptions(openAiChatOptions)
                .defaultAdvisors(List.of(new TokenUsageAuditAdvisor()))
                .defaultSystem("""
                        Vocês é especialista em queijos, só responda perguntas onde o usuário quer tirar alguma dúvida sobre queijos
                        """)
                .build();
    }
}
