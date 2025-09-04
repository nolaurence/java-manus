package cn.nolaurene.cms.common.sandbox.worker.resp.shell;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @author nolaurence
 * @date 2025/5/13 13:53
 * @description: Shell command execution result model
 */
@Data
@AllArgsConstructor
public class ShellCommandResult {

    /**
     * Shell session ID
     */
    private String sessionId;

    /**
     * Executed command
     */
    private String command;

    /**
     * Command execution status
     */
    private String status;

    /**
     * Process return code, only has value when status is completed
     */
    private int returncode;

    /**
     * Command execution output, only has value when status is completed
     */
    private String output;

    /**
     * Console command records
     */
    private List<ConsoleRecord> console;
}
