# Spring AI Decision Models

Spring Boot 4.2.0 / Java 25 service for the customer support of an online musical instrument
store, on local Ollama models. Uses Spring AI 2.1.0's **decision model** API
(`DecisionModel`, model `d1-omni:600m`, set in `application.yml`) for classification and guardrails, and a chat model
(`phi:2.7b`, temperature 0) for the support chat. See `README.md` for endpoints and curls.

## Layout

Base package `com.ruhit.ai.spring`:

- `controller.TicketController`: `POST /tickets/...` classification endpoints. Single-text
  endpoints take a plain-text body (`@NotBlank`); `/triage` takes a JSON `TriageRequest`
  (`@Valid`). Bodies, not query params, so clients need no URL encoding and customer text stays
  out of access logs.
- `postman/Spring AI Decision Models.postman_collection.json`: Postman collection (v2.1 JSON,
  for Postman's Import) with every endpoint. Keep it in step with the controllers.
- `controller.SupportChatController`: `POST /support/chat` (plain-text body), a `ChatClient`
  with a fixed store-policy system prompt and the two guardrail advisors.
- `guardrails`: `DecisionInputGuardrailAdvisor`, `DecisionOutputGuardrailAdvisor`. The package is
  `@NullMarked` (JSpecify).

## Build & run

- Run: `./gradlew bootRun`. Port 8080.
- Test: `./gradlew test`. `TicketControllerTest` (`@WebMvcTest`) stubs `DecisionModel` with
  `CALLS_REAL_METHODS`, so the default `call(text, questions)` reaches the stubbed
  `call(DecisionPrompt)`; it runs without Ollama. `DecisionModelsApplicationTests.contextLoads`
  starts the full context, so it needs Ollama running.

## Ollama

- Ollama must be running natively at `http://localhost:11434` (the Spring AI default; no
  `base-url` is set). Nothing starts it for you: Arconia dev services were removed on purpose,
  so don't re-add a container-based setup unless asked.
- Missing models are pulled on startup (`pull-model-strategy: when_missing`): the decision and
  chat models are pulled, the embedding model is excluded. The first run can be slow.
- `OllamaDecisionModel` contacts Ollama while the bean is created, so an unreachable Ollama fails
  application startup (`Connection refused ... /api/tags`), not the first request.
- If that error shows `localhost/<non-loopback IP>`, check the hosts file for a `localhost`
  entry that is not `127.0.0.1`: Java honours it even though Windows DNS does not.

## Decision API notes

- `TicketController` asks on-topic (`YesNoQuestion`), category (`ChoiceQuestion`), sentiment
  and urgency (`ScoreQuestion`). `/triage` asks category, sentiment and urgency in one
  `DecisionPrompt` with the ticket fields (subject, body, customer).
- `ScoreAnswer` levels are **indices in declaration order** (sentiment: 0 angry … 3 happy).
  Act on `getNearestLevel()` (most probable level), not `getRoundedLevel()` (rounded mean), and
  map back to labels with `ScoreQuestion.getLevelLabels()`.
- `confidence` on answers is the provider's own estimate, not a probability of correctness;
  use `probability`/`probabilities` (or `isYes(threshold)`) for thresholds.
- Option and level descriptions are part of the model's prompt: rewording them changes
  classification results, so treat them as behaviour, not text.
- Routing is deterministic Java on top of the model's answers: `/triage` sends `angry` tickets
  to the `escalated` queue, otherwise to the category's queue.
- The on-topic question is defined three times with identical text (`/on-topic`,
  `/on-topic/threshold`, the input guardrail), and category/sentiment twice. Change all copies
  together, or extract them.

## Guardrails

- Advisor order: input (`getOrder() = 0`) runs before output (`1`).
- Input guardrail asks `sensitiveData` and `onTopic` in one call. When it blocks, it returns a
  canned `ChatClientResponse` **without calling the chat model**; a blank message is blocked as
  off-topic without calling the decision model.
- Output guardrail sends the conversation **minus system messages** plus the chat reply, asks
  `answered`, and replaces the reply with a hand-off message below `ANSWERED_THRESHOLD` (0.35).
- Both treat `decision.isRefused(...)` as a failed check (block / hand off), so check refusal
  before reading the answer.

## Conventions

- Match existing formatting: 4-space indentation in `src/main/java`, tabs in generated files
  (`DecisionModelsApplication`, `build.gradle`).
- New classes carry the existing Javadoc header (`@author Ruhit`, `@since <date time>`).
