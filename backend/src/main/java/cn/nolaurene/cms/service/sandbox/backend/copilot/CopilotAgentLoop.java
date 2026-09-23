package cn.nolaurene.cms.service.sandbox.backend.copilot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * In-process implementation of Copilot's core agent loop.
 *
 * <p>The model is the authority for continuation: every assistant response is
 * appended to the transcript; all requested tools are executed; their results
 * are appended with the original call IDs; and only a response without tool
 * requests ends the run.  No planner, XML parser, or synthetic skill tool is
 * involved.</p>
 */
public final class CopilotAgentLoop {

    private final ChatModel chatModel;
    private final CopilotToolRegistry toolRegistry;
    private final CopilotLoopConfig config;
    private final Consumer<CopilotAgentEvent> eventSink;
    private final String sessionId;
    private final AtomicBoolean aborted = new AtomicBoolean(false);
    private final AtomicReference<Future<ChatResponse>> activeModelCall = new AtomicReference<>();
    private final AtomicReference<AtomicBoolean> activeModelCancellation = new AtomicReference<>();
    private volatile boolean streamingResponse;
    private volatile boolean streamedReasoning;
    private static final ExecutorService MODEL_EXECUTOR = new ThreadPoolExecutor(
            2,
            8,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),
            runnable -> {
                Thread thread = new Thread(runnable, "copilot-model-calls");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    public CopilotAgentLoop(ChatModel chatModel,
                            CopilotToolRegistry toolRegistry,
                            CopilotLoopConfig config,
                            String sessionId,
                            Consumer<CopilotAgentEvent> eventSink) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.config = config == null ? new CopilotLoopConfig() : config;
        this.sessionId = sessionId;
        this.eventSink = eventSink == null ? ignored -> { } : eventSink;
        Consumer<CopilotPermissionRequest> hostPermissionSink = this.config.getPermissionRequestSink();
        this.config.setPermissionRequestSink(request -> {
            if (hostPermissionSink != null) {
                hostPermissionSink.accept(request);
            }
            Map<String, Object> permissionData = new LinkedHashMap<>();
            permissionData.put("requestId", request.getRequestId());
            permissionData.put("permissionRequestId", request.getRequestId());
            permissionData.put("permissionRequest", request.toMap());
            emit(CopilotAgentEvent.builder(CopilotAgentEventType.TOOL_PERMISSION_REQUEST)
                    .toolCallId(request.getInvocation().getToolCallId())
                    .toolName(request.getDefinition().name())
                    .arguments(request.getInvocation().getArguments())
                    .data(permissionData)
                    .build());
        });
    }

    public CopilotLoopResult run(String prompt) {
        return run(prompt, Collections.emptyList());
    }

    public CopilotLoopResult run(String prompt, List<ChatMessage> previousMessages) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        List<ChatMessage> messages = new ArrayList<>();
        if (previousMessages != null) {
            messages.addAll(previousMessages);
        }
        messages.add(UserMessage.from(prompt));
        // Keep an un-compacted canonical transcript for persistence and resume.  The
        // request-specific `messages` list may be shortened before a model
        // call, but that must never erase the session's canonical history.
        List<ChatMessage> transcript = new ArrayList<>(messages);
        emit(CopilotAgentEvent.builder(CopilotAgentEventType.USER_MESSAGE)
                .content(prompt)
                .data(Map.of("sessionId", sessionId == null ? "" : sessionId))
                .build());

        String finalText = "";
        int completedTurns = 0;
        List<ToolSpecification> specifications = buildToolSpecifications();

        for (int turn = 1; turn <= config.getMaxTurns(); turn++) {
            if (aborted.get()) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn - 1).data(Map.of("aborted", true)).build());
                return finishResult(finalText, transcript, completedTurns, false, true);
            }
            completedTurns = turn;
            messages = compact(messages);
            emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_TURN_START)
                    .turn(turn).build());

            ChatResponse response;
            try {
                response = callModel(messages, specifications, turn);
            } catch (CancellationException error) {
                if (aborted.get()) {
                    emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                            .turn(turn - 1).data(Map.of("aborted", true)).build());
                    return finishResult(finalText, transcript, completedTurns, false, true);
                }
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_ERROR)
                        .turn(turn).error(rootMessage(error)).build());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", false, "error", true)).build());
                throw new CopilotAgentLoopException("Model call interrupted", error, transcript, turn);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                if (aborted.get()) {
                    emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                            .turn(turn - 1).data(Map.of("aborted", true)).build());
                    return finishResult(finalText, transcript, completedTurns, false, true);
                }
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_ERROR)
                        .turn(turn).error(rootMessage(error)).build());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", false, "error", true)).build());
                throw new CopilotAgentLoopException("Model call interrupted", error, transcript, turn);
            } catch (TimeoutException error) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_ERROR)
                        .turn(turn).error(rootMessage(error)).data(Map.of("timeout", true)).build());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", false, "error", true, "timeout", true)).build());
                throw new CopilotAgentLoopException("Model call timed out after "
                        + config.getModelTimeoutMillis() + " ms", error, transcript, turn);
            } catch (Throwable error) {
                if (aborted.get()) {
                    emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                            .turn(turn - 1).data(Map.of("aborted", true)).build());
                    return finishResult(finalText, transcript, completedTurns, false, true);
                }
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_ERROR)
                        .turn(turn).error(rootMessage(error)).build());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", false, "error", true)).build());
                throw new CopilotAgentLoopException("Model call failed", error, transcript, turn);
            }

            if (aborted.get()) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn - 1).data(Map.of("aborted", true)).build());
                return finishResult(finalText, transcript, completedTurns, false, true);
            }

            if (response == null || response.aiMessage() == null) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_ERROR)
                        .turn(turn).error("Model returned an empty response").build());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", false, "error", true)).build());
                throw new CopilotAgentLoopException("Model returned an empty response", transcript, turn);
            }

            AiMessage assistant = response.aiMessage();
            boolean hasToolRequests = assistant.hasToolExecutionRequests();
            List<ToolExecutionRequest> normalizedRequests = new ArrayList<>();
            if (hasToolRequests && assistant.toolExecutionRequests() != null) {
                for (int index = 0; index < assistant.toolExecutionRequests().size(); index++) {
                    normalizedRequests.add(normalizeRequest(
                            assistant.toolExecutionRequests().get(index), turn, index));
                }
                AiMessage.Builder normalizedAssistant = AiMessage.builder()
                        .text(assistant.text() == null ? "" : assistant.text())
                        .toolExecutionRequests(normalizedRequests);
                if (assistant.thinking() != null) {
                    normalizedAssistant.thinking(assistant.thinking());
                }
                if (assistant.attributes() != null && !assistant.attributes().isEmpty()) {
                    normalizedAssistant.attributes(assistant.attributes());
                }
                assistant = normalizedAssistant.build();
            }
            messages.add(assistant);
            transcript.add(assistant);
            if (assistant.thinking() != null && !assistant.thinking().isBlank() && !streamedReasoning) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_REASONING)
                        .turn(turn).content(assistant.thinking()).build());
            }
            if (assistant.text() != null && !assistant.text().isBlank()) {
                finalText = assistant.text();
                if (!streamingResponse) {
                    emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_MESSAGE_DELTA)
                            .turn(turn).content(assistant.text()).build());
                }
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_MESSAGE)
                        .turn(turn)
                        .content(assistant.text())
                        .data(Map.of("toolRequests", describeToolRequests(assistant.toolExecutionRequests())))
                        .build());
            } else {
                if (!hasToolRequests) {
                    finalText = "";
                }
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_MESSAGE)
                        .turn(turn)
                        .data(Map.of("toolOnly", true,
                                "toolRequests", describeToolRequests(assistant.toolExecutionRequests())))
                        .build());
            }

            // Event consumers may request abort from any assistant callback.
            // Do not start tools or report a normal completion after that
            // callback has marked the session cancelled.
            if (aborted.get()) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", true)).build());
                return finishResult(finalText, transcript, completedTurns, false, true);
            }

            if (!hasToolRequests) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_TURN_END)
                        .turn(turn).build());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", false)).build());
                return finishResult(finalText, transcript, completedTurns, false, false);
            }

            List<ToolExecutionRequest> requests = normalizedRequests;
            if (requests == null || requests.isEmpty()) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_TURN_END)
                        .turn(turn).build());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", false)).build());
                return finishResult(finalText, transcript, completedTurns, false, false);
            }
            List<CompletableFuture<CopilotToolRegistry.CopilotToolExecution>> futures = new ArrayList<>();
            List<String> requestIds = new ArrayList<>(requests.size());
            for (int index = 0; index < requests.size(); index++) {
                if (aborted.get()) {
                    break;
                }
                ToolExecutionRequest request = requests.get(index);
                String toolCallId = request.id();
                requestIds.add(toolCallId);
                Map<String, Object> args = parseArgumentsForEvent(request.arguments());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.TOOL_EXECUTION_START)
                        .turn(turn)
                        .toolCallId(toolCallId)
                        .toolName(request.name())
                        .arguments(args)
                        .build());
                futures.add(toolRegistry.execute(sessionId, request, config));
            }

            if (aborted.get()) {
                appendAbortedToolResults(requests, futures, 0, messages, transcript, turn);
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", true)).build());
                return finishResult(finalText, transcript, completedTurns, false, true);
            }

            // Preserve model request order in the transcript even if handlers
            // complete concurrently in the future.
            boolean terminalSuccess = false;
            String terminalText = null;
            for (int index = 0; index < requests.size(); index++) {
                if (aborted.get()) {
                    appendAbortedToolResults(requests, futures, index, messages, transcript, turn);
                    emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                            .turn(turn).data(Map.of("aborted", true)).build());
                    return finishResult(finalText, transcript, completedTurns, false, true);
                }
                ToolExecutionRequest request = normalizedRequests.get(index);
                String toolCallId = requestIds.get(index);
                CopilotToolRegistry.CopilotToolExecution execution;
                try {
                    execution = futures.get(index).join();
                } catch (Throwable error) {
                    execution = CopilotToolRegistry.CopilotToolExecution.error(
                            toolCallId, rootMessage(error));
                }
                String modelText = execution.modelText();
                messages.add(ToolExecutionResultMessage.builder()
                        .id(toolCallId)
                        .toolName(request.name())
                        .text(modelText)
                        .isError(!execution.isSuccess())
                        .build());
                transcript.add(messages.get(messages.size() - 1));
                Map<String, Object> completionData = new LinkedHashMap<>();
                completionData.put("success", execution.isSuccess());
                completionData.put("resultType", execution.resultType());
                completionData.put("modelText", modelText);
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.TOOL_EXECUTION_COMPLETE)
                        .turn(turn)
                        .toolCallId(toolCallId)
                        .toolName(request.name())
                        .result(execution.getResult())
                        .error(execution.getError())
                        .data(completionData)
                        .build());
                if (aborted.get()) {
                    appendAbortedToolResults(requests, futures, index + 1,
                            messages, transcript, turn);
                    emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                            .turn(turn).data(Map.of("aborted", true)).build());
                    return finishResult(finalText, transcript, completedTurns, false, true);
                }
                if (execution.isSuccess()
                        && toolRegistry.find(request.name()).map(CopilotToolDefinition::isTerminal).orElse(false)) {
                    terminalSuccess = true;
                    terminalText = modelText;
                }
            }
            if (aborted.get()) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", true)).build());
                return finishResult(finalText, transcript, completedTurns, false, true);
            }
            if (terminalSuccess) {
                if (terminalText != null && !terminalText.isBlank()) {
                    finalText = terminalText;
                }
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_TURN_END)
                        .turn(turn).data(Map.of("terminal", true)).build());
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", false, "terminal", true)).build());
                return finishResult(finalText, transcript, completedTurns, false, false);
            }
            emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_TURN_END)
                    .turn(turn).data(Map.of("continued", true)).build());
            if (aborted.get()) {
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                        .turn(turn).data(Map.of("aborted", true)).build());
                return finishResult(finalText, transcript, completedTurns, false, true);
            }
        }

        if (aborted.get()) {
            emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                    .turn(completedTurns).data(Map.of("aborted", true)).build());
            return finishResult(finalText, transcript, completedTurns, false, true);
        }

        String limitMessage = finalText == null || finalText.isBlank()
                ? "The agent reached its maximum number of turns before completing the task."
                : finalText;
        emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_LIMIT)
                .turn(completedTurns)
                .error("max_turns_reached")
                .data(Map.of("maxTurns", config.getMaxTurns()))
                .build());
        emit(CopilotAgentEvent.builder(CopilotAgentEventType.SESSION_IDLE)
                .turn(completedTurns).data(Map.of("aborted", false, "limit", true)).build());
        return finishResult(limitMessage, transcript, completedTurns, true, false);
    }

    private CopilotLoopResult finishResult(String finalText,
                                           List<ChatMessage> transcript,
                                           int turns,
                                           boolean reachedLimit,
                                           boolean aborted) {
        return new CopilotLoopResult(finalText, compactTranscript(transcript), turns,
                reachedLimit, aborted);
    }

    /**
     * Keep the persisted transcript structurally valid when cancellation lands
     * between a model tool request and its result.  Every assistant request is
     * paired with an explicit error result, and outstanding handlers are
     * cancelled on a best-effort basis.
     */
    private void appendAbortedToolResults(List<ToolExecutionRequest> requests,
                                          List<CompletableFuture<CopilotToolRegistry.CopilotToolExecution>> futures,
                                          int start,
                                          List<ChatMessage> messages,
                                          List<ChatMessage> transcript,
                                          int turn) {
        for (int index = start; index < requests.size(); index++) {
            if (index < futures.size()) {
                futures.get(index).cancel(true);
            }
            ToolExecutionRequest request = requests.get(index);
            String id = request == null ? "java-aborted-call-" + turn + "-" + index : request.id();
            String name = request == null ? "invalid_tool_call" : request.name();
            String text = "Tool execution aborted before completion.";
            ToolExecutionResultMessage result = ToolExecutionResultMessage.builder()
                    .id(id)
                    .toolName(name)
                    .text(text)
                    .isError(true)
                    .build();
            messages.add(result);
            transcript.add(result);
            emit(CopilotAgentEvent.builder(CopilotAgentEventType.TOOL_EXECUTION_COMPLETE)
                    .turn(turn)
                    .toolCallId(id)
                    .toolName(name)
                    .error(text)
                    .data(Map.of("success", false, "modelText", text, "aborted", true))
                    .build());
        }
    }

    /** Request cooperative cancellation at the next safe loop boundary. */
    public void abort() {
        aborted.set(true);
        Future<ChatResponse> modelCall = activeModelCall.getAndSet(null);
        AtomicBoolean cancellation = activeModelCancellation.getAndSet(null);
        if (cancellation != null) {
            cancellation.set(true);
        }
        if (modelCall != null) {
            modelCall.cancel(true);
        }
        // Tool futures have their own cancellation bridge; cancelling them
        // here makes abort effective even while the loop is joining results.
        toolRegistry.cancelOutstanding(0L);
    }

    public boolean isAborted() {
        return aborted.get();
    }

    /**
     * Execute one blocking LangChain4j call behind a cancellable Java future.
     * The official SDK exposes abort/timeout at the session boundary; this
     * wrapper provides the same boundary without introducing another runtime.
     */
    private ChatResponse callModel(List<ChatMessage> messages,
                                   List<ToolSpecification> specifications,
                                   int turn)
            throws InterruptedException, ExecutionException, TimeoutException {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(specifications)
                .build();
        streamingResponse = false;
        streamedReasoning = false;
        AtomicBoolean cancellation = new AtomicBoolean(false);
        Future<ChatResponse> future = MODEL_EXECUTOR.submit(() -> {
            if (chatModel instanceof StreamingChatModel) {
                return streamModel((StreamingChatModel) chatModel, request, turn, cancellation);
            }
            return chatModel.chat(request);
        });
        activeModelCall.set(future);
        activeModelCancellation.set(cancellation);
        if (aborted.get()) {
            cancellation.set(true);
            future.cancel(true);
        }
        try {
            return future.get(config.getModelTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            cancellation.set(true);
            future.cancel(true);
            throw error;
        } finally {
            if (future.isCancelled()) {
                cancellation.set(true);
            }
            activeModelCall.compareAndSet(future, null);
            activeModelCancellation.compareAndSet(cancellation, null);
        }
    }

    private ChatResponse streamModel(StreamingChatModel streamingModel,
                                     ChatRequest request,
                                     int turn,
                                     AtomicBoolean cancellation)
            throws InterruptedException, ExecutionException {
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        AtomicBoolean streamFinished = new AtomicBoolean(false);
        StreamingChatResponseHandler handler = new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String text) {
                if (aborted.get() || cancellation.get() || streamFinished.get()
                        || text == null || text.isBlank()) {
                    return;
                }
                streamingResponse = true;
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_MESSAGE_DELTA)
                        .turn(turn).content(text).build());
            }

            @Override
            public void onPartialThinking(PartialThinking thinking) {
                if (aborted.get() || cancellation.get() || streamFinished.get()
                        || thinking == null || thinking.text() == null
                        || thinking.text().isBlank()) {
                    return;
                }
                streamingResponse = true;
                streamedReasoning = true;
                emit(CopilotAgentEvent.builder(CopilotAgentEventType.ASSISTANT_REASONING)
                        .turn(turn).content(thinking.text()).build());
            }

            @Override
            public void onCompleteResponse(ChatResponse complete) {
                streamFinished.set(true);
                if (aborted.get() || cancellation.get()) {
                    response.completeExceptionally(new CancellationException("model stream aborted"));
                } else {
                    response.complete(complete);
                }
            }

            @Override
            public void onError(Throwable error) {
                streamFinished.set(true);
                response.completeExceptionally(aborted.get() || cancellation.get()
                        ? new CancellationException("model stream aborted") : error);
            }
        };
        try {
            streamingModel.chat(request, handler);
        } catch (Throwable error) {
            response.completeExceptionally(error);
        }
        return response.get();
    }

    private List<ToolSpecification> buildToolSpecifications() {
        List<ToolSpecification> result = new ArrayList<>();
        for (CopilotToolDefinition definition : toolRegistry.all()) {
            result.add(ToolSpecification.builder()
                    .name(definition.name())
                    .description(definition.description())
                    .parameters(CopilotToolAdapter.toLangChainObjectSchema(definition.parameters()))
                    .build());
        }
        return result;
    }

    private List<ChatMessage> compact(List<ChatMessage> messages) {
        int max = config.getMaxContextMessages();
        if (messages.size() <= max) {
            return messages;
        }
        int prefixCount = 0;
        while (prefixCount < messages.size() && messages.get(prefixCount) instanceof SystemMessage) {
            prefixCount++;
        }
        int remaining = Math.max(1, max - prefixCount);
        int start = Math.max(prefixCount, messages.size() - remaining);

        // Never send a tool result without the assistant tool request that
        // produced it.  Providers reject such transcripts, and it also loses
        // the call/result correlation the Copilot protocol relies on.
        if (start < messages.size() && messages.get(start) instanceof ToolExecutionResultMessage) {
            int pairedStart = findToolCallStart(messages, start, prefixCount);
            start = pairedStart >= prefixCount ? pairedStart : skipToolResults(messages, start);
        }

        List<ChatMessage> result = new ArrayList<>();
        result.addAll(messages.subList(0, prefixCount));
        result.addAll(messages.subList(Math.min(start, messages.size()), messages.size()));
        return result;
    }

    /**
     * Bound the canonical transcript used for restart snapshots.  The request
     * context and persistence bound are intentionally separate: a host may
     * send a small context window while retaining a larger resumable history.
     */
    private List<ChatMessage> compactTranscript(List<ChatMessage> messages) {
        int max = config.getMaxTranscriptMessages();
        if (messages == null || messages.size() <= max) {
            return messages == null ? List.of() : new ArrayList<>(messages);
        }
        int prefixCount = 0;
        while (prefixCount < messages.size() && messages.get(prefixCount) instanceof SystemMessage) {
            prefixCount++;
        }
        int remaining = Math.max(1, max - prefixCount);
        int start = Math.max(prefixCount, messages.size() - remaining);
        if (start < messages.size() && messages.get(start) instanceof ToolExecutionResultMessage) {
            int pairedStart = findToolCallStart(messages, start, prefixCount);
            start = pairedStart >= prefixCount ? pairedStart : skipToolResults(messages, start);
        }
        List<ChatMessage> result = new ArrayList<>();
        result.addAll(messages.subList(0, prefixCount));
        result.addAll(messages.subList(Math.min(start, messages.size()), messages.size()));
        return result;
    }

    private int findToolCallStart(List<ChatMessage> messages, int resultIndex, int lowerBound) {
        if (!(messages.get(resultIndex) instanceof ToolExecutionResultMessage)) {
            return -1;
        }
        String resultId = ((ToolExecutionResultMessage) messages.get(resultIndex)).id();
        for (int index = resultIndex - 1; index >= lowerBound; index--) {
            ChatMessage candidate = messages.get(index);
            if (!(candidate instanceof AiMessage)) {
                continue;
            }
            AiMessage assistant = (AiMessage) candidate;
            if (!assistant.hasToolExecutionRequests()) {
                continue;
            }
            if (resultId == null || assistant.toolExecutionRequests().stream()
                    .anyMatch(request -> Objects.equals(resultId, request.id()))) {
                return index;
            }
        }
        return -1;
    }

    private int skipToolResults(List<ChatMessage> messages, int start) {
        int index = start;
        while (index < messages.size() && messages.get(index) instanceof ToolExecutionResultMessage) {
            index++;
        }
        return index;
    }

    private List<Map<String, Object>> describeToolRequests(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(requests.size());
        for (ToolExecutionRequest request : requests) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", request == null ? null : request.id());
            item.put("name", request == null ? null : request.name());
            item.put("arguments", request == null ? null : request.arguments());
            result.add(item);
        }
        return result;
    }

    private String normalizedToolCallId(ToolExecutionRequest request, int turn, int index) {
        if (request != null && request.id() != null && !request.id().isBlank()) {
            return request.id();
        }
        return "java-call-" + turn + "-" + index;
    }

    private ToolExecutionRequest normalizeRequest(ToolExecutionRequest request, int turn, int index) {
        String id = normalizedToolCallId(request, turn, index);
        String name = request == null || request.name() == null || request.name().isBlank()
                ? "invalid_tool_call" : request.name();
        if (request != null && Objects.equals(request.id(), id) && Objects.equals(request.name(), name)
                && request.arguments() != null) {
            return request;
        }
        return ToolExecutionRequest.builder()
                .id(id)
                .name(name)
                .arguments(request == null || request.arguments() == null ? "{}" : request.arguments())
                .build();
    }

    private Map<String, Object> parseArgumentsForEvent(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            JSONObject object = JSON.parseObject(raw);
            return object == null ? Map.of() : new LinkedHashMap<>(object);
        } catch (Exception error) {
            return Map.of("_raw", raw, "_parseError", rootMessage(error));
        }
    }

    private void emit(CopilotAgentEvent event) {
        try {
            eventSink.accept(event);
        } catch (Throwable ignored) {
            // A disconnected UI must not break the model/tool transcript.
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public static final class CopilotAgentLoopException extends RuntimeException {
        private final List<ChatMessage> messages;
        private final int turns;

        public CopilotAgentLoopException(String message, List<ChatMessage> messages, int turns) {
            super(message);
            this.messages = messages == null ? List.of() : List.copyOf(messages);
            this.turns = turns;
        }

        public CopilotAgentLoopException(String message, Throwable cause,
                                         List<ChatMessage> messages, int turns) {
            super(message, cause);
            this.messages = messages == null ? List.of() : List.copyOf(messages);
            this.turns = turns;
        }

        public List<ChatMessage> getMessages() { return messages; }
        public int getTurns() { return turns; }
    }
}
