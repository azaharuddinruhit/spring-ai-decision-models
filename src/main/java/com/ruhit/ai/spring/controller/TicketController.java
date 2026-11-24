package com.ruhit.ai.spring.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Ruhit
 * @since 19-Nov-26 3:09 PM
 */
@RestController
@RequestMapping("/tickets")
class TicketController {

    private final DecisionModel decisionModel;

    TicketController(DecisionModel decisionModel) {
        this.decisionModel = decisionModel;
    }

    @PostMapping("/on-topic")
    YesNoAnswer onTopic(@NotBlank @RequestBody String text) {
        return decisionModel.call(text, Map.of("onTopic", YesNoQuestion.builder()
                        .text("Is this message something the customer support of an online musical instrument store can help with?")
                        .whenYes("It asks about buying or owning an instrument or an accessory, or about orders, shipping, returns, billing, or the customer's account")
                        .whenNo("It is about anything else, such as cooking, travel, homework, music history, or general knowledge")
                        .build()))
                .getYesNo("onTopic");
    }

    @PostMapping("/on-topic/threshold")
    boolean onTopicThreshold(@NotBlank @RequestBody String text,
                             @RequestParam(defaultValue = "0.5") double threshold) {
        return decisionModel.call(text, Map.of("onTopic", YesNoQuestion.builder()
                        .text("Is this message something the customer support of an online musical instrument store can help with?")
                        .whenYes("It asks about buying or owning an instrument or an accessory, or about orders, shipping, returns, billing, or the customer's account")
                        .whenNo("It is about anything else, such as cooking, travel, homework, music history, or general knowledge")
                        .build()))
                .getYesNo("onTopic").isYes(threshold);
    }

    @PostMapping("/category")
    ChoiceAnswer category(@NotBlank @RequestBody String text) {
        return decisionModel.call(text, Map.of("category", ChoiceQuestion.builder()
                        .text("Which team should handle this support ticket?")
                        .option("billing", "Payments, invoices, refunds already issued, and duplicate charges")
                        .option("shipping", "Late, lost, or damaged deliveries, and changes to a delivery address")
                        .option("returns", "Sending an instrument back, or asking for a replacement or a refund")
                        .option("technical", "An instrument or piece of gear that doesn't work, or help with setting it up")
                        .option("account", "The customer's account on the store's website: logging in, resetting its password, and managing personal details")
                        .option("other", "None of the above")
                        .build()))
                .getChoice("category");

    }

    @PostMapping("/sentiment")
    ScoreAnswer sentiment(@NotBlank @RequestBody String text) {
        return decisionModel.call(text, Map.of("sentiment", ScoreQuestion.builder()
                        .text("How does the customer feel?")
                        .level("angry")
                        .level("frustrated")
                        .level("neutral")
                        .level("happy")
                        .build()))
                .getScore("sentiment");
    }

    @PostMapping("/triage")
    TriageResult triage(@Valid @RequestBody TriageRequest request) {

        var ticket = new LinkedHashMap<String, Object>();
        ticket.put("subject", request.subject());
        ticket.put("body", request.body());
        ticket.put("customer", request.customer());

        var categoryQuestion = ChoiceQuestion.builder()
                .text("Which team should handle this support ticket?")
                .option("billing", "Payments, invoices, refunds already issued, and duplicate charges")
                .option("shipping", "Late, lost, or damaged deliveries, and changes to a delivery address")
                .option("returns", "Sending an instrument back, or asking for a replacement or a refund")
                .option("technical", "An instrument or piece of gear that doesn't work, or help with setting it up")
                .option("account", "The customer's account on the store's website: logging in, resetting its password, and managing personal details")
                .option("other", "None of the above")
                .build();

        var sentimentQuestion = ScoreQuestion.builder()
                .text("How does the customer feel?")
                .level("angry")
                .level("frustrated")
                .level("neutral")
                .level("happy")
                .build();

        var urgencyQuestion = ScoreQuestion.builder()
                .text("How urgently does this ticket need a reply, given its content and the type of customer?")
                .level("low", "A general question that can wait a few days")
                .level("normal", "A problem that should be handled within a day")
                .level("high", "The customer is blocked right now, especially when they are a music school")
                .build();

        var response = decisionModel.call(DecisionPrompt.builder()
                .fields(ticket)
                .question("category", categoryQuestion)
                .question("sentiment", sentimentQuestion)
                .question("urgency", urgencyQuestion)
                .build());

        var category = response.getChoice("category").getValue();
        var sentiment = sentimentQuestion.getLevelLabels().get(response.getScore("sentiment").getNearestLevel());
        var urgency = urgencyQuestion.getLevelLabels().get(response.getScore("urgency").getNearestLevel());

        var queue = "angry".equals(sentiment) ? "escalated" : category;

        return new TriageResult(queue, category, sentiment, urgency);
    }

    record TriageRequest(
            @NotBlank String subject,
            @NotBlank String body,
            String customer
    ) {

        TriageRequest {
            if (customer == null || customer.isBlank()) {
                customer = "individual";
            }
        }
    }

    record TriageResult(String queue, String category, String sentiment, String urgency) {}
}
