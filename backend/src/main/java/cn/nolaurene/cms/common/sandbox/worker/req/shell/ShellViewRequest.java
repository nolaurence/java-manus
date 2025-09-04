package cn.nolaurene.cms.common.sandbox.worker.req.shell;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: Shell session content view request model
 */
@Data
public class ShellViewRequest {

    /**
     * Unique identifier of the target shell session
     */
    private String id;
}
