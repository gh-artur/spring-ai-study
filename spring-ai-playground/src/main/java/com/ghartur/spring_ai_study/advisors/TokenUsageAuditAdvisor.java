package com.ghartur.spring_ai_study.advisors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

public class TokenUsageAuditAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(TokenUsageAuditAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {

        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

        logUsage(chatClientResponse);

        return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {

        // No streaming a resposta vem em pedaços (Flux); o Usage só costuma vir
        // preenchido no último chunk, por isso inspecionamos cada elemento emitido.
        return streamAdvisorChain.nextStream(chatClientRequest)
                .doOnNext(this::logUsage);
    }

    private void logUsage(ChatClientResponse chatClientResponse) {
        ChatResponse chatResponse = chatClientResponse.chatResponse();

        if (chatResponse != null && chatResponse.getMetadata().getUsage() != null) {
            Usage usage = chatResponse.getMetadata().getUsage();

            logger.info("Token usage details: {}", usage.toString());
        }
    }

    @Override
    public String getName() {
        return "TokenUsageAuditAdvisor";
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
