package com.ghartur.mcpclient.controller;

import com.ghartur.mcpclient.util.ToolUtil;
import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class MCPClientController {

    private final ChatClient chatClient;

    private final List<McpSyncClient> mcpClients;

    public MCPClientController(ChatClient.Builder chatClientBuilder,
//                               ToolCallbackProvider toolCallbackProvider
                               List<McpSyncClient> mcpClients
    ) {
        this.chatClient = chatClientBuilder
//                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

        this.mcpClients = mcpClients;
    }

    @GetMapping("/chat")
    public String chat(@RequestHeader(required = false) String username,
                       @RequestParam String message) {

        ToolCallback[] toolCallbacks = ToolUtil.selectToolsFor(mcpClients, "helpdesk-mcp-server");

        return chatClient
                .prompt()
                .tools(toolCallbacks)
                .toolContext(Map.of("progressToken", UUID.randomUUID().toString()))
                .user(message + " username: "+username)
                .call().content();
    }
}
