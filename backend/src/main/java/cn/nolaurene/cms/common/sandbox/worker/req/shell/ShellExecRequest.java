package cn.nolaurene.cms.common.sandbox.worker.req.shell;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: Shell command execution request model
 */
@Data
public class ShellExecRequest {

    /**
     * Unique identifier of the target shell session, if not provided, one will be automatically created
     */
    private String id;

    /**
     * Working directory for command execution (must use absolute path)
     */
    private String execDir;

    /**
     * Shell command to execute
     */
    private String command;
}
