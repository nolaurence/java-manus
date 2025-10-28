// src/components/VncViewer.tsx
import React, { useEffect, useRef, FC } from 'react';
// @ts-ignore
import RFB from '@novnc/novnc/lib/rfb';

interface VncViewerProps {
  sessionId: string;
  vncPassword?: string;
  width?: number;
  height?: number;
}

const VncViewer: FC<VncViewerProps> = ({
  sessionId,
  vncPassword = 'defaultpassword',
  width = 1024,
  height = 768,
}) => {
  const vncContainerRef = useRef<HTMLDivElement>(null);
  const rfbRef = useRef<RFB | null>(null);

  useEffect(() => {
    if (!sessionId || !vncContainerRef.current) {
      console.error('Missing sessionId or container ref for VNC');
      return;
    }

    // 自动判断 ws/wss
    let vncHost = '';
      if (window.location.hostname === 'localhost') {
        vncHost = `192.168.49.250:7001`;
      } else {
        vncHost = window.location.host;
      }
    const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const wsUrl = `${wsProtocol}://${vncHost}/vnc?sessionId=${encodeURIComponent(sessionId)}`;

    // 创建 RFB 实例
    const rfb = new RFB({
      target: vncContainerRef.current,
      url: wsUrl,
      credentials: { password: vncPassword },
      viewOnly: false,
      scaleViewport: true,
      resizeSession: true,
    });

    rfbRef.current = rfb;

    // 可选：监听事件（用于调试或 UI 反馈）
    const onConnect = () => console.log('VNC connected');
    const onDisconnect = (e: CustomEvent) => console.log('VNC disconnected', e.detail);
    const onCredentialsRequired = () => console.warn('VNC credentials required');

    rfb.addEventListener('connect', onConnect);
    rfb.addEventListener('disconnect', onDisconnect);
    rfb.addEventListener('credentialsrequired', onCredentialsRequired);

    // 启动连接
    rfb.connect();

    // 清理：组件卸载时断开
    return () => {
      if (rfbRef.current) {
        rfbRef.current.removeEventListener('connect', onConnect);
        rfbRef.current.removeEventListener('disconnect', onDisconnect);
        rfbRef.current.removeEventListener('credentialsrequired', onCredentialsRequired);
        if (rfbRef.current._rfb_state !== 'disconnected') {
          rfbRef.current.disconnect();
        }
        rfbRef.current = null;
      }
    };
  }, [sessionId, vncPassword]);

  return (
    <div
      ref={vncContainerRef}
      style={{
        width: `${width}px`,
        height: `${height}px`,
        border: '1px solid #ccc',
        backgroundColor: '#000',
        position: 'relative',
        overflow: 'hidden',
        borderRadius: '4px',
      }}
    />
  );
};

export default VncViewer;