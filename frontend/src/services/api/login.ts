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

export async function currentUser(options?: { [key: string]: any }) {
  return request<API.LoginResult>('/user/current', {
    method: 'GET',
    ...(options || {}),
  });
}
