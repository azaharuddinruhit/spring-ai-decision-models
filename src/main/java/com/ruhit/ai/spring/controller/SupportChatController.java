package com.ruhit.ai.spring.controller;

import com.ruhit.ai.spring.guardrails.DecisionInputGuardrailAdvisor;
import com.ruhit.ai.spring.guardrails.DecisionOutputGuardrailAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ruhit
 * @since 19-Nov-26 4:17 PM
 */
@RestController
class SupportChatController {

    private static final String SYSTEM_PROMPT = """
            You are the customer support assistant of an online musical instrument store.
            Answer the question using only the store policy below.
            If the policy doesn't cover the question, say that you don't have that information.
            
            Store policy:
            - Standard shipping takes 3 to 5 business days and is free for orders over 50 euros.
            - Instruments and accessories can be returned within 30 days of delivery, in their original packaging.
            - Refunds are issued to the original payment method within 10 business days.
            """;

    private final ChatClient chatClient;

    SupportChatController(ChatClient.Builder chatClientBuilder, DecisionModel decisionModel) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new DecisionInputGuardrailAdvisor(decisionModel),
                        new DecisionOutputGuardrailAdvisor(decisionModel))
                .build();
    }

    @PostMapping("/support/chat")
    String chat(@RequestBody String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }


}
