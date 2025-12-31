package cn.nolaurene.cms.dal.entity;

import io.mybatis.provider.Entity.Column;
import io.mybatis.provider.Entity.Table;
import lombok.Data;

import java.util.Date;

@Data
@Table("agent_session_server")
public class AgentSessionServerDO {

    @Column(id = true, remark = "主键", updatable = false, insertable = false)
    private Long id;

    @Column("agent_id")
    private String agentId;

    @Column("server_ip")
    private String serverIp;

    @Column("server_port")
    private Integer serverPort;

    @Column("gmt_create")
    private Date gmtCreate;

    @Column("gmt_modified")
    private Date gmtModified;
}
