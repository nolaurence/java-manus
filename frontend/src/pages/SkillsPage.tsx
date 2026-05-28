import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'umi';
import { Card, Switch, Button, Upload, Typography, Tag, message as antdMessage, Spin, Empty, Divider } from 'antd';
import { UploadOutlined, ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { Wrench } from 'lucide-react';
import { listSkills, installSkill, toggleSkill, Skill } from '@/services/api/skill';
import { createStyles } from 'antd-style';

const { Title, Text, Paragraph } = Typography;

const useStyles = createStyles(({ css }) => ({
  container: css`
    min-height: 100vh;
    background-color: var(--background-gray-main);
    padding: 24px;
  `,
  header: css`
    display: flex;
    align-items: center;
    gap: 16px;
    margin-bottom: 24px;
  `,
  skillCard: css`
    margin-bottom: 16px;
    border-radius: 12px;
    transition: all 0.3s ease;
    &:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }
  `,
  skillHeader: css`
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 12px;
  `,
  skillTitle: css`
    display: flex;
    align-items: center;
    gap: 12px;
  `,
  skillMeta: css`
    display: flex;
    gap: 8px;
    align-items: center;
    margin-top: 8px;
  `,
  uploadArea: css`
    margin-bottom: 24px;
  `,
}));

const SkillsPage: React.FC = () => {
  const { styles } = useStyles();
  const navigate = useNavigate();
  const [skills, setSkills] = useState<Skill[]>([]);
  const [loading, setLoading] = useState(true);
  const [installing, setInstalling] = useState(false);

  const userId = localStorage.getItem('userId') || 'anonymous';

  const loadSkills = useCallback(async () => {
    try {
      setLoading(true);
      const data = await listSkills(userId);
      setSkills(data);
    } catch (error) {
      antdMessage.error('Failed to load skills');
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    loadSkills();
  }, [loadSkills]);

  const handleToggle = async (skillId: string, enabled: boolean) => {
    try {
      await toggleSkill(userId, skillId, enabled);
      antdMessage.success(`Skill ${enabled ? 'enabled' : 'disabled'}`);
      setSkills(prev =>
        prev.map(s => (s.id === skillId ? { ...s, enabled } : s))
      );
    } catch (error) {
      antdMessage.error('Failed to toggle skill');
    }
  };

  const handleUpload = async (file: File) => {
    if (!file.name.endsWith('.zip')) {
      antdMessage.error('Only .zip files are supported');
      return false;
    }

    try {
      setInstalling(true);
      const base64 = await fileToBase64(file);
      await installSkill(userId, file.name, base64);
      antdMessage.success('Skill installed successfully');
      await loadSkills();
    } catch (error: any) {
      antdMessage.error(error.message || 'Failed to install skill');
    } finally {
      setInstalling(false);
    }
    return false;
  };

  const fileToBase64 = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.readAsDataURL(file);
      reader.onload = () => {
        const base64 = (reader.result as string).split(',')[1];
        resolve(base64);
      };
      reader.onerror = reject;
    });
  };

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/')}>
          Back
        </Button>
        <Title level={3} style={{ margin: 0, display: 'flex', alignItems: 'center', gap: 12 }}>
          <Wrench size={24} />
          Skill Management
        </Title>
      </div>

      <Card className={styles.uploadArea}>
        <Upload beforeUpload={handleUpload} showUploadList={false} accept=".zip">
          <Button icon={<UploadOutlined />} loading={installing} type="primary">
            <PlusOutlined /> Install Skill from ZIP
          </Button>
        </Upload>
        <Text type="secondary" style={{ marginLeft: 12 }}>
          Upload a skill package (.zip) containing SKILL.md
        </Text>
      </Card>

      <Divider />

      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      ) : skills.length === 0 ? (
        <Empty description="No skills installed yet" style={{ marginTop: 48 }}>
          <Upload beforeUpload={handleUpload} showUploadList={false} accept=".zip">
            <Button icon={<PlusOutlined />} type="primary">
              Install Your First Skill
            </Button>
          </Upload>
        </Empty>
      ) : (
        <div>
          {skills.map(skill => (
            <Card key={skill.id} className={styles.skillCard}>
              <div className={styles.skillHeader}>
                <div className={styles.skillTitle}>
                  <Title level={5} style={{ margin: 0 }}>
                    {skill.name}
                  </Title>
                  {skill.version && <Tag color="blue">v{skill.version}</Tag>}
                </div>
                <Switch
                  checked={skill.enabled}
                  onChange={checked => handleToggle(skill.id, checked)}
                  checkedChildren="ON"
                  unCheckedChildren="OFF"
                />
              </div>
              {skill.description && (
                <Paragraph type="secondary">{skill.description}</Paragraph>
              )}
              <div className={styles.skillMeta}>
                <Text type="secondary" code>
                  {skill.id}
                </Text>
                {skill.tags?.map(tag => (
                  <Tag key={tag} color="green">
                    {tag}
                  </Tag>
                ))}
              </div>
              {skill.installedAt && (
                <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>
                  Installed: {new Date(skill.installedAt).toLocaleDateString()}
                </Text>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};

export default SkillsPage;
