package cn.nolaurene.cms.common.sandbox.worker.resp.file;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Date: 2025/5/12
 * Author: nolaurence
 * Description: File content search result
 */
@Data
@AllArgsConstructor
public class FileSearchResult {

    /**
     * Path of the searched file
     */
    private String file;

    /**
     * List of matched content
     */
    private List<String> matches;

    /**
     * List of matched line numbers
     */
    private List<Integer> lineNumbers;
}
