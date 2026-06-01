import client from './client';
import { ApiResponse, LoginParams, LoginResult, RegisterParams, UserInfo } from '@/types/api';

export async function login(params: LoginParams): Promise<ApiResponse<LoginResult>> {
  return client.post('/user/login', params) as Promise<ApiResponse<LoginResult>>;
}

export async function logout(): Promise<ApiResponse<LoginResult>> {
  return client.get('/user/logout') as Promise<ApiResponse<LoginResult>>;
}

export async function currentUser(): Promise<ApiResponse<UserInfo>> {
  return client.get('/user/current', {
    params: { skipErrorHandler: true },
  }) as Promise<ApiResponse<UserInfo>>;
}

export async function register(params: RegisterParams): Promise<ApiResponse<LoginResult>> {
  return client.post('/user/register', params) as Promise<ApiResponse<LoginResult>>;
}
