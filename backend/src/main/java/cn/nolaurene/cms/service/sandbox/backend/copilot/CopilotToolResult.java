package cn.nolaurene.cms.service.sandbox.backend.copilot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;

/**
 * Structured result a Java tool may return to the in-process agent loop.
 *
 * <p>The shape mirrors the Copilot tool result contract while remaining
 * independent of the official SDK.  Simple strings, maps, and lists returned
 * by existing tools are still accepted and are normalized by the registry.</p>
 */
public final class CopilotToolResult {

    public enum ResultType {
        SUCCESS("success"),
        ERROR("error"),
        FAILURE("failure"),
        REJECTED("rejected"),
        DENIED("denied"),
        TIMEOUT("timeout");

        private final String wireValue;

        ResultType(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    private final String resultType;
    private final String textResultForLlm;
    private final String error;
    private final String sessionLog;
    private final Map<String, Object> toolTelemetry;
    private final List<String> toolReferences;
    private final List<BinaryResult> binaryResultsForLlm;

    /** Constructor ordered like Copilot's Java ToolResultObject contract. */
    public CopilotToolResult(String resultType,
                             String textResultForLlm,
                             List<BinaryResult> binaryResultsForLlm,
                             String error,
                             String sessionLog,
                             Map<String, Object> toolTelemetry) {
        this(resultType, textResultForLlm, binaryResultsForLlm, error,
                sessionLog, toolTelemetry, List.of());
    }

    /** Canonical seven-argument constructor ordered like the Copilot record. */
    public CopilotToolResult(String resultType,
                             String textResultForLlm,
                             List<BinaryResult> binaryResultsForLlm,
                             String error,
                             String sessionLog,
                             Map<String, Object> toolTelemetry,
                             List<String> toolReferences) {
        this.resultType = resultType == null || resultType.isBlank()
                ? ResultType.SUCCESS.wireValue() : resultType;
        this.textResultForLlm = textResultForLlm == null ? "" : textResultForLlm;
        this.error = error;
        this.sessionLog = sessionLog;
        this.toolTelemetry = toolTelemetry == null || toolTelemetry.isEmpty()
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(toolTelemetry));
        this.toolReferences = toolReferences == null || toolReferences.isEmpty()
                ? List.of() : List.copyOf(toolReferences);
        this.binaryResultsForLlm = binaryResultsForLlm == null || binaryResultsForLlm.isEmpty()
                ? List.of() : List.copyOf(binaryResultsForLlm);
    }

    public static CopilotToolResult success(String textResultForLlm) {
        return new CopilotToolResult(ResultType.SUCCESS.wireValue(), textResultForLlm,
                List.of(), null, null, Map.of());
    }

    public static CopilotToolResult failure(String textResultForLlm, String error) {
        return new CopilotToolResult(ResultType.FAILURE.wireValue(), textResultForLlm,
                List.of(), error, null, Map.of());
    }

    public static CopilotToolResult error(String error) {
        return new CopilotToolResult(ResultType.ERROR.wireValue(), "",
                List.of(), error, null, Map.of());
    }

    /** Error result with both model-visible text and internal error detail. */
    public static CopilotToolResult error(String textResultForLlm, String error) {
        return new CopilotToolResult(ResultType.ERROR.wireValue(), textResultForLlm,
                List.of(), error, null, Map.of());
    }

    public static CopilotToolResult timeout(String message) {
        return new CopilotToolResult(ResultType.TIMEOUT.wireValue(), message,
                List.of(), message, null, Map.of());
    }

    public static CopilotToolResult rejected(String message) {
        return new CopilotToolResult(ResultType.REJECTED.wireValue(), message,
                List.of(), message, null, Map.of());
    }

    public static CopilotToolResult denied(String message) {
        return new CopilotToolResult(ResultType.DENIED.wireValue(), message,
                List.of(), message, null, Map.of());
    }

    public String resultType() { return resultType; }
    public String getResultType() { return resultType; }
    public String textResultForLlm() { return textResultForLlm; }
    public String getTextResultForLlm() { return textResultForLlm; }
    public String error() { return error; }
    public String getError() { return error; }
    public String sessionLog() { return sessionLog; }
    public String getSessionLog() { return sessionLog; }
    public Map<String, Object> toolTelemetry() { return toolTelemetry; }
    public Map<String, Object> getToolTelemetry() { return toolTelemetry; }
    public List<String> toolReferences() { return toolReferences; }
    public List<String> getToolReferences() { return toolReferences; }
    public List<BinaryResult> binaryResultsForLlm() { return binaryResultsForLlm; }
    public List<BinaryResult> getBinaryResultsForLlm() { return binaryResultsForLlm; }

    public boolean isSuccess() {
        return ResultType.SUCCESS.wireValue().equalsIgnoreCase(resultType);
    }

    @Override
    public String toString() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("resultType", resultType);
        value.put("textResultForLlm", textResultForLlm);
        value.put("error", error == null ? "" : error);
        if (!binaryResultsForLlm.isEmpty()) value.put("binaryResultsForLlm", binaryResultsForLlm);
        if (sessionLog != null) value.put("sessionLog", sessionLog);
        if (!toolTelemetry.isEmpty()) value.put("toolTelemetry", toolTelemetry);
        if (!toolReferences.isEmpty()) value.put("toolReferences", toolReferences);
        return JSON.toJSONString(value);
    }

    /** Binary image/resource content a tool wants to expose to the model. */
    public static final class BinaryResult {
        private final String data;
        private final String mimeType;
        private final String type;
        private final String description;

        public BinaryResult(String data, String mimeType, String type, String description) {
            this.data = data == null ? "" : data;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.type = type == null || type.isBlank() ? "image" : type;
            this.description = description;
        }

        public String data() { return data; }
        public String getData() { return data; }
        public String mimeType() { return mimeType; }
        public String getMimeType() { return mimeType; }
        public String type() { return type; }
        public String getType() { return type; }
        public String description() { return description; }
        public String getDescription() { return description; }
    }
}
