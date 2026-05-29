import React, { useState } from 'react';
import { Link, Outlet } from 'umi';
import './index.less';
import '@/assets/theme.css';
import '../../tailwind.css';

export default function Layout() {
  return (
    <div style={{ height: '100%' }}>
      <Outlet />
    </div>
  );
}
