package cn.nolaurene.cms.service.sandbox.backend.tool;

import java.util.Map;

public class MessageTool implements Tool {

    @Override
    public String name() {
        return "message";
    }

    @Override
    public String description() {
        return "Send a message to frontend using SseEmitter";
    }

    @Override
    public String run(String input, Map<String, Object> context) {
        // Here you would implement the logic to send a message to the agent.
        // For now, we just return a confirmation message.
        return "Message sent: " + input;
    }
}
