package cn.nolaurene.cms.common.sandbox.worker.resp.supervisor;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author nolaurence
 * @date 2025/5/13 18:51
 * @description: Process information model
 */
@Data
@AllArgsConstructor
public class ProcessInfo {

    /**
     * Process name
     */
    private String name;

    /**
     * Process group
     */
    private String group;

    /**
     * Process description
     */
    private String description;

    /**
     * Start timestamp
     */
    private int start;

    /**
     * Stop timestamp
     */
    private int stop;

    /**
     * Current timestamp
     */
    private int now;

    /**
     * State code
     */
    private int state;

    /**
     * State name
     */
    private String statename;

    /**
     * Spawn error
     */
    private String spawnerr;

    /**
     * Exit status code
     */
    private int exitstatus;

    /**
     * Log file
     */
    private String logfile;

    /**
     * Standard output log file
     */
    private String stdoutLogfile;

    /**
     * Standard error log file
     */
    private String stderrLogfile;

    /**
     * Process ID
     */
    private int pid;
}
