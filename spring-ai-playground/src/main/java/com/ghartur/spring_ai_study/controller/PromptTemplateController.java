package com.ghartur.spring_ai_study.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PromptTemplateController {

    private final ChatClient chatClient;

    public PromptTemplateController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Value("classpath:/promptTemplates/userPromptTemplate.st")
    Resource userPromptTemplate;

    @GetMapping("/email")
    public String emailResponse(@RequestParam("customerName") String customerName,
                       @RequestParam("customerMessage") String customerMessage){
        return chatClient
                .prompt()
                .system("""
                        Vocês é especialista em carros, só responda perguntas onde o usuário quer tirar alguma dúvida sobre carros
                        """)
                .user(promptUserSpec ->
                        promptUserSpec.text(userPromptTemplate)
                                .param("customerName", customerName)
                                .param("customerMessage", customerMessage))
                .call().content();
    }
}
