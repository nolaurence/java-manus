import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { BASE_URL, API_TIMEOUT } from '@/constants/config';

const client = axios.create({
  baseURL: BASE_URL,
  timeout: API_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
client.interceptors.request.use(
  async (config) => {
    // 可以在这里添加认证token等
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
client.interceptors.response.use(
  (response) => {
    return response.data;
  },
  (error) => {
    if (error.response) {
      return Promise.reject(new Error(error.response.data?.message || '请求失败'));
    }
    if (error.request) {
      return Promise.reject(new Error('网络请求失败，请检查网络连接'));
    }
    return Promise.reject(new Error(error.message || '未知错误'));
  }
);

export default client;
