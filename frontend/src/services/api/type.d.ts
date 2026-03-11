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

  type RegisterParams = {
    account?: string;
    password?: string;
<<<<<<< HEAD
    
=======
    checkPassword?: string;
>>>>>>> dfab66c48db9c8b4b987502702a92e7cf551ce88
    name?: string;
    inviteCode?: string;
    gender?: number;
    email?: string;
    phone?: string;
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