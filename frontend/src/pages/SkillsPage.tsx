import React, { useState, useEffect } from 'react';
import {
  Button,
  Input,
  Card,
  Form,
  message,
  Space,
  Divider,
  Modal,
  Table,
  Tag,
  Tooltip,
  Popconfirm,
  Tabs,
  Select,
  InputNumber,
  Switch,
  Collapse,
  Empty,
  Spin,
  Upload,
} from 'antd';
import { useNavigate } from 'react-router';
import {
  ArrowLeft,
  Plus,
  Trash2,
  Edit,
  Play,
  FileText,
  RefreshCw,
  Search,
  Code,
  BookOpen,
  Zap,
  Upload as UploadIcon,
  User,
} from 'lucide-react';
import ManusLogoTextIcon from '@/components/icons/ManusLogoTextIcon';
import { createStyles } from 'antd-style';
import type {
  SkillDefinition,
  SkillRegisterRequest,
  SkillUpdateRequest,
  SkillDocument,
} from '@/types/skill';
import {
  listSkills,
  registerSkill,
  registerSkillFromMd,
  updateSkill,
  deleteSkill,
  enableSkill,
  disableSkill,
  executeSkill,
  getSkillDocuments,
  addSkillDocument,
  refreshSkillCache,
  importSkillFromZip,
  getEnabledSkillsForUser,
  enableSkillForUser,
  disableSkillForUser,
  initializeUserSkillStatus,
} from '@/services/api/skill';

const useStyles = createStyles((utils) => {
  const css = utils.css;
  return {
    container: css`
      min-height: 100vh;
      background-color: var(--background-gray-main);
      padding: 20px;
    `,
    header: css`
      display: flex;
      align-items: center;
      justify-content: space-between;
      max-width: 1200px;
      margin: 0 auto 20px;
    `,
    backButton: css`
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 16px;
      border-radius: 8px;
      cursor: pointer;
      border: none;
      background: transparent;
      color: var(--text-primary);

      &:hover {
        background-color: var(--fill-tsp-gray-main);
      }
    `,
    logoContainer: css`
      display: flex;
      align-items: center;
      gap: 12px;
    `,
    contentCard: css`
      max-width: 1200px;
      margin: 0 auto;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    `,
    toolbar: css`
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    `,
    searchInput: css`
      width: 300px;
    `,
    skillCard: css`
      margin-bottom: 12px;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      }
    `,
    skillHeader: css`
      display: flex;
      justify-content: space-between;
      align-items: center;
    `,
    skillTitle: css`
      font-size: 16px;
      font-weight: 600;
      color: var(--text-primary);
    `,
    skillMeta: css`
      display: flex;
      gap: 8px;
      margin-top: 8px;
    `,
    skillDescription: css`
      color: var(--text-secondary);
      margin-top: 8px;
      font-size: 14px;
    `,
    modalForm: css`
      .ant-form-item {
        margin-bottom: 16px;
      }
    `,
    codeEditor: css`
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      font-size: 13px;
    `,
    triggerItem: css`
      padding: 8px 12px;
      background: #f5f5f5;
      border-radius: 6px;
      margin-bottom: 8px;
    `,
    toolItem: css`
      padding: 12px;
      background: #fafafa;
      border: 1px solid #e8e8e8;
      border-radius: 8px;
      margin-bottom: 12px;
    `,
    emptyState: css`
      padding: 60px 20px;
      text-align: center;
    `,
    detailSection: css`
      margin-bottom: 20px;
    `,
    detailLabel: css`
      font-weight: 600;
      color: var(--text-primary);
      margin-bottom: 8px;
    `,
    detailContent: css`
      color: var(--text-secondary);
    `,
    paramRow: css`
      display: flex;
      gap: 8px;
      margin-bottom: 8px;
      align-items: center;
    `,
  };
});

const SkillsPage: React.FC = () => {
  const { styles } = useStyles();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(false);
  const [skills, setSkills] = useState<SkillDefinition[]>([]);
  const [searchText, setSearchText] = useState('');
  const [registerModalOpen, setRegisterModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [executeModalOpen, setExecuteModalOpen] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [zipImportModalOpen, setZipImportModalOpen] = useState(false);
  const [userStatusModalOpen, setUserStatusModalOpen] = useState(false);
  const [selectedSkill, setSelectedSkill] = useState<SkillDefinition | null>(null);
  const [skillDocuments, setSkillDocuments] = useState<SkillDocument[]>([]);
  const [enabledSkillIds, setEnabledSkillIds] = useState<string[]>([]);
  const [uploading, setUploading] = useState(false);

  const [registerForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [executeForm] = Form.useForm();
  const [importForm] = Form.useForm();

  // 加载 Skill 列表和用户启用状态
  useEffect(() => {
    loadSkills();
    loadUserEnabledSkills();
  }, []);

  const loadSkills = async () => {
    setLoading(true);
    try {
      const data = await listSkills();
      setSkills(data);
    } catch (error) {
      console.error('加载 Skills 失败:', error);
      message.error('加载 Skills 失败');
    } finally {
      setLoading(false);
    }
  };

  const loadUserEnabledSkills = async () => {
    try {
      const data = await getEnabledSkillsForUser();
      setEnabledSkillIds(data);
    } catch (error) {
      console.error('加载用户启用 Skills 失败:', error);
    }
  };

  const handleRefreshCache = async () => {
    try {
      await refreshSkillCache();
      message.success('缓存已刷新');
      loadSkills();
    } catch (error) {
      message.error('刷新缓存失败');
    }
  };

  // 注册 Skill
  const handleRegister = async (values: any) => {
    try {
      // 将 author 放入 metadata 中，符合 Agent Skills 规范
      const metadata: Record<string, string> = values.metadata || {};
      if (values.author) {
        metadata.author = values.author;
      }

      const request: SkillRegisterRequest = {
        name: values.name,
        description: values.description,
        version: values.version || '1.0.0',
        license: values.license,
        compatibility: values.compatibility,
        metadata: metadata,
        allowedTools: values.allowedTools,
      };
      await registerSkill(request);
      message.success('Skill 注册成功');
      setRegisterModalOpen(false);
      registerForm.resetFields();
      loadSkills();
    } catch (error) {
      message.error('注册失败');
    }
  };

  // 从 SKILL.md 导入
  const handleImport = async (values: { content: string }) => {
    try {
      await registerSkillFromMd(values.content);
      message.success('Skill 导入成功');
      setImportModalOpen(false);
      importForm.resetFields();
      loadSkills();
    } catch (error) {
      message.error('导入失败');
    }
  };

  // 编辑 Skill
  const handleEdit = async (values: any) => {
    if (!selectedSkill) return;
    try {
      const request: SkillUpdateRequest = {
        name: values.name,
        version: values.version,
        description: values.description,
        category: values.category,
        priority: values.priority,
        triggers: values.triggers,
        tools: values.tools,
      };
      await updateSkill(selectedSkill.skillId, request);
      message.success('更新成功');
      setEditModalOpen(false);
      editForm.resetFields();
      loadSkills();
    } catch (error) {
      message.error('更新失败');
    }
  };

  // 删除 Skill
  const handleDelete = async (skillId: string) => {
    try {
      await deleteSkill(skillId);
      message.success('删除成功');
      loadSkills();
    } catch (error) {
      message.error('删除失败');
    }
  };

  // 启用/禁用 Skill（全局）
  const handleToggleStatus = async (skill: SkillDefinition) => {
    try {
      if (skill.status === 1) {
        await disableSkill(skill.skillId);
        message.success('已禁用');
      } else {
        await enableSkill(skill.skillId);
        message.success('已启用');
      }
      loadSkills();
    } catch (error) {
      message.error('操作失败');
    }
  };

  // 为用户启用/禁用 Skill
  const handleToggleUserStatus = async (skillId: string, enabled: boolean) => {
    try {
      if (enabled) {
        await enableSkillForUser(skillId);
        message.success('已为您启用此 Skill');
      } else {
        await disableSkillForUser(skillId);
        message.success('已为您禁用此 Skill');
      }
      loadUserEnabledSkills();
    } catch (error) {
      message.error('操作失败');
    }
  };

  // 初始化用户 Skill 状态
  const handleInitializeUserSkills = async () => {
    try {
      await initializeUserSkillStatus();
      message.success('已初始化您的 Skill 状态');
      loadUserEnabledSkills();
    } catch (error) {
      message.error('初始化失败');
    }
  };

  // 从 Zip 导入 Skill
  const handleZipImport = async (file: File) => {
    setUploading(true);
    try {
      const skillId = await importSkillFromZip(file);
      message.success(`Skill 导入成功: ${skillId}`);
      setZipImportModalOpen(false);
      loadSkills();
      loadUserEnabledSkills();
    } catch (error: any) {
      message.error(`导入失败: ${error.message || '未知错误'}`);
    } finally {
      setUploading(false);
    }
    return false; // 阻止默认上传行为
  };

  // 执行 Skill
  const handleExecute = async (values: any) => {
    if (!selectedSkill) return;
    try {
      const result = await executeSkill({
        skillId: selectedSkill.skillId,
        toolName: values.toolName,
        params: values.params ? JSON.parse(values.params) : {},
      });
      if (result.status === 'success') {
        message.success('执行成功');
        Modal.success({
          title: '执行结果',
          content: (
            <div>
              <p><strong>输出:</strong></p>
              <pre style={{ maxHeight: 300, overflow: 'auto' }}>{result.output}</pre>
              <p><strong>耗时:</strong> {result.durationMs}ms</p>
            </div>
          ),
        });
      } else {
        message.error(`执行失败: ${result.error}`);
      }
      setExecuteModalOpen(false);
      executeForm.resetFields();
    } catch (error) {
      message.error('执行失败');
    }
  };

  // 查看详情
  const handleViewDetail = async (skill: SkillDefinition) => {
    setSelectedSkill(skill);
    try {
      const docs = await getSkillDocuments(skill.skillId);
      setSkillDocuments(docs);
    } catch (error) {
      setSkillDocuments([]);
    }
    setDetailModalOpen(true);
  };

  // 打开编辑弹窗
  const openEditModal = (skill: SkillDefinition) => {
    setSelectedSkill(skill);
    editForm.setFieldsValue({
      name: skill.name,
      description: skill.description,
      version: skill.version,
      license: skill.license,
      compatibility: skill.compatibility,
      allowedTools: skill.allowedTools,
    });
    setEditModalOpen(true);
  };

  // 过滤 Skill 列表
  const filteredSkills = skills.filter(
    (skill) =>
      skill.name.toLowerCase().includes(searchText.toLowerCase()) ||
      skill.description.toLowerCase().includes(searchText.toLowerCase()) ||
      skill.skillId.toLowerCase().includes(searchText.toLowerCase())
  );

  const getStatusTag = (status?: number) => {
    if (status === 1) {
      return <Tag color="green">全局启用</Tag>;
    }
    return <Tag color="red">全局禁用</Tag>;
  };

  const getUserStatusTag = (skillId: string) => {
    const isEnabled = enabledSkillIds.includes(skillId);
    if (isEnabled) {
      return <Tag color="blue">已启用</Tag>;
    }
    return <Tag color="default">未启用</Tag>;
  };

  const getTriggerTypeTag = (type: string) => {
    const colors: Record<string, string> = {
      keyword: 'blue',
      regex: 'purple',
      intent: 'orange',
    };
    return <Tag color={colors[type] || 'default'}>{type}</Tag>;
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <button className={styles.backButton} onClick={() => navigate('/')}>
          <ArrowLeft size={20} />
          <span>返回</span>
        </button>
        <div className={styles.logoContainer}>
          <Zap size={36} />
          <ManusLogoTextIcon />
        </div>
      </div>

      <Card className={styles.contentCard}>
        <div className={styles.toolbar}>
          <Input
            className={styles.searchInput}
            placeholder="搜索 Skills..."
            prefix={<Search size={16} />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            allowClear
          />
          <Space>
            <Button icon={<RefreshCw size={16} />} onClick={handleRefreshCache}>
              刷新缓存
            </Button>
            <Button icon={<User size={16} />} onClick={() => setUserStatusModalOpen(true)}>
              我的 Skills
            </Button>
            <Button icon={<FileText size={16} />} onClick={() => setImportModalOpen(true)}>
              导入 SKILL.md
            </Button>
            <Button icon={<UploadIcon size={16} />} onClick={() => setZipImportModalOpen(true)}>
              上传 Zip
            </Button>
            <Button type="primary" icon={<Plus size={16} />} onClick={() => setRegisterModalOpen(true)}>
              注册 Skill
            </Button>
          </Space>
        </div>

        <Divider style={{ margin: '16px 0' }} />

        <Spin spinning={loading}>
          {filteredSkills.length === 0 ? (
            <div className={styles.emptyState}>
              <Empty description="暂无 Skills" />
            </div>
          ) : (
            filteredSkills.map((skill) => (
              <Card
                key={skill.skillId}
                className={styles.skillCard}
                size="small"
                onClick={() => handleViewDetail(skill)}
              >
                <div className={styles.skillHeader}>
                  <span className={styles.skillTitle}>{skill.name}</span>
                  <Space onClick={(e) => e.stopPropagation()}>
                    {getStatusTag(skill.status)}
                    <Tooltip title="执行">
                      <Button
                        type="text"
                        size="small"
                        icon={<Play size={16} />}
                        onClick={(e) => {
                          e.stopPropagation();
                          openExecuteModal(skill);
                        }}
                      />
                    </Tooltip>
                    <Tooltip title="编辑">
                      <Button
                        type="text"
                        size="small"
                        icon={<Edit size={16} />}
                        onClick={(e) => {
                          e.stopPropagation();
                          openEditModal(skill);
                        }}
                      />
                    </Tooltip>
                    <Tooltip title={skill.status === 1 ? '禁用' : '启用'}>
                      <Button
                        type="text"
                        size="small"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleToggleStatus(skill);
                        }}
                      >
                        <Switch size="small" checked={skill.status === 1} />
                      </Button>
                    </Tooltip>
                    <Popconfirm
                      title="确定删除此 Skill?"
                      onConfirm={(e) => {
                        e?.stopPropagation();
                        handleDelete(skill.skillId);
                      }}
                      onCancel={(e) => e?.stopPropagation()}
                    >
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<Trash2 size={16} />}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </Popconfirm>
                  </Space>
                </div>
                <div className={styles.skillMeta}>
                  <Tag>{skill.skillId}</Tag>
                  <Tag color="blue">v{skill.version}</Tag>
                  <Tag color="cyan">{skill.metadata?.author || 'anonymous'}</Tag>
                  {skill.license && <Tag color="geekblue">{skill.license}</Tag>}
                  {getUserStatusTag(skill.skillId)}
                </div>
                <div className={styles.skillDescription}>{skill.description}</div>
              </Card>
            ))
          )}
        </Spin>
      </Card>

      {/* 注册弹窗 */}
      <Modal
        open={registerModalOpen}
        title="注册新 Skill"
        onCancel={() => setRegisterModalOpen(false)}
        footer={null}
        width={700}
      >
        <Form form={registerForm} layout="vertical" onFinish={handleRegister} className={styles.modalForm}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="Skill 名称" />
          </Form.Item>
          <Form.Item name="author" label="作者" rules={[{ required: true, message: '请输入作者' }]}>
            <Input placeholder="作者名称" />
          </Form.Item>
          <Form.Item name="version" label="版本">
            <Input placeholder="1.0.0" />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ required: true, message: '请输入描述' }]}>
            <Input.TextArea rows={3} placeholder="Skill 功能描述" />
          </Form.Item>
          <Form.Item name="category" label="分类">
            <Input placeholder="分类名称" />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <InputNumber min={0} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              注册
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* 导入 SKILL.md 弹窗 */}
      <Modal
        open={importModalOpen}
        title="导入 SKILL.md"
        onCancel={() => setImportModalOpen(false)}
        footer={null}
        width={800}
      >
        <Form form={importForm} layout="vertical" onFinish={handleImport}>
          <Form.Item
            name="content"
            label="SKILL.md 内容"
            rules={[{ required: true, message: '请输入 SKILL.md 内容' }]}
          >
            <Input.TextArea
              rows={15}
              placeholder={`---
name: example-skill
version: 1.0.0
author: your-name
description: An example skill
triggers:
  - type: keyword
    pattern: example
tools:
  - name: run
    description: Run the skill
    executor: shell
    command: echo "Hello"
---
# Example Skill

This is an example skill.`}
              className={styles.codeEditor}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              导入
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑弹窗 */}
      <Modal
        open={editModalOpen}
        title="编辑 Skill"
        onCancel={() => setEditModalOpen(false)}
        footer={null}
        width={700}
      >
        <Form form={editForm} layout="vertical" onFinish={handleEdit} className={styles.modalForm}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="Skill 名称" />
          </Form.Item>
          <Form.Item name="description" label="描述" rules={[{ required: true, message: '请输入描述' }]}>
            <Input.TextArea rows={3} placeholder="Skill 功能描述" />
          </Form.Item>
          <Form.Item name="version" label="版本">
            <Input placeholder="1.0.0" />
          </Form.Item>
          <Form.Item name="license" label="许可证">
            <Input placeholder="例如：MIT, Apache-2.0" />
          </Form.Item>
          <Form.Item name="compatibility" label="兼容性说明">
            <Input.TextArea rows={2} placeholder="环境要求、系统包、网络访问等" />
          </Form.Item>
          <Form.Item name="allowedTools" label="允许使用的工具">
            <Input placeholder="例如：Bash(git:*) Read Write" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block>
              保存
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* 执行弹窗 */}
      <Modal
        open={executeModalOpen}
        title={`执行 Skill: ${selectedSkill?.name}`}
        onCancel={() => setExecuteModalOpen(false)}
        footer={null}
        width={600}
      >
        <Form form={executeForm} layout="vertical" onFinish={handleExecute}>
          <Form.Item name="toolName" label="工具名称">
            <Select placeholder="选择要执行的工具">
              {selectedSkill?.tools?.map((tool) => (
                <Select.Option key={tool.name} value={tool.name}>
                  {tool.name} - {tool.description}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="params" label="参数 (JSON 格式)">
            <Input.TextArea
              rows={5}
              placeholder='{"key": "value"}'
              className={styles.codeEditor}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block icon={<Play size={16} />}>
              执行
            </Button>
          </Form.Item>
        </Form>
      </Modal>

      {/* Zip 上传弹窗 */}
      <Modal
        open={zipImportModalOpen}
        title="从 Zip 文件导入 Skill"
        onCancel={() => setZipImportModalOpen(false)}
        footer={null}
        width={500}
      >
        <div style={{ padding: '20px 0' }}>
          <p style={{ marginBottom: 16, color: '#666' }}>
            上传包含 SKILL.md 的 zip 文件，系统将自动解析并注册 Skill。
          </p>
          <p style={{ marginBottom: 24, color: '#999', fontSize: 12 }}>
            支持的文件结构：
            <br />
            skill-name/
            <br />
            ├── SKILL.md (必需)
            <br />
            ├── reference.md (可选)
            <br />
            ├── examples.md (可选)
            <br />
            └── scripts/ (可选)
          </p>
          <Upload.Dragger
            name="file"
            accept=".zip"
            beforeUpload={handleZipImport}
            showUploadList={false}
            disabled={uploading}
          >
            <p className="ant-upload-drag-icon">
              <UploadIcon size={48} style={{ margin: '0 auto', display: 'block' }} />
            </p>
            <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p className="ant-upload-hint">仅支持 .zip 格式文件</p>
          </Upload.Dragger>
          {uploading && (
            <div style={{ marginTop: 16, textAlign: 'center' }}>
              <Spin tip="正在导入..." />
            </div>
          )}
        </div>
      </Modal>

      {/* 用户状态管理弹窗 */}
      <Modal
        open={userStatusModalOpen}
        title="我的 Skills"
        onCancel={() => setUserStatusModalOpen(false)}
        footer={[
          <Button key="init" onClick={handleInitializeUserSkills}>
            初始化所有 Skill
          </Button>,
          <Button key="close" type="primary" onClick={() => setUserStatusModalOpen(false)}>
            关闭
          </Button>,
        ]}
        width={700}
      >
        <div style={{ marginBottom: 16 }}>
          <p style={{ color: '#666' }}>
            管理您启用的 Skills。只有启用的 Skills 才会在对话中生效。
          </p>
        </div>
        <Table
          dataSource={skills}
          rowKey="skillId"
          pagination={false}
          size="small"
          columns={[
            {
              title: 'Skill 名称',
              dataIndex: 'name',
              key: 'name',
              render: (text: string, record: SkillDefinition) => (
                <div>
                  <div style={{ fontWeight: 500 }}>{text}</div>
                  <div style={{ fontSize: 12, color: '#999' }}>{record.skillId}</div>
                </div>
              ),
            },
            {
              title: '作者',
              key: 'author',
              width: 120,
              render: (_, record: SkillDefinition) => record.metadata?.author || 'anonymous',
            },
            {
              title: '许可证',
              dataIndex: 'license',
              key: 'license',
              width: 100,
              render: (license: string) => license || '-',
            },
            {
              title: '我的状态',
              key: 'userStatus',
              width: 100,
              render: (_, record: SkillDefinition) => {
                const isEnabled = enabledSkillIds.includes(record.skillId);
                return isEnabled ? (
                  <Tag color="blue">已启用</Tag>
                ) : (
                  <Tag color="default">未启用</Tag>
                );
              },
            },
            {
              title: '操作',
              key: 'action',
              width: 100,
              render: (_, record: SkillDefinition) => {
                const isEnabled = enabledSkillIds.includes(record.skillId);
                return (
                  <Switch
                    checked={isEnabled}
                    onChange={(checked) => handleToggleUserStatus(record.skillId, checked)}
                    checkedChildren="启用"
                    unCheckedChildren="禁用"
                  />
                );
              },
            },
          ]}
        />
      </Modal>

      {/* 详情弹窗 */}
      <Modal
        open={detailModalOpen}
        title={`Skill 详情: ${selectedSkill?.name}`}
        onCancel={() => setDetailModalOpen(false)}
        footer={null}
        width={900}
      >
        <Tabs
          items={[
            {
              key: 'basic',
              label: '基本信息',
              icon: <BookOpen size={16} />,
              children: (
                <div>
                  <div className={styles.detailSection}>
                    <div className={styles.detailLabel}>Skill ID</div>
                    <div className={styles.detailContent}>{selectedSkill?.skillId}</div>
                  </div>
                  <div className={styles.detailSection}>
                    <div className={styles.detailLabel}>描述</div>
                    <div className={styles.detailContent}>{selectedSkill?.description}</div>
                  </div>
                  <div className={styles.detailSection}>
                    <div className={styles.detailLabel}>版本 / 作者</div>
                    <div className={styles.detailContent}>
                      v{selectedSkill?.version} by {selectedSkill?.metadata?.author || 'anonymous'}
                    </div>
                  </div>
                  <div className={styles.detailSection}>
                    <div className={styles.detailLabel}>许可证</div>
                    <div className={styles.detailContent}>{selectedSkill?.license || '-'}</div>
                  </div>
                  <div className={styles.detailSection}>
                    <div className={styles.detailLabel}>兼容性说明</div>
                    <div className={styles.detailContent}>{selectedSkill?.compatibility || '-'}</div>
                  </div>
                  <div className={styles.detailSection}>
                    <div className={styles.detailLabel}>允许使用的工具</div>
                    <div className={styles.detailContent}>{selectedSkill?.allowedTools || '-'}</div>
                  </div>
                </div>
              ),
            },
            {
              key: 'documents',
              label: '文档',
              icon: <FileText size={16} />,
              children: (
                <div>
                  {skillDocuments.length > 0 ? (
                    skillDocuments.map((doc) => (
                      <Card key={doc.id} size="small" style={{ marginBottom: 12 }}>
                        <div style={{ fontWeight: 600, marginBottom: 8 }}>
                          <Tag>{doc.docType}</Tag>
                        </div>
                        <pre style={{ maxHeight: 200, overflow: 'auto', fontSize: 12 }}>
                          {doc.content.length > 500 ? doc.content.slice(0, 500) + '...' : doc.content}
                        </pre>
                      </Card>
                    ))
                  ) : (
                    <Empty description="无文档" />
                  )}
                </div>
              ),
            },
          ]}
        />
      </Modal>
    </div>
  );
};

export default SkillsPage;
