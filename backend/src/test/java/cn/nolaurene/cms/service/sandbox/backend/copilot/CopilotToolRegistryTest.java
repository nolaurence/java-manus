package cn.nolaurene.cms.service.sandbox.backend.copilot;

import cn.nolaurene.cms.service.sandbox.backend.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotToolRegistryTest {

    @Test
    void exposesSortedDefinitionsAndPreservesStructuredResults() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(definition("zeta", Map.of(), args -> Map.of("ok", true)));
        registry.register(definition("alpha", Map.of(), args -> List.of("a", "b")));

        assertEquals(List.of("alpha", "zeta"), registry.names());
        CopilotToolRegistry.CopilotToolExecution result = registry.execute(
                "s", request("alpha", "{}"), allowAll()).join();
        assertTrue(result.isSuccess());
        assertEquals(List.of("a", "b"), result.getResult());
        assertEquals("[\"a\",\"b\"]", result.modelText());
    }

    @Test
    void validatesObjectArgumentsAndPermissionBeforeCallingHandler() {
        boolean[] called = {false};
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "required", "Requires query",
                Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")),
                invocation -> {
                    called[0] = true;
                    return CompletableFuture.completedFuture("done");
                }));

        CopilotToolRegistry.CopilotToolExecution malformed = registry.execute(
                "s", request("required", "[]"), allowAll()).join();
        assertFalse(malformed.isSuccess());
        assertFalse(called[0]);

        CopilotLoopConfig denied = new CopilotLoopConfig()
                .setPermissionHandler((invocation, definition) -> CopilotPermissionDecision.DENY);
        CopilotToolRegistry.CopilotToolExecution deniedResult = registry.execute(
                "s", request("required", "{\"query\":\"x\"}"), denied).join();
        assertFalse(deniedResult.isSuccess());
        assertFalse(called[0]);
    }

    @Test
    void exposesStructuredFailureToTheModelWithoutThrowing() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "structured", "Structured result", Map.of("type", "object"),
                invocation -> CompletableFuture.completedFuture(
                        CopilotToolResult.failure("Please retry with a valid id", "invalid id"))));

        CopilotToolRegistry.CopilotToolExecution result = registry.execute(
                "s", request("structured", "{}"), allowAll()).join();

        assertFalse(result.isSuccess());
        assertEquals("failure", result.getResultType());
        assertEquals("Please retry with a valid id", result.modelText());
        assertEquals("invalid id", result.getError());
    }

    @Test
    void keepsErrorAndFailureResultTypesDistinct() {
        assertEquals("error", CopilotToolResult.error("visible", "internal").resultType());
        assertEquals("failure", CopilotToolResult.failure("visible", "internal").resultType());
    }

    @Test
    void invocationSupportsTypedArgumentBinding() {
        record Args(String query) { }
        CopilotToolInvocation invocation = new CopilotToolInvocation(
                "session", "call", "lookup", Map.of("query", "java"), Map.of());
        assertEquals("java", invocation.getArgumentsAs(Args.class).query());
    }

    @Test
    void preservesBinaryStructuredResultAsWireTextForTextOnlyChatMessages() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        CopilotToolResult value = new CopilotToolResult(
                "success", "",
                List.of(new CopilotToolResult.BinaryResult("aGVsbG8=", "image/png", "image", "preview")),
                null, null, Map.of(), List.of());
        registry.register(new CopilotToolDefinition("image", "Image", Map.of(),
                invocation -> CompletableFuture.completedFuture(value)));

        CopilotToolRegistry.CopilotToolExecution result = registry.execute(
                "s", request("image", "{}"), allowAll()).join();
        assertTrue(result.isSuccess());
        assertTrue(result.modelText().contains("binaryResultsForLlm"));
    }

    @Test
    void validatesNestedJsonSchemaTypesBeforeInvokingHandler() {
        boolean[] called = {false};
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "typed", "Typed", Map.of(
                        "type", "object",
                        "properties", Map.of("count", Map.of("type", "integer"))),
                invocation -> {
                    called[0] = true;
                    return CompletableFuture.completedFuture("ok");
                }));

        CopilotToolRegistry.CopilotToolExecution result = registry.execute(
                "s", request("typed", "{\"count\":\"one\"}"), allowAll()).join();

        assertFalse(result.isSuccess());
        assertFalse(called[0]);
        assertTrue(result.modelText().contains("must be an integer"));
    }

    @Test
    void declarationOnlyToolProducesARecoverableModelError() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "external", "Handled by an external host", Map.of("type", "object"), null));

        CopilotToolRegistry.CopilotToolExecution result = registry.execute(
                "s", request("external", "{}"), allowAll()).join();

        assertFalse(result.isSuccess());
        assertTrue(result.modelText().contains("no local handler"));
    }

    @Test
    void requiredAllowsExplicitNullWhenThePropertySchemaAllowsNull() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "nullable", "Nullable", Map.of(
                        "type", "object",
                        "properties", Map.of("value", Map.of("type", "null")),
                        "required", List.of("value")),
                invocation -> CompletableFuture.completedFuture("ok")));

        CopilotToolRegistry.CopilotToolExecution result = registry.execute(
                "s", request("nullable", "{\"value\":null}"), allowAll()).join();
        assertTrue(result.isSuccess());
    }

    @Test
    void appliesConstAndEnumConstraintsToCompositeValues() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "composite", "Composite", Map.of(
                        "type", "object",
                        "properties", Map.of("kind", Map.of("type", "string")),
                        "required", List.of("kind"),
                        "const", Map.of("kind", "fixed")),
                invocation -> CompletableFuture.completedFuture("ok")));

        CopilotToolRegistry.CopilotToolExecution result = registry.execute(
                "s", request("composite", "{\"kind\":\"other\"}"), allowAll()).join();
        assertFalse(result.isSuccess());
        assertTrue(result.modelText().contains("const"));
    }

    @Test
    void propagatesInvocationContextAndKeepsUnconfiguredPermissionsPending() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "contextual", "Contextual", Map.of(),
                invocation -> CompletableFuture.completedFuture(invocation.getContext().get("tenant"))));

        CompletableFuture<CopilotToolRegistry.CopilotToolExecution> pendingExecution = registry.execute(
                "s", request("contextual", "{}"), new CopilotLoopConfig());
        assertFalse(pendingExecution.isDone());
        CopilotPermissionRequest pending = registry.pendingPermissionRequests().get(0);
        assertTrue(pending.resolve(CopilotPermissionDecision.DENY));
        CopilotToolRegistry.CopilotToolExecution denied = pendingExecution.join();
        assertFalse(denied.isSuccess());
        assertEquals("denied", denied.getResultType());
        assertTrue(denied.modelText().contains("Permission denied"));

        CopilotLoopConfig configured = allowAll()
                .setInvocationContext(Map.of("tenant", "acme"));
        CopilotToolRegistry.CopilotToolExecution allowed = registry.execute(
                "s", request("contextual", "{}"), configured).join();
        assertTrue(allowed.isSuccess());
        assertEquals("acme", allowed.getResult());
    }

    @Test
    void supportsPendingPermissionResolutionWithoutOfficialSdk() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(definition("approval", Map.of(), args -> "approved"));
        AtomicReference<CopilotPermissionRequest> pending = new AtomicReference<>();
        CopilotLoopConfig config = new CopilotLoopConfig()
                .setPermissionRequestHandler(request -> {
                    pending.set(request);
                    return request.decision();
                });

        CompletableFuture<CopilotToolRegistry.CopilotToolExecution> execution = registry.execute(
                "session", request("approval", "{}"), config);
        assertFalse(execution.isDone());
        assertEquals(1, registry.pendingPermissionRequests().size());
        assertTrue(pending.get().resolve(CopilotPermissionDecision.ALLOW));
        assertTrue(execution.join().isSuccess());
        assertTrue(registry.pendingPermissionRequests().isEmpty());
    }

    @Test
    void convertsHandlerTimeoutIntoStructuredTimeoutResult() {
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new CopilotToolDefinition(
                "slow", "Slow", Map.of(), invocation -> new CompletableFuture<>()));

        CopilotToolRegistry.CopilotToolExecution result = registry.execute(
                "s", request("slow", "{}"), allowAll()
                        .setToolTimeoutMillis(20)).join();
        assertFalse(result.isSuccess());
        assertEquals("timeout", result.getResultType());
        assertTrue(result.modelText().contains("timed out"));
        assertTrue(registry.cancelOutstanding(100));
    }

    @Test
    void timeoutInterruptsBlockingLegacyToolThroughBoundedExecutor() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CopilotToolRegistry registry = new CopilotToolRegistry();
        registry.register(new Tool() {
            @Override
            public String name() { return "blocking"; }

            @Override
            public String description() { return "blocking"; }

            @Override
            public String run(String input, Map<String, Object> context) {
                started.countDown();
                try {
                    Thread.sleep(10_000L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return "interrupted";
                }
                return "finished";
            }
        });

        CompletableFuture<CopilotToolRegistry.CopilotToolExecution> execution = registry.execute(
                "s", request("blocking", "{\"input\":\"x\"}"),
                allowAll().setToolTimeoutMillis(30));
        assertTrue(started.await(1, TimeUnit.SECONDS));
        CopilotToolRegistry.CopilotToolExecution result = execution.join();
        assertEquals("timeout", result.getResultType());
        assertTrue(registry.cancelOutstanding(1_000));
    }

    private static CopilotToolDefinition definition(String name,
                                                    Map<String, Object> properties,
                                                    java.util.function.Function<Map<String, Object>, Object> fn) {
        return new CopilotToolDefinition(name, name,
                Map.of("type", "object", "properties", properties),
                invocation -> CompletableFuture.completedFuture(fn.apply(invocation.getArguments())));
    }

    private static ToolExecutionRequest request(String name, String arguments) {
        return ToolExecutionRequest.builder().id(name + "-call").name(name).arguments(arguments).build();
    }

    private static CopilotLoopConfig allowAll() {
        return new CopilotLoopConfig()
                .setPermissionHandler((invocation, definition) -> CopilotPermissionDecision.ALLOW);
    }
}
