package cn.nolaurene.cms.common.sandbox.worker.resp.file;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * file read result
 */
@Data
@AllArgsConstructor
public class FileReadResult {

    /**
     * File content
     */
    private String content;

    /**
     * Path of the read file
     */
    private String file;
}
