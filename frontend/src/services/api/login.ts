// @ts-ignore
/* eslint-disable */
import request from '@/services/request';

/** 登录接口 POST /api/login/account */
export async function login(body: API.LoginParams, options?: { [key: string]: any }) {
  return request<API.LoginResult>('/user/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}

export async function logout(options?: { [key: string]: any }) {
  return request<API.LoginResult>('/user/logout', {
    method: 'GET',
    ...(options || {}),
  });
}

let _currentUserPromise: Promise<API.LoginResult> | null = null;
let _currentUserTimer: ReturnType<typeof setTimeout> | null = null;

export async function currentUser(options?: { [key: string]: any }) {
  if (!_currentUserPromise) {
    _currentUserPromise = request<API.LoginResult>('/user/current', {
      method: 'GET',
      skipErrorHandler: true,
      ...(options || {}),
    }).then((res) => {
      if (res && !res.success) {
        console.error(res.errorMessage);
      }
      return res;
    }).catch((err) => {
      console.error(err?.message || err);
      return { success: false, errorMessage: err?.message || '请求异常' } as API.LoginResult;
    });
    // 短时间窗口内的多次调用共享同一个 Promise，窗口结束后清除缓存
    _currentUserTimer = setTimeout(() => {
      _currentUserPromise = null;
      _currentUserTimer = null;
    }, 200);
  }
  return _currentUserPromise;
}

/** 注册接口 POST /user/register */
export async function register(body: API.RegisterParams, options?: { [key: string]: any }) {
  return request<API.LoginResult>('/user/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}
