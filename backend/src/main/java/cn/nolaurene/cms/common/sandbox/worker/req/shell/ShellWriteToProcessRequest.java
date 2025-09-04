package cn.nolaurene.cms.common.sandbox.worker.req.shell;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: Request model for writing input to a running process
 */
@Data
public class ShellWriteToProcessRequest {

    /**
     * Unique identifier of the target shell session
     */
    private String id;

    /**
     * Input content to write to the process
     */
    private String input;

    /**
     * Whether to press enter key after input
     */
    private Boolean pressEnter;
}
