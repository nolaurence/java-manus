package cn.nolaurene.cms.common.sandbox.worker.req.file;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: File read request
 */
@Data
public class FileReadRequest {

    /**
     * Absolute file path
     */
    private String file;

    /**
     * Start line (0-based)
     */
    private Integer startLine;

    /**
     * End line (not inclusive)
     */
    private Integer endLine;

    /**
     * Whether to use sudo privileges
     */
    private boolean sudo;
}
