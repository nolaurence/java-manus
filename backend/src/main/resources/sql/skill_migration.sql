-- Skill 表结构迁移脚本
-- 从旧版本（含扩展字段）迁移到新版（遵循 https://agentskills.io/specification 规范）
-- 执行前请备份数据！

-- ============================================
-- 1. 添加新字段（如果不存在）
-- ============================================

-- 添加 license 字段
ALTER TABLE `skill_info`
ADD COLUMN IF NOT EXISTS `license` VARCHAR(64) COMMENT '许可证（可选）';

-- 添加 compatibility 字段
ALTER TABLE `skill_info`
ADD COLUMN IF NOT EXISTS `compatibility` VARCHAR(500) COMMENT '兼容性说明：环境要求、系统包、网络访问等（可选，最多500字符）';

-- 添加 metadata 字段
ALTER TABLE `skill_info`
ADD COLUMN IF NOT EXISTS `metadata` JSON COMMENT '元数据：任意键值对（可选）';

-- 添加 allowed_tools 字段
ALTER TABLE `skill_info`
ADD COLUMN IF NOT EXISTS `allowed_tools` VARCHAR(512) COMMENT '允许使用的工具列表：空格分隔（可选，实验性）';

-- ============================================
-- 2. 修改字段长度（符合规范）
-- ============================================

-- 修改 name 字段长度为 64
ALTER TABLE `skill_info`
MODIFY COLUMN `name` VARCHAR(64) NOT NULL COMMENT 'Skill名称：小写字母、数字、连字符，1-64字符';

-- 修改 description 字段为 VARCHAR(1024)
ALTER TABLE `skill_info`
MODIFY COLUMN `description` VARCHAR(1024) NOT NULL COMMENT 'Skill描述：1-1024字符，描述功能和何时使用';

-- ============================================
-- 3. 删除扩展字段（如果存在）
-- ============================================

-- 删除 category 字段
ALTER TABLE `skill_info`
DROP COLUMN IF EXISTS `category`;

-- 删除 triggers 字段
ALTER TABLE `skill_info`
DROP COLUMN IF EXISTS `triggers`;

-- 删除 tools 字段
ALTER TABLE `skill_info`
DROP COLUMN IF EXISTS `tools`;

-- 删除 requires 字段
ALTER TABLE `skill_info`
DROP COLUMN IF EXISTS `requires`;

-- 删除 os_support 字段
ALTER TABLE `skill_info`
DROP COLUMN IF EXISTS `os_support`;

-- 删除 priority 字段
ALTER TABLE `skill_info`
DROP COLUMN IF EXISTS `priority`;

-- ============================================
-- 4. 更新表注释
-- ============================================

ALTER TABLE `skill_info` COMMENT = 'Skill信息表 - 遵循 https://agentskills.io/specification 规范';

-- ============================================
-- 5. 删除相关索引（如果存在）
-- ============================================

-- 删除 category 索引
DROP INDEX IF EXISTS `idx_category` ON `skill_info`;
