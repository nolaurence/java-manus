package cn.nolaurene.cms.common.sandbox.worker.req.file;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: File write request
 */
@Data
public class FileWriteRequest {

    /**
     * Absolute file path
     */
    private String file;

    /**
     * Content to write
     */
    private String content;

    /**
     * Whether to use append mode
     */
    private boolean append;

    /**
     * Whether to add leading newline
     */
    private boolean leadingNewline;

    /**
     * Whether to add trailing newline
     */
    private boolean trailingNewline;

    /**
     * Whether to use sudo privileges
     */
    private boolean sudo;
}
