package com.ghartur.spring_ai_study.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Profile("rag")
public class OpenChatController {

    ChatClient chatClient;

    public OpenChatController(@Qualifier("openChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/open-chat")
    public String chat(@RequestParam String message){
        return chatClient
                .prompt()
                .user(message)
                .call().content();
    }

}
