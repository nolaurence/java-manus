-- Skill 主表：存储 skill 基本信息
CREATE TABLE IF NOT EXISTS `skill_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `skill_id` VARCHAR(128) NOT NULL COMMENT 'Skill唯一标识符（如：author/skill-name）',
    `name` VARCHAR(128) NOT NULL COMMENT 'Skill名称',
    `version` VARCHAR(32) NOT NULL DEFAULT '1.0.0' COMMENT '版本号',
    `author` VARCHAR(64) NOT NULL COMMENT '作者',
    `description` TEXT COMMENT 'Skill描述',
    `category` VARCHAR(64) COMMENT '分类标签',
    `triggers` JSON COMMENT '触发器配置（JSON数组）',
    `tools` JSON COMMENT '工具定义（JSON数组）',
    `requires` JSON COMMENT '依赖配置（bins, env, config）',
    `os_support` JSON COMMENT '支持的操作系统列表',
    `priority` INT DEFAULT 0 COMMENT '优先级（Workspace > Local > Bundled）',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用, 0-禁用',
    `user_id` BIGINT COMMENT '所属用户ID（NULL表示系统级）',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` TINYINT DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_id` (`skill_id`),
    KEY `idx_author` (`author`),
    KEY `idx_category` (`category`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill信息表';

-- Skill 文档表：存储 skill 的多个文档（SKILL.md、README等）
CREATE TABLE IF NOT EXISTS `skill_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `skill_id` VARCHAR(128) NOT NULL COMMENT '关联的Skill ID',
    `doc_type` VARCHAR(32) NOT NULL COMMENT '文档类型：SKILL_MD, README, REFERENCE, EXAMPLE, SCRIPT',
    `doc_name` VARCHAR(128) COMMENT '文档名称',
    `content` LONGTEXT COMMENT '文档内容',
    `file_path` VARCHAR(512) COMMENT '原始文件路径（可选）',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` TINYINT DEFAULT 0 COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_skill_id` (`skill_id`),
    KEY `idx_doc_type` (`doc_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill文档表';

-- Skill 执行记录表：记录 skill 执行历史
CREATE TABLE IF NOT EXISTS `skill_execution_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `skill_id` VARCHAR(128) NOT NULL COMMENT '执行的Skill ID',
    `session_id` VARCHAR(64) COMMENT '关联的会话ID',
    `user_id` BIGINT COMMENT '执行用户ID',
    `input_params` JSON COMMENT '输入参数',
    `output_result` TEXT COMMENT '执行结果',
    `status` VARCHAR(32) COMMENT '执行状态：SUCCESS, FAILED, TIMEOUT',
    `error_message` TEXT COMMENT '错误信息',
    `duration_ms` BIGINT COMMENT '执行耗时（毫秒）',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_skill_id` (`skill_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill执行日志表';

-- 用户Skill状态表：记录每个用户对Skill的启用/禁用状态
CREATE TABLE IF NOT EXISTS `user_skill_status` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `skill_id` VARCHAR(128) NOT NULL COMMENT 'Skill ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态：1-启用, 0-禁用',
    `gmt_create` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_skill` (`user_id`, `skill_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_skill_id` (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户Skill状态表';
