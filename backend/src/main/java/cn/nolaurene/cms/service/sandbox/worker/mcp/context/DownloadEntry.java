package cn.nolaurene.cms.service.sandbox.worker.mcp.context;

import com.microsoft.playwright.Download;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadEntry {

    private Download download;

    private boolean finished;

    private String outputFile;
}
