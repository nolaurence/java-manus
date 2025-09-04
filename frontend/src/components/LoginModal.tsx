import React, { useState, useEffect } from 'react';
import { Modal, Button, Form, Input, ConfigProvider, message, Avatar } from 'antd';
import { login, logout, currentUser } from '@/services/api/login';

const UserInfoComponent: React.FC = () => {

  const [open, setOpen] = useState(false);
  const [buttonLoading, setButtonLoading] = useState(false);
  const [isLogedIn, setIsLoggedIn] = useState(false);
  const [userInfo, setUserInfo] = useState<API.UserInfo | null>(null);

  const handleLogin = async (values) => {
    setButtonLoading(true);
    const response = await login({
      account: values.username,
      password: values.password,
    });
    if (response.success && response.data) {
      message.success("登录成功");
      setOpen(false);
    } else {
      message.error("登录失败");
    }
    setButtonLoading(false);
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

  return (
    <>
      {isLogedIn && userInfo ? (
        <div>
          <p>Welcome, {userInfo.name}!</p>
          <Avatar src={userInfo?.avatar} />
          {/* <Button onClick={logout}>Logout</Button> */}
        </div>
      ) : null}
      { !isLogedIn && (
        <ConfigProvider theme={{
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
        }}>
          <Button  variant="solid" onClick={() => setOpen(true)} >login</Button>
          <Modal
            open={open}
            title="login"
            onCancel={() => setOpen(false)}
            footer={null}
          >
            <Form
              layout='vertical'
              onFinish={handleLogin}
            >
              <Form.Item
                name="username"
                label="Account"
              >
                <Input maxLength={30}/>
              </Form.Item>
              <Form.Item
                name="password"
                label="Password"
              >
                <Input type="password" maxLength={20} />
              </Form.Item>
              <Form.Item
                className="pt-5"
              >
                <Button loading={buttonLoading} size="large" block htmlType="submit">
                  Login
                </Button>
              </Form.Item>
            </Form>
          </Modal>
        </ConfigProvider>
      )}
    </>
  );
};

export default UserInfoComponent;
