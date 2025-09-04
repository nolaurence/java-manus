package cn.nolaurene.cms.service.sandbox.worker.mcp.server;


import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * @author nolaurence
 * @date 2025/7/2 下午1:49
 * @description:
 */
@Getter
public abstract class ModalState {

    private final ModalStateType type;
    private final String description;

    protected ModalState(ModalStateType type, String description) {
        this.type = type;
        this.description = description;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        ModalState that = (ModalState) obj;
        return type == that.type &&
                (Objects.equals(description, that.description));
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }
}
