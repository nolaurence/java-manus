package cn.nolaurene.cms.service.sandbox.worker.mcp.server;


import cn.nolaurene.cms.service.sandbox.worker.mcp.context.ModalStateWithTab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import com.microsoft.playwright.Dialog;
import lombok.Getter;

/**
 * @author nolaurence
 * @date 2025/7/2 下午1:53
 * @description:
 */
@Getter
public class DialogModalState extends ModalStateWithTab {
    private final Dialog dialog;

    public DialogModalState(String description, Dialog dialog) {
        super(ModalStateType.DIALOG, description, null);
        this.dialog = dialog;
    }

    public DialogModalState(String description, Dialog dialog, Tab tab) {
        super(ModalStateType.DIALOG, description, tab);
        this.dialog = dialog;
    }

    @Override
    public String toString() {
        return "DialogModalState{" +
                "type=" + getType() +
                ", description='" + getDescription() + '\'' +
                ", dialog=" + dialog +
                '}';
    }
}
