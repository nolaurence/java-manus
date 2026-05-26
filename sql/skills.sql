CREATE TABLE IF NOT EXISTS `skills` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `skill_id` VARCHAR(128) NOT NULL COMMENT '技能唯一标识',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `name` VARCHAR(255) NOT NULL COMMENT '技能名称',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '技能描述',
    `version` VARCHAR(64) DEFAULT '1.0.0' COMMENT '版本号',
    `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    `source_dir` VARCHAR(500) DEFAULT NULL COMMENT '技能源码目录',
    `installed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '安装时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_user` (`skill_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能表';

CREATE TABLE IF NOT EXISTS `agent_skills` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `agent_id` VARCHAR(64) NOT NULL COMMENT 'Agent ID',
    `skill_id` VARCHAR(128) NOT NULL COMMENT '技能标识',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_skill` (`agent_id`, `skill_id`),
    KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent技能关联表';
