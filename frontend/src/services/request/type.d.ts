// request 方法 opts 参数的接口
import type { AxiosRequestConfig } from 'axios';

export interface RequestOptions extends AxiosRequestConfig {
  skipErrorHandler?: boolean;
  requestInterceptors?: IRequestInterceptorTuple[];
  responseInterceptors?: IResponseInterceptorTuple[];
  [key: string]: any;
}
