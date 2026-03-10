package cn.nolaurene.cms.common.dto.skill;

import lombok.Data;

/**
 * Skill文档请求
 *
 * @author nolaurence
 */
@Data
public class SkillDocumentRequest {

    /**
     * 文档类型：SKILL_MD, README, REFERENCE, EXAMPLE, SCRIPT
     */
    private String docType;

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 原始文件路径
     */
    private String filePath;

    /**
     * 排序顺序
     */
    private Integer sortOrder;
}
