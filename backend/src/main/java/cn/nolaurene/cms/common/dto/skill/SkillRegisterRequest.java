package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Skill注册请求
 * 遵循 https://agentskills.io/specification 规范
 *
 * @author nolaurence
 */
@Data
public class SkillRegisterRequest {

    /**
     * Skill名称：小写字母、数字、连字符，1-64字符（必需）
     */
    private String name;

    /**
     * Skill描述：1-1024字符，描述功能和何时使用（必需）
     */
    private String description;

    private String version;

    private String author;

    /**
     * 许可证（可选）
     */
    private String license;

    /**
     * 兼容性说明：环境要求、系统包、网络访问等（可选，最多500字符）
     */
    private String compatibility;

    /**
     * 元数据：任意键值对（可选）
     */
    private Map<String, String> metadata;

    /**
     * 允许使用的工具列表：空格分隔（可选，实验性）
     * 例如: "Bash(git:*) Bash(jq:*) Read"
     */
    private String allowedTools;

    private Long userId;

    /**
     * 文档列表
     */
    private List<SkillDocumentRequest> documents;
}
