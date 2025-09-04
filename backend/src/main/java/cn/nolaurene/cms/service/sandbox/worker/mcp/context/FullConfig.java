package cn.nolaurene.cms.service.sandbox.worker.mcp.context;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
public class FullConfig {

    private String imageResponses = "auto"; // "allow", "omit", "auto"
    private boolean saveTrace = false;
    private NetworkConfig network;
    private String outputDir;
    private String sessionId;

    /**
     * 获取输出文件路径
     */
    public String getOutputFile(String filename) {
        // 简单实现，实际可以根据需要配置输出目录
        return System.getProperty("java.io.tmpdir") + "/" + filename;
    }

    /**
     * 网络配置类
     */
    @Getter
    @Setter
    public static class NetworkConfig {

        private List<String> allowedOrigins;
        private List<String> blockedOrigins;

    }
}
