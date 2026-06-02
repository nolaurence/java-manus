import axios from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { BASE_URL, API_TIMEOUT } from '@/constants/config';

const COOKIE_KEY = 'JSESSIONID';
let sessionCookie = '';

AsyncStorage.getItem(COOKIE_KEY).then((val) => {
  if (val) sessionCookie = val;
});

const client = axios.create({
  baseURL: BASE_URL,
  timeout: API_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
});

client.interceptors.request.use(
  async (config) => {
    if (sessionCookie) {
      config.headers['Cookie'] = `JSESSIONID=${sessionCookie}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

client.interceptors.response.use(
  (response) => {
    const setCookie = response.headers['set-cookie'];
    if (setCookie) {
      const cookies = Array.isArray(setCookie) ? setCookie : [setCookie];
      for (const cookie of cookies) {
        const match = cookie.match(/JSESSIONID=([^;]+)/);
        if (match) {
          sessionCookie = match[1];
          AsyncStorage.setItem(COOKIE_KEY, match[1]);
        }
      }
    }
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
