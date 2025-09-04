package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.context.ModalStateWithTab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.DialogModalState;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.ModalStateType;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import lombok.Data;

import java.util.List;

/**
 * @author nolaurence
 * @date 2025/7/8 下午3:40
 * @description:
 */
public class DialogTool {

    @Data
    public static class HandleDialogInput {
        @FieldDescription("Whether to accept the dialog.")
        private boolean accept;

        @FieldDescription("The text of the prompt in case of a prompt dialog.")
        private String promptText;
    }

    public static ToolFactory createHandleDialogToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<HandleDialogInput> schema = ToolSchema.<HandleDialogInput>builder()
                    .name("browser_handle_dialog")
                    .title("Handle a dialog")
                    .description("Handle a dialog")
                    .inputSchema(new HandleDialogInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<HandleDialogInput> handler = (context, params) -> {
                try {
                    ModalStateWithTab dialogState = context.modalStates()
                            .stream()
                            .filter(state -> state.getType().equals(ModalStateType.DIALOG))
                            .findFirst()
                            .orElse(null);

                    if (null == dialogState) {
                        throw new Exception("No dialog visible");
                    }

                    if (params.isAccept()) {
                        ((DialogModalState) dialogState).getDialog().accept(params.getPromptText());
                    } else {
                        ((DialogModalState) dialogState).getDialog().dismiss();
                    }

                    context.clearModalState(dialogState);
                    List<String> code = List.of(String.format("// <internal code to handle \"%s\" dialog>", ((DialogModalState) dialogState).getDialog().type()));
                    return ToolResult.builder()
                            .code(code)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to handle dialog: ", e);
                }
            };
            return Tool.<HandleDialogInput>builder()
                    .capability(ToolCapability.CORE)
                    .schema(schema)
                    .handler(handler)
                    .clearsModalState(ModalStateType.DIALOG)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createHandleDialogToolFactory().createTool(captureSnapshot));
    }
}
