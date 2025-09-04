package cn.nolaurene.cms.common.sandbox.worker.resp.file;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description: File find result
 */
@Data
@AllArgsConstructor
public class FileFindResult {

    /**
     * Path of the search directory
     */
    private String path;

    /**
     * List of found files
     */
    private List<String> files;
}
