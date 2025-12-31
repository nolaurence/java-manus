-- Agent Session Server IP Mapping Table
-- 用于记录每个session（agentId）连接的后端服务器IP，支持分布式部署

CREATE TABLE IF NOT EXISTS `agent_session_server` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `agent_id` VARCHAR(64) NOT NULL COMMENT 'Agent ID（Session ID）',
    `server_ip` VARCHAR(64) NOT NULL COMMENT '后端服务器IP地址',
    `server_port` INT(11) DEFAULT NULL COMMENT '后端服务器端口',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_id` (`agent_id`),
    KEY `idx_server_ip` (`server_ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Session与后端服务器映射表';
