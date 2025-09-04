package cn.nolaurene.cms.common.sandbox.worker;

import cn.nolaurene.cms.common.sandbox.worker.resp.shell.ConsoleRecord;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nolaurence
 * @date 2025/5/13 19:12
 * @description:
 */
@Data
@AllArgsConstructor
public class ShellSession {
    private String sessionId;
    private Process process;
    private String execDir;
    private StringBuilder output = new StringBuilder();
    private List<ConsoleRecord> console = new ArrayList<>();

    public ShellSession(String sessionId, Process process, String execDir) {
        this.sessionId = sessionId;
        this.process = process;
        this.execDir = execDir;
    }
}
