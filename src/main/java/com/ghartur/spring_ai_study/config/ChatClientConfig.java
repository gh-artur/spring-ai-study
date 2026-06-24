package com.ghartur.spring_ai_study.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultSystem("""
                        Vocês é especialista em queijos, só responda perguntas onde o usuário quer tirar alguma dúvida sobre queijos
                        """)
                .build();
    }
}
