package cn.nolaurene.cms.common.sandbox.worker.resp.shell;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @author nolaurence
 * @date 2025/5/13 13:56
 * @description: Shell session content view result model
 */
@Data
@AllArgsConstructor
public class ShellViewResult {

    /**
     * Shell session output content
     */
    private String output;

    /**
     * Shell session ID
     */
    private String sessionId;

    /**
     * Console command records
     */
    private List<ConsoleRecord> console;
}
