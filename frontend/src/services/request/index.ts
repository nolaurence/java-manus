import type {RequestOptions} from '@/services/request/type';
import {message, notification} from 'antd'; // 假设你使用了 Ant Design
import axios from 'axios';

enum ErrorShowType {
  SILENT = 0,
  WARN_MESSAGE = 1,
  ERROR_MESSAGE = 2,
  NOTIFICATION = 3,
  REDIRECT = 9,
}

// 创建一个映射，用来保存枚举成员名和对应的枚举值
const ErrorShowTypeMap: { [key: string]: ErrorShowType } = {
  SILENT: ErrorShowType.SILENT,
  WARN_MESSAGE: ErrorShowType.WARN_MESSAGE,
  ERROR_MESSAGE: ErrorShowType.ERROR_MESSAGE,
  NOTIFICATION: ErrorShowType.NOTIFICATION,
  REDIRECT: ErrorShowType.REDIRECT,
};

// 从环境变量中读取配置
// const API_BASE_URL = window.location.hostname === 'localhost' ? "https://manus.proxy.nolaurence.cn" : window.location.origin;
const API_BASE_URL = window.location.hostname === 'localhost' ? "http://192.168.49.247:7001" : window.location.origin;
const ALLOW_CORS = window.location.hostname === 'localhost';

// 创建 axios 实例
const axiosInstance = axios.create({
  baseURL: API_BASE_URL, // 你的 API 基础 URL
  withCredentials: ALLOW_CORS, // 是否允许跨域携带 cookie
  timeout: 10000, // 请求超时时间
});

// 请求拦截器
axiosInstance.interceptors.request.use(
  (config) => {
    // 可以在这里添加请求头或进行其他配置
    // config.headers['Authorization'] = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error),
);

// 响应拦截器
axiosInstance.interceptors.response.use((response) => {
  // 从config中获取skipErrorHandler
  const {data, config} = response;
  // @ts-ignore
  if (config.skipErrorHandler) {
    return data;
  }
  if (data.success) {
    return data;
  } else {
    // 根据data.errorShowType 判断如何处理错误
    let errorShowType = ErrorShowType.WARN_MESSAGE;
    if (data.errorShowType in ErrorShowTypeMap) {
      errorShowType = ErrorShowTypeMap[data.errorShowType];
    }
    switch (errorShowType) {
      case ErrorShowType.SILENT:
        // do nothing
        break;
      case ErrorShowType.WARN_MESSAGE:
        message.warning(`后端报错: ${data.errorMessage}`);
        break;
      case ErrorShowType.ERROR_MESSAGE:
        message.error(`后端报错: ${data.errorMessage}`);
        break;
      case ErrorShowType.NOTIFICATION:
        notification.error({
          description: `后端报错: ${data.errorMessage}`,
          message: data.errorCode,
        });
        break;
      case ErrorShowType.REDIRECT:
        console.log("redirect 目前不支持");
        break;
    }
  }
});

async function request<T = any>(url: string, options: RequestOptions): Promise<T> {
  const {method, ...restOptions} = options;

  // 调用 axios 实例
  const response = await axiosInstance({
    url,
    method,
    ...restOptions,
  });
  return response as T; // 返回的响应已经处理为后端返回的 data
}

export default request;
