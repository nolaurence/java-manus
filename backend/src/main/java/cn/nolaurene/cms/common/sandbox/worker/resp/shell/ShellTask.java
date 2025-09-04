package cn.nolaurene.cms.common.sandbox.worker.resp.shell;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author nolaurence
 * @date 2025/5/13 13:52
 * @description: Shell task model
 */
@Data
@AllArgsConstructor
public class ShellTask {

    /**
     * Task unique identifier
     */
    private String id;

    /**
     * Executed command
     */
    private String command;

    /**
     * Task status
     */
    private String createdAt;

    /**
     * Task Output
     */
    private String output;
}
