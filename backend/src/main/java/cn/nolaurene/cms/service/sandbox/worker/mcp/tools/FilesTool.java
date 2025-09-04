package cn.nolaurene.cms.service.sandbox.worker.mcp.tools;


import cn.nolaurene.cms.service.sandbox.worker.mcp.context.ModalStateWithTab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.FileUploadModalState;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.ModalStateType;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.*;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * @author nolaurence
 * @date 2025/7/8 下午5:24
 * @description:
 */
public class FilesTool {

    @Data
    public static class UploadFileInput {

        @FieldDescription("The absolute paths to the files to upload. Can be a single file or multiple files.")
        private List<String> paths;
    }

    public static ToolFactory createUploadFileToolFactory() {
        return (captureSnapshot) -> {
            ToolSchema<UploadFileInput> schema = ToolSchema.<UploadFileInput>builder()
                    .name("browser_file_upload")
                    .title("Upload files")
                    .description("Upload one or multiple files")
                    .inputSchema(new UploadFileInput())
                    .type(ToolType.DESTRUCTIVE)
                    .build();

            ToolHandler<UploadFileInput> handler = (context, params) -> {
                try {
                    ModalStateWithTab modelState = context.modalStates()
                            .stream()
                            .filter(state -> state.getType().equals(ModalStateType.FILE_CHOOSER))
                            .findFirst()
                            .orElse(null);

                    if (null == modelState) {
                        throw new RuntimeException("No file chooser visible");
                    }
                    List<String> code = List.of(String.format("// <internal code to chose files %s", String.join(", ", params.getPaths())));

                    Supplier<CompletableFuture<ToolActionResult>> action = () -> CompletableFuture.runAsync(() -> {
                        Path[] pathList = params.getPaths()
                                .stream()
                                .map(Path::of).toArray(Path[]::new);

                        ((FileUploadModalState) modelState).getFileChooser().setFiles(pathList);
                        context.clearModalState(modelState);
                    }).thenApply(v -> ToolActionResult.empty());

                    return ToolResult.builder()
                            .code(code)
                            .action(action)
                            .captureSnapshot(captureSnapshot)
                            .waitForNetwork(false)
                            .build();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to upload file: ", e);
                }
            };

            return Tool.<UploadFileInput>builder()
                    .capability(ToolCapability.FILES)
                    .schema(schema)
                    .handler(handler)
                    .clearsModalState(ModalStateType.FILE_CHOOSER)
                    .build();
        };
    }

    public static List<Tool<?>> getAllTools(boolean captureSnapshot) {
        return List.of(createUploadFileToolFactory().createTool(captureSnapshot));
    }
}
