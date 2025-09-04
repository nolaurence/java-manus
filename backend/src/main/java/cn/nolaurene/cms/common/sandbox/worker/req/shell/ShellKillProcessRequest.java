package cn.nolaurene.cms.common.sandbox.worker.req.shell;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: Request model for terminating a running process
 */
@Data
public class ShellKillProcessRequest {

    /**
     * Unique identifier of the target shell session
     */
    private String id;
}
