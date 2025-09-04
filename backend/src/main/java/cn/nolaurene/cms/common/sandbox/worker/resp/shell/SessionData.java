package cn.nolaurene.cms.common.sandbox.worker.resp.shell;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Date: 2025/5/13
 * Author: nolaurence
 * Description:
 */
@Data
@AllArgsConstructor
public class SessionData {

    private Process process;

    private String execDir;

    private String output;

    private List<ConsoleRecord> console;
}
