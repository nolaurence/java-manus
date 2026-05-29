import React, { useState, useEffect } from 'react';
import { Modal, Button, Form, Input, Select, ConfigProvider, message, Avatar, Dropdown, Space } from 'antd';
import { SettingOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { login, logout, currentUser, register } from '@/services/api/login';
import { Zap } from 'lucide-react';
import { history } from 'umi';

const UserInfoComponent: React.FC = () => {

  const [loginOpen, setLoginOpen] = useState(false);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [buttonLoading, setButtonLoading] = useState(false);
  const [registerLoading, setRegisterLoading] = useState(false);
  const [isLogedIn, setIsLoggedIn] = useState(false);
  const [userInfo, setUserInfo] = useState<API.UserInfo | null>(null);

  const [loginForm] = Form.useForm();
  const [registerForm] = Form.useForm();

  const handleLogin = async (values: any) => {
    setButtonLoading(true);
    const response = await login({
      account: values.username,
      password: values.password,
    });
    if (response.success && response.data) {
      message.success("登录成功");
      setLoginOpen(false);
      setIsLoggedIn(true);
      setUserInfo(response.data);
      // 发送事件通知其他组件（如 Panel）刷新数据
      window.dispatchEvent(new Event('loginSuccess'));
      loginForm.resetFields();
    } else {
      message.error(response.message || "登录失败");
    }
    setButtonLoading(false);
  };

  const handleRegister = async (values: any) => {
    setRegisterLoading(true);
    const response = await register({
      account: values.username,
      password: values.password,
      checkPassword: values.confirmPassword,
      name: values.nickname,
      inviteCode: values.inviteCode,
      gender: values.gender,
      email: values.email,
      phone: values.phone,
    });
    if (response.success) {
      message.success("注册成功，请登录");
      setRegisterOpen(false);
      setLoginOpen(true);
      // 发送事件通知其他组件（如 Panel）刷新数据
      window.dispatchEvent(new Event('loginSuccess'));
      registerForm.resetFields();
    } else {
      message.error(response.message || "注册失败");
    }
    setRegisterLoading(false);
  };

  const handleLogout = async () => {
    const response = await logout();
    if (response.success) {
      message.success("已退出登录");
      setIsLoggedIn(false);
      setUserInfo(null);
      // 发送事件通知其他组件（如 Panel）刷新数据
      window.dispatchEvent(new Event('loginSuccess'));
    } else {
      message.error("退出失败");
    }
  };

  const handleGoSettings = () => {
    history.push('/settings');
  };

  const handleGoSkills = () => {
    history.push('/skills');
  };

  useEffect(() => {
    const init = async () => {
      const loginInfo = await currentUser();
      if (loginInfo && loginInfo.success && loginInfo.data) {
        setIsLoggedIn(true);
        setUserInfo(loginInfo.data);
      }
    };

    init();
  }, []);

  // 下拉菜单项
  const dropdownItems: MenuProps['items'] = [
    {
      key: 'skill',
      label: '技能',
      icon: <Zap size={16} />,
      onClick: handleGoSkills,
    },
    {
      key: 'settings',
      label: '设置',
      icon: <SettingOutlined />,
      onClick: handleGoSettings,
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      label: '退出登录',
      icon: <LogoutOutlined />,
      onClick: handleLogout,
      danger: true,
    },
  ];

  const buttonTheme = {
    components: {
      Button: {
        defaultBg: "rgb(0, 0, 0)",
        defaultHoverBg: "rgba(0, 0, 0, 0.9)",
        defaultActiveBorderColor: "rgba(0, 0, 0, 0.9)",
        defaultColor: "white",
        defaultHoverColor: "white",
        defaultActiveBg: "rgba(0, 0, 0, 0.9)"
      }
    }
  };

  return (
    <>
      {isLogedIn && userInfo ? (
        <Dropdown
          menu={{ items: dropdownItems }}
          placement="bottomRight"
          trigger={['click']}
        >
          <div className="flex items-center cursor-pointer gap-2 px-2 py-1 rounded hover:bg-gray-100">
            <span className="text-sm">Welcome, {userInfo.name}!</span>
            <Avatar
              src={userInfo?.avatar}
              icon={!userInfo?.avatar && <UserOutlined />}
              style={{ cursor: 'pointer' }}
            />
          </div>
        </Dropdown>
      ) : (
        <ConfigProvider theme={buttonTheme}>
          <Space>
            <Button variant="solid" onClick={() => setLoginOpen(true)}>
              登录
            </Button>
            <Button onClick={() => setRegisterOpen(true)}>注册</Button>
          </Space>
        </ConfigProvider>
      )}

      {/* 登录弹窗 */}
      <ConfigProvider theme={buttonTheme}>
        <Modal
          open={loginOpen}
          title="登录"
          onCancel={() => setLoginOpen(false)}
          footer={null}
        >
          <Form form={loginForm} layout="vertical" onFinish={handleLogin}>
            <Form.Item
              name="username"
              label="账号"
              rules={[{ required: true, message: '请输入账号' }]}
            >
              <Input maxLength={30} placeholder="请输入账号" />
            </Form.Item>
            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input type="password" maxLength={20} placeholder="请输入密码" />
            </Form.Item>
            <Form.Item className="pt-5">
              <Button
                // type="primary"
                loading={buttonLoading}
                size="large"
                block
                htmlType="submit"
              >
                登录
              </Button>
            </Form.Item>
          </Form>
        </Modal>

        {/* 注册弹窗 */}
        <Modal
          open={registerOpen}
          title="注册"
          onCancel={() => setRegisterOpen(false)}
          footer={null}
        >
          <Form form={registerForm} layout="vertical" onFinish={handleRegister}>
            <Form.Item
              name="username"
              label="账号"
              rules={[{ required: true, message: '请输入账号' }]}
            >
              <Input maxLength={30} placeholder="请输入账号" />
            </Form.Item>
            <Form.Item
              name="nickname"
              label="昵称"
              rules={[{ required: true, message: '请输入昵称' }]}
            >
              <Input maxLength={20} placeholder="请输入昵称" />
            </Form.Item>
            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6位' },
              ]}
            >
              <Input.Password maxLength={20} placeholder="请输入密码" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label="确认密码"
              dependencies={['password']}
              rules={[
                { required: true, message: '请确认密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'));
                  },
                }),
              ]}
            >
              <Input.Password maxLength={20} placeholder="请再次输入密码" />
            </Form.Item>
            <Form.Item
              name="inviteCode"
              label="邀请码"
              rules={[{ required: true, message: '请输入邀请码' }]}
            >
              <Input maxLength={20} placeholder="请输入邀请码" />
            </Form.Item>
            <Form.Item
              name="gender"
              label="性别"
            >
              <Select placeholder="请选择性别" allowClear>
                <Select.Option value={1}>男</Select.Option>
                <Select.Option value={2}>女</Select.Option>
              </Select>
            </Form.Item>
            <Form.Item
              name="email"
              label="邮箱"
            >
              <Input maxLength={50} placeholder="请输入邮箱" />
            </Form.Item>
            <Form.Item
              name="phone"
              label="手机号"
            >
              <Input maxLength={20} placeholder="请输入手机号" />
            </Form.Item>
            <Form.Item className="pt-5">
              <Button
                loading={registerLoading}
                size="large"
                block
                htmlType="submit"
              >
                注册
              </Button>
            </Form.Item>
          </Form>
        </Modal>
      </ConfigProvider>
    </>
  );
};

export default UserInfoComponent;
