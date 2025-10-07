package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

/**
 * Description:conversation brief info
 * @author nolaurence
 * Date 2025-10-07
 */
@Data
@Table("conversation_info")
public class ConversationInfoDO {

    /**
     * 会话ID
     */
    @Column("session_id")
    private String sessionId;

    /**
     * 对话标题
     */
    @Column("title")
    private String title;

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
     * 删除时间
     */
    @Column("gmt_deleted")
    private Date gmtDeleted;
}