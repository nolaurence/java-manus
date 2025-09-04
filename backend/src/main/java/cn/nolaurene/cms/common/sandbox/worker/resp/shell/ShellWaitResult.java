package cn.nolaurene.cms.common.sandbox.worker.resp.shell;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author nolaurence
 * @date 2025/5/13 13:57
 * @description: Process wait result model
 */
@Data
@AllArgsConstructor
public class ShellWaitResult {

    private String sessionId;
    /**
     * Process return code
     */
    private int returncode;
}
