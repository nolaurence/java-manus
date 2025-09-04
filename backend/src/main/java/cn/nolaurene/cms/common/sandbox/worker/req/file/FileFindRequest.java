package cn.nolaurene.cms.common.sandbox.worker.req.file;

import lombok.Data;

/**
 * Date: 2025/5/19
 * Author: nolaurence
 * Description: File find request
 */
@Data
public class FileFindRequest {

    /**
     * Directory path to search
     */
    private String path;

    /**
     * Filename pattern (glob syntax)
     */
    private String glob;
}
