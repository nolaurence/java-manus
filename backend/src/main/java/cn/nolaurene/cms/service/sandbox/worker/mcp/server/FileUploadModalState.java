package cn.nolaurene.cms.service.sandbox.worker.mcp.server;


import cn.nolaurene.cms.service.sandbox.worker.mcp.context.ModalStateWithTab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import com.microsoft.playwright.FileChooser;
import lombok.Getter;

/**
 * @author nolaurence
 * @date 2025/7/2 下午1:51
 * @description:
 */
@Getter
public class FileUploadModalState extends ModalStateWithTab {
    private final FileChooser fileChooser;

    public FileUploadModalState(String description, FileChooser fileChooser) {
        super(ModalStateType.FILE_CHOOSER, description, null);
        this.fileChooser = fileChooser;
    }

    public FileUploadModalState(String description, FileChooser fileChooser, Tab tab) {
        super(ModalStateType.FILE_CHOOSER, description, tab);
        this.fileChooser = fileChooser;
    }

    @Override
    public String toString() {
        return "FileUploadModalState{" +
                "type=" + getType() +
                ", description='" + getDescription() + '\'' +
                ", fileChooser=" + fileChooser +
                '}';
    }
}
