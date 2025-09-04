package cn.nolaurene.cms.common.sandbox.worker.req.shell;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: Shell process wait request model
 */
@Data
public class ShellWaitRequest {

    /**
     * Unique identifier of the target shell session
     */
    private String id;

    /**
     * Wait time (seconds)
     */
    private Integer seconds;
}
