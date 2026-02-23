import React, { useState, useEffect } from 'react';
import { Modal, Button, Form, Input, ConfigProvider, message, Avatar, Dropdown, Space } from 'antd';
import { SettingOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { login, logout, currentUser, register } from '@/services/api/login';

// 固定的邀请码
const VALID_INVITE_CODE = 'MANUS2024';

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
      loginForm.resetFields();
    } else {
      message.error(response.message || "登录失败");
    }
    setButtonLoading(false);
  };

  const handleRegister = async (values: any) => {
    // 验证邀请码
    if (values.inviteCode !== VALID_INVITE_CODE) {
      message.error("邀请码无效");
      return;
    }
    
    setRegisterLoading(true);
    const response = await register({
      account: values.username,
      password: values.password,
      name: values.nickname,
      inviteCode: values.inviteCode,
    });
    if (response.success) {
      message.success("注册成功，请登录");
      setRegisterOpen(false);
      setLoginOpen(true);
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
    } else {
      message.error("退出失败");
    }
  };

  const handleSettings = () => {
    message.info("设置功能开发中");
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
      key: 'settings',
      label: '设置',
      icon: <SettingOutlined />,
      onClick: handleSettings,
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
        <Dropdown menu={{ items: dropdownItems }} placement="bottomRight" trigger={['click']}>
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
            <Button variant="solid" onClick={() => setLoginOpen(true)}>登录</Button>
            <Button onClick={() => setRegisterOpen(true)}>注册</Button>
          </Space>
        </ConfigProvider>
      )}

      {/* 登录弹窗 */}
      <Modal
        open={loginOpen}
        title="登录"
        onCancel={() => setLoginOpen(false)}
        footer={null}
      >
        <Form
          form={loginForm}
          layout='vertical'
          onFinish={handleLogin}
        >
          <Form.Item
            name="username"
            label="账号"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input maxLength={30} placeholder="请输入账号"/>
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input type="password" maxLength={20} placeholder="请输入密码"/>
          </Form.Item>
          <Form.Item className="pt-5">
            <Button loading={buttonLoading} size="large" block htmlType="submit">
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
        <Form
          form={registerForm}
          layout='vertical'
          onFinish={handleRegister}
        >
          <Form.Item
            name="username"
            label="账号"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input maxLength={30} placeholder="请输入账号"/>
          </Form.Item>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称' }]}
          >
            <Input maxLength={20} placeholder="请输入昵称"/>
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, message: '密码至少6位' }
            ]}
          >
            <Input.Password maxLength={20} placeholder="请输入密码"/>
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
            <Input.Password maxLength={20} placeholder="请再次输入密码"/>
          </Form.Item>
          <Form.Item
            name="inviteCode"
            label="邀请码"
            rules={[{ required: true, message: '请输入邀请码' }]}
          >
            <Input maxLength={20} placeholder="请输入邀请码"/>
          </Form.Item>
          <Form.Item className="pt-5">
            <Button loading={registerLoading} size="large" block htmlType="submit">
              注册
            </Button>
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export default UserInfoComponent;
