package cn.nolaurene.cms.common.sandbox.worker.req.file;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: File content search request
 */
@Data
public class FileSearchRequest {

    /**
     * """File content search request"""
     *     file: str = Field(..., description="Absolute file path")
     *     regex: str = Field(..., description="Regular expression pattern")
     *     sudo: Optional[bool] = Field(False, description="Whether to use sudo privileges")
     */

    /**
     * Absolute file path
     */
    private String file;

    /**
     * Regular expression pattern
     */
    private String regex;

    /**
     * Whether to use sudo privileges
     */
    private boolean sudo;
}
