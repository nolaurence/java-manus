import React, { useState, useEffect } from 'react';
import { Button, Input, Card, Form, message, Space, Divider } from 'antd';
import { useNavigate } from 'react-router';
import { Bot, ArrowLeft, Save, RotateCcw } from 'lucide-react';
import ManusLogoTextIcon from '@/components/icons/ManusLogoTextIcon';
import { createStyles } from 'antd-style';
import { getLlmConfig, updateLlmConfig } from '@/services/api/settings';

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
      max-width: 800px;
      margin: 0 auto 30px;
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
      max-width: 800px;
      margin: 0 auto;
      border-radius: 12px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    `,
    formGroup: css`
      margin-bottom: 24px;
    `,
    label: css`
      display: block;
      margin-bottom: 8px;
      font-weight: 500;
      color: var(--text-primary);
    `,
    input: css`
      width: 100%;
      padding: 8px 12px;
      border-radius: 6px;
      border: 1px solid #d9d9d9;
      font-size: 14px;
      
      &:focus {
        border-color: #1890ff;
        outline: none;
      }
    `,
    buttonGroup: css`
      display: flex;
      gap: 12px;
      justify-content: flex-end;
      margin-top: 24px;
    `,
    sectionTitle: css`
      font-size: 18px;
      font-weight: 600;
      margin-bottom: 16px;
      color: var(--text-primary);
    `,
    description: css`
      color: var(--text-secondary);
      font-size: 12px;
      margin-top: 4px;
    `,
  };
});

const SettingsPage: React.FC = () => {
  const { styles } = useStyles();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(false);

  const [form] = Form.useForm();

  // 加载当前配置
  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {
    try {
      const config = await getLlmConfig();
      form.setFieldsValue({
        endpoint: config.data?.endpoint || '',
        apiKey: config.data?.apiKey || '',
        modelName: config.data?.modelName || '',
      });
    } catch (error) {
      console.error('加载配置失败:', error);
      message.error('加载配置失败');
    }
  };

  const handleSave = async (values: {
    endpoint: string;
    apiKey: string;
    modelName: string;
  }) => {
    if (!values.endpoint.trim()) {
      message.warning('请输入模型端点地址');
      return;
    }
    if (!values.apiKey.trim()) {
      message.warning('请输入 API Key');
      return;
    }
    if (!values.modelName.trim()) {
      message.warning('请输入模型名称');
      return;
    }

    setLoading(true);
    try {
      await updateLlmConfig({
        endpoint: values.endpoint.trim(),
        apiKey: values.apiKey.trim(),
        modelName: values.modelName.trim(),
      });
      message.success('保存成功');
    } catch (error) {
      console.error('保存配置失败:', error);
      message.error('保存配置失败');
    } finally {
      setLoading(false);
    }
  };

  const handleBack = () => {
    navigate('/');
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <button className={styles.backButton} onClick={handleBack}>
          <ArrowLeft size={20} />
          <span>返回</span>
        </button>
        <div className={styles.logoContainer}>
          <Bot size={36} />
          <ManusLogoTextIcon />
        </div>
      </div>

      <Card className={styles.contentCard}>
        <div className={styles.sectionTitle}>大模型配置</div>
        <Divider style={{ margin: '16px 0' }} />
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item
            name="endpoint"
            label="模型端点地址"
            tooltip="大模型服务的端点 URL,通常以 /v1 结尾"
          >
            <Input
              placeholder="例如: http://192.168.49.241:8080/v1"
            />
          </Form.Item>
          <Form.Item
            name="apiKey"
            label="API Key"
            tooltip="用于访问大模型服务的认证密钥"
          >
            <Input.Password
              placeholder="请输入您的 API Key"
            />
          </Form.Item>
          <Form.Item
            name="modelName"
            label="模型名称"
            tooltip="要使用的具体模型名称"
          >
            <Input
              placeholder="例如: Qwen3-Next-80B-A3B-Instruct-int4g-fp16-mixed"
            />
          </Form.Item>
          <div className={styles.buttonGroup}>
            <Button
              type="primary"
              icon={<Save size={16} />}
              loading={loading}
              htmlType="submit"
            >
              保存配置
            </Button>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default SettingsPage;
