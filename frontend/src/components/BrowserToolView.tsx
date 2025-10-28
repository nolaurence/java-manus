import React, { useEffect, useRef } from 'react';
import type {ToolContent} from '@/types/message';
import { getVNCUrl } from '@/services/api/sandbox';
import { createStyles } from 'antd-style';
import { useParams } from 'umi';
import Hls from 'hls.js';
import { message } from 'antd';
import { startStream, stopStream } from '@/services/api/sandbox';
import { Tabs } from 'antd';
import VncViewer from '@/components/VncViewewr';

interface BrowserToolViewProps {
  agentId: string;
  toolContent: ToolContent
}

const BrowserToolView: React.FC<BrowserToolViewProps> = ({ agentId, toolContent }) => {
  const params = useParams();
  // const agentId = params.agentId;
  const vncContainer = useRef(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [realStreamId, setRealStreamId] = React.useState<string>('');

  useEffect(() => {
    const init = async () => {
      if (!toolContent?.args?.url) {
        return;
      }
      const video = videoRef.current;
      const effectiveStreamId = agentId || params.agentId;

      if (!video) {
        return;
      }
      if (!effectiveStreamId) {
        return;
      }
      setRealStreamId(effectiveStreamId);

      // invoke backend to start stream
      const response = await startStream(effectiveStreamId);
      if (!response) {
        message.error("Failed to start stream");
        return;
      }

      // construct stream url
      let streamUrl = '';
      if (window.location.hostname === 'localhost') {
        streamUrl = `http://192.168.49.250:7001/proxy/stream/${effectiveStreamId}.m3u8`;
      } else {
        streamUrl = window.location.origin + `/proxy/stream/${effectiveStreamId}.m3u8`;
      }

      // Safari support HLS natively
      if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = streamUrl;
      } else if (Hls.isSupported()) {
        const hls = new Hls({
          liveSyncDuration: 5,
          maxLiveSyncPlaybackRate: 1.5,
          lowLatencyMode: true,
        });
        hlsRef.current = hls;
        message.info(`streaming at ${streamUrl}`);
        hls.loadSource(streamUrl);
        hls.attachMedia(video);

        hls.on(Hls.Events.ERROR, (event, data) => {
          console.log('HLS.js error: ', data);
        });
      } else {
        message.error("This browser does not support HLS");
      }
    };

    init();
    // return () => {
    //   hlsRef.current?.destroy();
    //   if (realStreamId) {
    //     stopStream(realStreamId);
    //   } else if (params.agentId) {
    //     stopStream(params.agentId);
    //   }
    // }
  }, [toolContent?.args?.url]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <div className="h-[36px] flex items-center px-3 w-full bg-[var(--background-gray-main)] border-b border-[var(--border-main)] rounded-t-[12px] shadow-[inset_0px_1px_0px_0px_#FFFFFF] dark:shadow-[inset_0px_1px_0px_0px_#FFFFFF30]">
        <div className="flex-1 flex items-center justify-center">
          <div className="max-w-[250px] truncate text-[var(--text-tertiary)] text-sm font-medium text-center">
            {toolContent?.args?.url || 'Browser'}
          </div>
        </div>
      </div>
      <div className="flex-1 min-h-0 w-full overflow-y-auto">
        <div className="px-0 py-0 flex flex-col relative h-full">
            <Tabs defaultActiveKey="1" items={[
                {
                  key: '1',
                  label: 'Stream',
                  children: (
                    <div className="w-full h-full">
                      <video
                        ref={videoRef}
                        style={{ width: '100%', height: 'auto' }}
                        autoPlay={true}
                        controls={true}
                        muted={true}
                      />
                      {/*<div ref={vncContainer} className={styles.vncContainer}></div>*/}
                    </div>
                  ),
                },
                {
                  key: '2',
                  label: 'VNC',
                  children: (
                    <VncViewer
                      sessionId={realStreamId}
                      vncPassword={toolContent?.args?.vncPassword || ''}
                    />
                  ),
                },
              ]} 
            />
        </div>
      </div>
    </div>
  );
};

export default BrowserToolView;
