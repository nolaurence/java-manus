// src/components/VncViewer.tsx
import React, { FC } from 'react';
import { VncScreen } from 'react-vnc';
import { useParams } from 'umi';

interface VncViewerProps {
  sessionId: string;
}

const VncViewer: FC<VncViewerProps> = ({
  sessionId,
}) => {
  // 自动判断 ws/wss
  let vncHost = '';
  if (window.location.hostname === 'localhost') {
    vncHost = `192.168.49.250:7001`;
  } else {
    vncHost = window.location.host;
  }
  const params = useParams();
  const sessionIdFromUrl = String(params.agentId);
  
  const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const wsUrl = `${wsProtocol}://${vncHost}/vnc/${encodeURIComponent(sessionId ? sessionId : sessionIdFromUrl)}`;

  return (
    <VncScreen
      url={wsUrl}
      scaleViewport
      background="#000000"
      style={{
        width: '100%',
        height: '100%',
        border: '1px solid #ccc',
        position: 'relative',
        overflow: 'hidden',
        borderRadius: '4px',
      }}
    />
  );
};

export default VncViewer;