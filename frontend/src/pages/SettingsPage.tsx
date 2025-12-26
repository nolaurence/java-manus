import React, { useState, useEffect } from 'react';
import { Button, Input, Card, message, Space, Divider } from 'antd';
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

  const [endpoint, setEndpoint] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [modelName, setModelName] = useState('');
  const [loading, setLoading] = useState(false);
  const [initialConfig, setInitialConfig] = useState({
    endpoint: '',
    apiKey: '',
    modelName: '',
  });

  // 加载当前配置
  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {
    try {
      const config = await getLlmConfig();
      setEndpoint(config.endpoint || '');
      setApiKey(config.apiKey || '');
      setModelName(config.modelName || '');
      setInitialConfig({
        endpoint: config.endpoint || '',
        apiKey: config.apiKey || '',
        modelName: config.modelName || '',
      });
    } catch (error) {
      console.error('加载配置失败:', error);
      message.error('加载配置失败');
    }
  };

  const handleSave = async () => {
    if (!endpoint.trim()) {
      message.warning('请输入模型端点地址');
      return;
    }
    if (!apiKey.trim()) {
      message.warning('请输入 API Key');
      return;
    }
    if (!modelName.trim()) {
      message.warning('请输入模型名称');
      return;
    }

    setLoading(true);
    try {
      await updateLlmConfig({
        endpoint: endpoint.trim(),
        apiKey: apiKey.trim(),
        modelName: modelName.trim(),
      });
      message.success('保存成功');
      setInitialConfig({
        endpoint: endpoint.trim(),
        apiKey: apiKey.trim(),
        modelName: modelName.trim(),
      });
    } catch (error) {
      console.error('保存配置失败:', error);
      message.error('保存配置失败');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setEndpoint(initialConfig.endpoint);
    setApiKey(initialConfig.apiKey);
    setModelName(initialConfig.modelName);
    message.info('已恢复到上次保存的配置');
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

        <div className={styles.formGroup}>
          <label className={styles.label}>模型端点地址</label>
          <Input
            value={endpoint}
            onChange={(e) => setEndpoint(e.target.value)}
            placeholder="例如: http://192.168.49.241:8080/v1"
            size="large"
          />
          <div className={styles.description}>
            大模型服务的端点 URL,通常以 /v1 结尾
          </div>
        </div>

        <div className={styles.formGroup}>
          <label className={styles.label}>API Key</label>
          <Input.Password
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="请输入您的 API Key"
            size="large"
          />
          <div className={styles.description}>
            用于访问大模型服务的认证密钥
          </div>
        </div>

        <div className={styles.formGroup}>
          <label className={styles.label}>模型名称</label>
          <Input
            value={modelName}
            onChange={(e) => setModelName(e.target.value)}
            placeholder="例如: Qwen3-Next-80B-A3B-Instruct-int4g-fp16-mixed"
            size="large"
          />
          <div className={styles.description}>
            要使用的具体模型名称
          </div>
        </div>

        <div className={styles.buttonGroup}>
          <Button
            icon={<RotateCcw size={16} />}
            onClick={handleReset}
          >
            重置
          </Button>
          <Button
            type="primary"
            icon={<Save size={16} />}
            onClick={handleSave}
            loading={loading}
          >
            保存配置
          </Button>
        </div>
      </Card>
    </div>
  );
};

export default SettingsPage;
