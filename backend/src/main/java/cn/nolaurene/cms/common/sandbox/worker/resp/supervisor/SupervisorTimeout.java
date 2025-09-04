package cn.nolaurene.cms.common.sandbox.worker.resp.supervisor;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author nolaurence
 * @date 2025/5/13 19:00
 * @description: Supervisor timeout model
 */
@Data
@AllArgsConstructor
public class SupervisorTimeout {

    /**
     * Timeout setting status
     */
    private String status;

    /**
     * Whether timeout is active
     */
    private boolean active;

    /**
     * Shutdown time
     */
    private String shutdownTime;

    /**
     * Timeout duration (minutes)
     */
    private float timeoutMinutes;

    /**
     * Remaining seconds
     */
    private float remainingSeconds;
}
