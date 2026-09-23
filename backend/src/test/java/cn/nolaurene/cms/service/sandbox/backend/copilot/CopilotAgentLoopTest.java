package cn.nolaurene.cms.service.sandbox.backend.copilot;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopilotAgentLoopTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void appendsToolResultsAndContinuesUntilAssistantHasNoToolCalls() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("lookup")
                .arguments("{\"query\":\"java\"}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from(List.of(request))))
                .thenReturn(response(AiMessage.from("final answer")));

        CopilotToolRegistry registry = new CopilotToolRegistry();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("query", Map.of("type", "string")));
        schema.put("required", List.of("query"));
        registry.register(new CopilotToolDefinition(
                "lookup", "Look up a value", schema,
                invocation -> CompletableFuture.completedFuture("result for "
                        + invocation.getArguments().get("query"))));

        List<CopilotAgentEvent> events = new ArrayList<>();
        CopilotLoopResult result = new CopilotAgentLoop(
                chatModel, registry, allowAll().setMaxTurns(4), "session-1", events::add)
                .run("find java");

        assertEquals("final answer", result.getFinalText());
        assertEquals(2, result.getTurns());
        assertFalse(result.isReachedLimit());
        assertTrue(events.stream().anyMatch(e -> e.getType() == CopilotAgentEventType.TOOL_EXECUTION_START));
        assertTrue(events.stream().anyMatch(e -> e.getType() == CopilotAgentEventType.TOOL_EXECUTION_COMPLETE
                && e.getData().get("success").equals(true)));
        assertEquals(CopilotAgentEventType.SESSION_IDLE,
                events.get(events.size() - 1).getType());
        verify(chatModel, times(2)).chat(any(ChatRequest.class));

        ArgumentCaptor<ChatRequest> requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, times(2)).chat(requests.capture());
        assertTrue(requests.getAllValues().get(1).messages().stream()
                .anyMatch(message -> message.type().name().equals("TOOL_EXECUTION_RESULT")));
    }

    @Test
    void unknownAndMalformedToolsBecomeModelVisibleErrors() {
        ToolExecutionRequest unknown = ToolExecutionRequest.builder()
                .id("unknown-call").name("missing").arguments("{}").build();
        ToolExecutionRequest malformed = ToolExecutionRequest.builder()
                .id("bad-call").name("known").arguments("[]").build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from(List.of(unknown, malformed))))
                .thenReturn(response(AiMessage.from("recovered")));

        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "known", "Known", Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture("ok")));

        CopilotLoopResult result = new CopilotAgentLoop(
                chatModel, registry, allowAll(), "session-2", event -> { })
                .run("continue");

        assertEquals("recovered", result.getFinalText());
        assertTrue(result.getMessages().stream()
                .filter(message -> message.type().name().equals("TOOL_EXECUTION_RESULT"))
                .count() == 2);
    }

    @Test
    void maxTurnsStopsAStuckModelWithExplicitLimit() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-loop").name("ping").arguments("{}").build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from(List.of(request))))
                .thenReturn(response(AiMessage.from(List.of(request))))
                .thenReturn(response(AiMessage.from(List.of(request))));

        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "ping", "Ping", Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture("pong")));
        List<CopilotAgentEvent> events = new ArrayList<>();

        CopilotLoopResult result = new CopilotAgentLoop(
                chatModel, registry, allowAll().setMaxTurns(2), "session-3", events::add)
                .run("keep going");

        assertTrue(result.isReachedLimit());
        assertTrue(events.stream().anyMatch(e -> e.getType() == CopilotAgentEventType.SESSION_LIMIT));
        assertEquals(2, result.getTurns());
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    @Test
    void successfulTerminalToolEndsWithoutAnotherModelCall() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("terminal-call").name("finish").arguments("{}").build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from(List.of(request))));

        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "finish", "Finish the run", Map.of("type", "object", "properties", Map.of()),
                invocation -> CompletableFuture.completedFuture(CopilotToolResult.success("done")),
                false, false, true, "never", Map.of()));

        CopilotLoopResult result = new CopilotAgentLoop(
                chatModel, registry, allowAll(), "terminal-session", event -> { })
                .run("finish now");

        assertEquals("done", result.getFinalText());
        assertEquals(1, result.getTurns());
        verify(chatModel, times(1)).chat(any(ChatRequest.class));
    }

    @Test
    void abortPairsOutstandingToolRequestsBeforeReturningTranscript() {
        ToolExecutionRequest first = ToolExecutionRequest.builder()
                .id("first").name("first_tool").arguments("{}").build();
        ToolExecutionRequest second = ToolExecutionRequest.builder()
                .id("second").name("second_tool").arguments("{}").build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from(List.of(first, second))));

        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition("first_tool", "first", Map.of(),
                invocation -> CompletableFuture.completedFuture("one")));
        registry.register(new CopilotToolDefinition("second_tool", "second", Map.of(),
                invocation -> CompletableFuture.completedFuture("two")));

        AtomicReference<CopilotAgentLoop> loopRef = new AtomicReference<>();
        CopilotAgentLoop loop = new CopilotAgentLoop(
                chatModel, registry, allowAll(), "abort-session", event -> {
                    if (event.getType() == CopilotAgentEventType.TOOL_EXECUTION_START) {
                        loopRef.get().abort();
                    }
                });
        loopRef.set(loop);

        CopilotLoopResult result = loop.run("abort");
        assertTrue(result.isAborted());
        long assistantCalls = result.getMessages().stream()
                .filter(message -> message.type().name().equals("AI")).count();
        long toolResults = result.getMessages().stream()
                .filter(message -> message.type().name().equals("TOOL_EXECUTION_RESULT")).count();
        assertEquals(1, assistantCalls);
        assertEquals(2, toolResults);
    }

    @Test
    void modelTimeoutRaisesARecoverableLoopError() {
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(1_000L);
            return response(AiMessage.from("too late"));
        });

        CopilotAgentLoop loop = new CopilotAgentLoop(
                chatModel, new CopilotToolRegistry(), allowAll()
                        .setModelTimeoutMillis(40L), "timeout-session", event -> { });

        long started = System.nanoTime();
        assertThrows(CopilotAgentLoop.CopilotAgentLoopException.class,
                () -> loop.run("timeout"));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(elapsedMillis < 800L, "model timeout should not block the caller");
    }

    @Test
    void abortInterruptsAnInFlightModelRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            entered.countDown();
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            return response(AiMessage.from("never"));
        });

        CopilotAgentLoop loop = new CopilotAgentLoop(
                chatModel, new CopilotToolRegistry(), allowAll()
                        .setModelTimeoutMillis(10_000L), "abort-model-session", event -> { });
        CompletableFuture<CopilotLoopResult> running = CompletableFuture.supplyAsync(() -> loop.run("abort"));
        assertTrue(entered.await(1, TimeUnit.SECONDS));
        loop.abort();

        CopilotLoopResult result = running.get(2, TimeUnit.SECONDS);
        assertTrue(result.isAborted());
    }

    @Test
    void abortFromTerminalToolEventDoesNotReportNormalCompletion() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("terminal-abort").name("finish").arguments("{}").build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from(List.of(request))));

        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "finish", "Finish", Map.of(),
                invocation -> CompletableFuture.completedFuture("done"),
                false, false, true, "never", Map.of()));
        AtomicReference<CopilotAgentLoop> loopRef = new AtomicReference<>();
        CopilotAgentLoop loop = new CopilotAgentLoop(
                chatModel, registry, allowAll(), "terminal-abort", event -> {
                    if (event.getType() == CopilotAgentEventType.TOOL_EXECUTION_COMPLETE) {
                        loopRef.get().abort();
                    }
                });
        loopRef.set(loop);

        CopilotLoopResult result = loop.run("finish");
        assertTrue(result.isAborted());
        assertEquals("", result.getFinalText());
    }

    @Test
    void forwardsOptionalStreamingDeltasWithoutDuplicatingTheFinalMessage() {
        class StreamingFake implements ChatModel, StreamingChatModel {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return response(AiMessage.from("unused"));
            }

            @Override
            public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onPartialResponse("hel");
                handler.onPartialResponse("lo");
                handler.onCompleteResponse(response(AiMessage.from("hello")));
            }

            @Override
            public java.util.Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
                return java.util.Set.of();
            }

            @Override
            public dev.langchain4j.model.ModelProvider provider() {
                return dev.langchain4j.model.ModelProvider.OTHER;
            }

            @Override
            public java.util.List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners() {
                return java.util.List.of();
            }

            @Override
            public dev.langchain4j.model.chat.request.ChatRequestParameters defaultRequestParameters() {
                return null;
            }
        }

        List<CopilotAgentEvent> events = new ArrayList<>();
        CopilotLoopResult result = new CopilotAgentLoop(
                new StreamingFake(), new CopilotToolRegistry(), allowAll(), "stream", events::add)
                .run("hello");

        assertEquals("hello", result.getFinalText());
        assertEquals(2, events.stream()
                .filter(event -> event.getType() == CopilotAgentEventType.ASSISTANT_MESSAGE_DELTA)
                .count());
    }

    @Test
    void abortDoesNotWaitForAHandlerThatIgnoresInterrupts() throws Exception {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("stubborn-call").name("stubborn").arguments("{}").build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from(List.of(request))));

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition("stubborn", "Stubborn", Map.of(), invocation -> {
            started.countDown();
            while (release.getCount() > 0) {
                try {
                    release.await(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // Deliberately ignore interruption to model a non-cooperative
                    // client; the outer execution future must still be cancellable.
                }
            }
            return CompletableFuture.completedFuture("released");
        }));
        AtomicReference<CopilotAgentLoop> loopRef = new AtomicReference<>();
        CopilotAgentLoop loop = new CopilotAgentLoop(
                chatModel, registry, allowAll(), "stubborn-session", event -> { });
        loopRef.set(loop);
        CompletableFuture<CopilotLoopResult> running = CompletableFuture.supplyAsync(() -> loop.run("abort"));
        assertTrue(started.await(1, TimeUnit.SECONDS));
        loopRef.get().abort();

        CopilotLoopResult result = running.get(1, TimeUnit.SECONDS);
        assertTrue(result.isAborted());
        release.countDown();
    }

    @Test
    void abortClosesAnUnresolvedPermissionRequestWithoutStartingTheTool() throws Exception {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("permission-call").name("protected").arguments("{}").build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(AiMessage.from(List.of(request))));

        AtomicReference<CopilotPermissionRequest> pending = new AtomicReference<>();
        AtomicReference<CopilotAgentLoop> loopRef = new AtomicReference<>();
        boolean[] invoked = {false};
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition("protected", "Protected", Map.of(), invocation -> {
            invoked[0] = true;
            return CompletableFuture.completedFuture("should not run");
        }));
        CopilotLoopConfig config = new CopilotLoopConfig()
                .setPermissionRequestHandler(permission -> {
                    pending.set(permission);
                    return permission.decision();
                });
        CopilotAgentLoop loop = new CopilotAgentLoop(
                chatModel, registry, config, "permission-abort", event -> { });
        loopRef.set(loop);
        CompletableFuture<CopilotLoopResult> running = CompletableFuture.supplyAsync(() -> loop.run("protected"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (pending.get() == null && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue(pending.get() != null);
        loopRef.get().abort();
        assertTrue(running.get(1, TimeUnit.SECONDS).isAborted());
        assertFalse(invoked[0]);
    }

    private static ChatResponse response(AiMessage message) {
        return ChatResponse.builder().aiMessage(message).build();
    }

    private static CopilotLoopConfig allowAll() {
        return new CopilotLoopConfig()
                .setPermissionHandler((invocation, definition) -> CopilotPermissionDecision.ALLOW);
    }
}
