import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { currentUser, login as loginApi, logout as logoutApi } from '@/api/auth';
import { UserInfo, LoginParams } from '@/types/api';

interface AuthContextType {
  user: UserInfo | null;
  isLoading: boolean;
  isLoggedIn: boolean;
  login: (params: LoginParams) => Promise<boolean>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = async () => {
    try {
      const res = await currentUser();
      if (res.success && res.data) {
        setUser(res.data);
      } else {
        setUser(null);
      }
    } catch (e) {
      setUser(null);
    }
  };

  const login = async (params: LoginParams): Promise<boolean> => {
    const res = await loginApi(params);
    if (res.success && res.data) {
      await refreshUser();
      return true;
    }
    return false;
  };

  const logout = async () => {
    try {
      await logoutApi();
    } catch (e) {
      // ignore
    }
    setUser(null);
  };

  useEffect(() => {
    refreshUser().finally(() => setIsLoading(false));
  }, []);

  return (
    <AuthContext.Provider value={{
      user,
      isLoading,
      isLoggedIn: !!user,
      login,
      logout,
      refreshUser,
    }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};
