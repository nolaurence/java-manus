package cn.nolaurene.cms.service.sandbox.worker.mcp.context;

import cn.nolaurene.cms.service.sandbox.worker.mcp.server.ModalState;
import cn.nolaurene.cms.service.sandbox.worker.mcp.Tab;
import cn.nolaurene.cms.service.sandbox.worker.mcp.server.ModalStateType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModalStateWithTab extends ModalState {

    /**
     * 完成Tab的兼容后，更改了这个类型 page -> Tab
     */
    private Tab tab;

    public ModalStateWithTab(ModalState state, Tab tab) {
        super(state.getType(), state.getDescription());
        this.tab = tab;
    }

    public ModalStateWithTab(ModalStateType type, String description, Tab tab) {
        super(type, description);
        this.tab = tab;
    }

    public boolean equals(ModalStateWithTab stateWithTab) {
        if (!super.equals(stateWithTab)) {
            return false;
        }
        if (null == stateWithTab.getTab() || !stateWithTab.getTab().equals(tab)) {
            return false;
        }
        return true;
    }
}
