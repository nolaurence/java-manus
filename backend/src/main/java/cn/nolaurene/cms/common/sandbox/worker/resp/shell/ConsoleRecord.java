package cn.nolaurene.cms.common.sandbox.worker.resp.shell;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description: Shell command console record model
 */
@Data
@AllArgsConstructor
public class ConsoleRecord {

    /**
     * Command prompt
     */
    private String ps1;

    /**
     * Executed command
     */
    private String command;

    /**
     * Command output
     */
    private String output;
}
