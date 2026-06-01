// 开发环境使用本地IP，生产环境使用当前域名
// 请根据实际部署环境修改此配置
export const BASE_URL = __DEV__ 
  ? 'http://192.168.49.247:7001'  // 开发环境后端地址
  : 'https://your-production-domain.com';  // 生产环境地址

export const API_TIMEOUT = 30000;
