package com.ghartur.spring_ai_study.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Profile("rag")
public class ChatMemoryController {

    private final ChatClient chatClient;

    public ChatMemoryController(@Qualifier("chatMemoryChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/chat-memory")
    public String chat(@RequestHeader("username") String username,
                       @RequestParam String message){
        return chatClient
                .prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, username))
                .call()
                .content();
    }

}
