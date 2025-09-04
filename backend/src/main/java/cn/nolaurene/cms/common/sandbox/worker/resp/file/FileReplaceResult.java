package cn.nolaurene.cms.common.sandbox.worker.resp.file;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description: File content replacement result
 */
@Data
@AllArgsConstructor
public class FileReplaceResult {

    /**
     * Path if the operated file
     */
    private String file;

    /**
     * Number of replacements
     */
    private int replacedCount;


}
