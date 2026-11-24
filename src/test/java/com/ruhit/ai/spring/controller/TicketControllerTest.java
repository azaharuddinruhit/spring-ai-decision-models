package com.ruhit.ai.spring.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.FieldsInput;
import org.springframework.ai.decision.input.TextInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author Ruhit
 * @since 24-Nov-26 2:08 PM
 */
@WebMvcTest(TicketController.class)
class TicketControllerTest {

    private static final String MESSAGE = "My new guitar strings keep breaking. Can you send replacements?";

    // Real default methods, so call(text, questions) reaches the stubbed call(DecisionPrompt)
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private DecisionModel decisionModel;

    private final MockMvcTester mvc;

    TicketControllerTest(@Autowired MockMvcTester mvc) {
        this.mvc = mvc;
    }

    @Test
    void onTopicReadsTheMessageFromThePlainTextBody() {
        respondWith(Map.of("onTopic", YesNoAnswer.builder().probability(0.99).build()));

        assertThat(postText("/tickets/on-topic", MESSAGE))
                .hasStatusOk()
                .bodyJson().extractingPath("$.probability").isEqualTo(0.99);
        assertThat(sentPrompt().getInstructions())
                .isInstanceOfSatisfying(TextInput.class, input -> assertThat(input.getText()).isEqualTo(MESSAGE));
    }

    @Test
    void onTopicThresholdComparesTheProbabilityWithTheThreshold() {
        respondWith(Map.of("onTopic", YesNoAnswer.builder().probability(0.4).build()));

        assertThat(postText("/tickets/on-topic/threshold?threshold=0.3", MESSAGE))
                .hasStatusOk()
                .bodyText().isEqualTo("true");
        assertThat(postText("/tickets/on-topic/threshold", MESSAGE))
                .hasStatusOk()
                .bodyText().isEqualTo("false");
    }

    @Test
    void blankTextIsRejectedWithoutCallingTheModel() {
        assertThat(postText("/tickets/category", "   ")).hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(decisionModel);
    }

    @Test
    void triageSendsAngryCustomersToTheEscalatedQueue() {
        respondWith(Map.of(
                "category", ChoiceAnswer.builder().value("billing").probabilities(Map.of("billing", 0.9, "other", 0.1)).build(),
                "sentiment", ScoreAnswer.builder().value(0.45).probabilities(List.of(0.7, 0.2, 0.05, 0.05)).build(),
                "urgency", ScoreAnswer.builder().value(1.6).probabilities(List.of(0.1, 0.2, 0.7)).build()));

        assertThat(postJson("/tickets/triage", """
                {"subject": "Duplicate charge, cancelling", "body": "You charged our music school twice.", "customer": "music-school"}
                """))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"queue": "escalated", "category": "billing", "sentiment": "angry", "urgency": "high"}
                        """);
    }

    @Test
    void triageSendsOtherCustomersToTheirCategoryQueueAndDefaultsTheCustomerType() {
        respondWith(Map.of(
                "category", ChoiceAnswer.builder().value("shipping").probabilities(Map.of("shipping", 0.95, "other", 0.05)).build(),
                "sentiment", ScoreAnswer.builder().value(1.85).probabilities(List.of(0.05, 0.1, 0.8, 0.05)).build(),
                "urgency", ScoreAnswer.builder().value(0.9).probabilities(List.of(0.2, 0.7, 0.1)).build()));

        assertThat(postJson("/tickets/triage", """
                {"subject": "New address", "body": "Could you change the delivery address for order 50117?"}
                """))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"queue": "shipping", "category": "shipping", "sentiment": "neutral", "urgency": "normal"}
                        """);
        assertThat(sentPrompt().getInstructions())
                .isInstanceOfSatisfying(FieldsInput.class,
                        input -> assertThat(input.getFields()).containsEntry("customer", "individual"));
    }

    @Test
    void triageRejectsATicketWithoutASubject() {
        assertThat(postJson("/tickets/triage", """
                {"body": "You charged our music school twice."}
                """))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(decisionModel);
    }

    private void respondWith(Map<String, DecisionAnswer> answers) {
        willAnswer(invocation -> DecisionResponse.builder(invocation.getArgument(0))
                .answers(answers)
                .build())
                .given(decisionModel).call(any(DecisionPrompt.class));
    }

    private DecisionPrompt sentPrompt() {
        var prompt = ArgumentCaptor.forClass(DecisionPrompt.class);
        then(decisionModel).should().call(prompt.capture());
        return prompt.getValue();
    }

    private MockMvcTester.MockMvcRequestBuilder postText(String uri,
                                                         String text) {
        return mvc.post().uri(uri).contentType(MediaType.TEXT_PLAIN).content(text);
    }

    private MockMvcTester.MockMvcRequestBuilder postJson(String uri,
                                                         String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
