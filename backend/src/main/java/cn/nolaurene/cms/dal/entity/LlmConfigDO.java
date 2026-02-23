package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * @author nolaurence
 * @description LLM配置实体类
 */
@Data
@Table("llm_config")
public class LlmConfigDO {

    /**
     * 主键ID
     */
    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    /**
     * 用户ID
     */
    @Column("user_id")
    private Long userId;

    /**
     * LLM服务端点
     */
    @Column("endpoint")
    private String endpoint;

    /**
     * API密钥
     */
    @Column("api_key")
    private String apiKey;

    /**
     * 模型名称
     */
    @Column("model_name")
    private String modelName;

    /**
     * 创建时间
     */
    @Column("gmt_create")
    private Date gmtCreate;

    /**
     * 更新时间
     */
    @Column("gmt_modified")
    private Date gmtModified;

    /**
     * 是否删除
     */
    @Column("is_delete")
    private Boolean isDelete;
}
