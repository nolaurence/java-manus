package cn.nolaurene.cms.common.sandbox.worker.req.file;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: File content replacement request
 */
@Data
public class FileReplaceRequest {

    /**
     * Absolute file path
     */
    private String file;

    /**
     * Original string to replace
     */
    private String oldStr;

    /**
     * New string to replace with
     */
    private String newStr;

    /**
     * Whether to use sudo privileges
     */
    private boolean sudo;
}
