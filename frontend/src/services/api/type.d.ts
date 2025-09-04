// @ts-ignore
/* eslint-disable */

declare namespace API {
  type Response<T> = {
    success: boolean;
    message?: string;
    data?: T;
  };

  type LoginParams = {
    account?: string;
    password?: string;
  };

  type LoginResult = {
    success?: boolean;
    data?: UserInfo;
    code?: string;
    message?: string;
  };

  type UserInfo = {
    account?: string;
    name?: string;
    avatar?: string;
    userid?: number;
    email?: string;
    signature?: string;
    title?: string;
    group?: string;
    notifyCount?: number;
    unreadCount?: number;
    country?: string;
    access?: string;
    address?: string;
    phone?: string;
    status?: number;
    role: number;
  };
}