import React, { useEffect, useRef } from 'react';
import type {ToolContent} from '@/types/message';
import { getVNCUrl } from '@/services/api/sandbox';
import { createStyles } from 'antd-style';
import { useParams } from 'umi';
import Hls from 'hls.js';
import { message } from 'antd';
import { startStream, stopStream } from '@/services/api/sandbox';

interface BrowserToolViewProps {
  agentId: string;
  toolContent: ToolContent
}

// @ts-ignore
const useStyles = createStyles((utils) => {
  const css = utils.css;
  return {
   header: css`
     height: 36px;
     display: flex;
     align-items: center;
     padding: 0 12px;
     width: 100%;
     background: var(--background-gray-main);
     border-bottom: 1px solid var(--border-main);
     border-top-left-radius: 12px;
     border-top-right-radius: 12px;
     box-shadow: inset 0 1px 0 0 #FFFFFF;
   `,
   headerDark: css`
     flex: 1;
     display: flex;
     align-items: center;
     justify-content: center;
   `,
   headerContent: css`
     flex: 1;
     display: flex;
     align-items: center;
     justify-content: center;
   `,
   title: css`
     max-width: 250px;
     overflow: hidden;
     text-overflow: ellipsis;
     white-space: nowrap;
     color: var(--text-tertiary);
     font-size: 14px;
     font-weight: 500;
     text-align: center;
   `,
   main: css`
     flex: 1px;
     min-height: 0;
     width: 100%;
     overflow-y: auto;
   `,
   container: css`
     padding: 0;
     display: flex;
     flex-direction: column;
     position: relative;
     height: 100%;
   `,
   vncWrapper: css`
     width: 100%;
     height: 100%;
     object-fit: cover;
     display: flex;
     align-items: center;
     justify-content: center;
     background: var(--fill-white);
     position: relative;
   `,
   vncInner: css`
     width: 100%;
     height: 100%;
   `,
   vncContainer: css`
     display: flex;
     width: 100%;
     height: 100%;
     overflow: auto;
     background: rgb(40, 40, 40);
   `,
  };
})

const BrowserToolView: React.FC<BrowserToolViewProps> = ({ agentId, toolContent }) => {
  const params = useParams();
  // const agentId = params.agentId;
  const { styles } = useStyles();
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
      <div className={styles.header}>
        <div className={styles.headerContent}>
          <div className={styles.title}>
            {toolContent?.args?.url || 'Browser'}
          </div>
        </div>
      </div>
      <div className={styles.main}>
        <div className={styles.container}>
          <div className={styles.vncWrapper}>
            <div className={styles.vncInner}>
              <video
                ref={videoRef}
                style={{ width: '100%', height: 'auto' }}
                autoPlay={true}
                controls={true}
                muted={true}
              />
              {/*<div ref={vncContainer} className={styles.vncContainer}></div>*/}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default BrowserToolView;
