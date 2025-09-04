package cn.nolaurene.cms.common.sandbox.worker.resp.file;

import lombok.Data;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description: File read result
 */
@Data
public class FileWriteResult {

    /**
     * Path of the written file
     */
    private String file;

    /**
     * Number of bytes written
     */
    private long bytesWritten;

    public FileWriteResult(String file, long bytesWritten) {
        this.file = file;
        this.bytesWritten = bytesWritten;
    }
}
