package cn.nolaurene.cms.common.sandbox.worker.resp.supervisor;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * @author nolaurence
 * @date 2025/5/13 18:57
 * @description: Supervisor operation result model
 */
@Data
@AllArgsConstructor
public class SupervisorActionResult {
    /**
     * Operation status
     */
    private String status;

    /**
     * Operation result
     */
    private List<String> result;

    /**
     * Stop result
     */
    private List<String> stopResult;

    /**
     * Start result
     */
    private List<String> startResult;

    /**
     * Shutdown result
     */
    private List<String> shutdownResult;
}
