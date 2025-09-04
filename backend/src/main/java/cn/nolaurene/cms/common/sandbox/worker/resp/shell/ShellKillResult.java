package cn.nolaurene.cms.common.sandbox.worker.resp.shell;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author nolaurence
 * @date 2025/5/13 13:59
 * @description: Process termination result model
 */
@Data
@AllArgsConstructor
public class ShellKillResult {

    /**
     *  Process status
     */
    private String status;

    /**
     * Process return code
     */
    private int returncode;
}
