import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import ChatBox from '@/components/ChatBox';
import { createAgent } from '@/services/api/sandbox';
import { message as antdMessage } from 'antd';
import ManusLogoTextIcon from '@/components/icons/ManusLogoTextIcon';
import {Bot, PanelLeft} from 'lucide-react';
import { createStyles } from 'antd-style';
import Panel from '@/components/Panel';
import UserInfoComponent from "@/components/LoginModal";

const useStyles = createStyles((utils) => {
  const css = utils.css;
  return {
    panelLeftIcon: css`
      margin-top: 5px;
      margin-right: 20px;
      display: flex;
      height: 2rem; /* 8 * 0.25cm = 2cm 或使用像素：height: 32px */
      width: 2rem;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      border-radius: 0.375rem; /* Tailwind 默认 rounded-md 对应值 */

      &:hover {
        background-color: var(--fill-tsp-gray-main);
      }
    `,
    chatBoxRoot: css`
      width: 100%;
      max-width: 100%;
      margin: 180px auto auto;
      animation: home-chat-show 0.5s ease-out forwards;

      /* sm:max-w-[768px] sm:min-w-[390px] */
      @media (min-width: 640px) {
        max-width: 768px;
        min-width: 390px;
      }

      /* max-sm:px-4 */
      @media (max-width: 639px) {
        padding-left: 1rem; /* px-4 */
        padding-right: 1rem;
      }

      @keyframes home-chat-show {
        from {
          opacity: 0;
          transform: translateY(20px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
    `,
    greetingContainer: css`
      width: 100%;
      display: flex;
      padding-left: 1rem; /* pl-4 = 4 * 0.25rem */
      padding-bottom: 1rem; /* pb-4 = 4 * 0.25rem */
      align-items: center;
      justify-content: flex-start;
    `,
    greetingTextSpan: css`
      color: var(--text-primary);
      text-align: left;
      font-family: 'ui-serif', Georgia, Cambria, "Times New Roman", Times, serif;
      font-size: 32px;
      line-height: 40px;
    `,
    chatBoxContainer: css`
      display: flex;
      flex-direction: column;
      width: 100%;
      gap: 0.25rem; /* gap-1 默认对应 0.25rem */
    `,
    chatBoxInner: css`
      display: flex;
      flex-direction: column;
      width: 100%;
      //background-color: var(--background-gray-main);
    `,
    chatBoxHead: css`
      background-color: var(--background-gray-main);
      border-radius: 22px 22px 0 0;

      &:not(:empty) {
        padding-bottom: 0.5rem;
      }
    `,
  };
});

const Home: React.FC = () => {

  const { styles } = useStyles();

  const historyPanelWidth: number = 300;

  const navigate = useNavigate();
  const [message, setMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [panelOpen, setPanelOpen] = useState(false);
  const [panelFixed, setPanelFixed] = useState(false);

  const handleSubmit = async () => {
    if (message.trim() && !isSubmitting) {
      setIsSubmitting(true);

      try {
        const agent = await createAgent();
        console.log("First message: ", message);
        localStorage.setItem('firstMessage', message);
        localStorage.setItem('agentId', agent.agentId);
        navigate(`/chat/${agent.agentId}`, {
          state: { message }
        });
      } catch (error) {
        console.error('Failed to create Agent:', error);
        antdMessage.error('Failed to create agent, please try again later');
        setIsSubmitting(false);
      }
    }
  };

  return (
    <div className="relative h-screen bg-[var(--background-gray-main)]" >
      <div
        className="absolute top-0 left-0 w-6 h-screen z-10"
        onMouseEnter={() => {
          if (!panelFixed) {
            setPanelOpen(true);
          }
        }}
      >
        <Panel panelWidth={historyPanelWidth} isOpen={panelOpen} fixed={panelFixed} setIsOpen={setPanelOpen} setFixed={setPanelFixed} />
      </div>
      <div
        className={`container mx-auto flex min-h-screen w-full max-w-full flex-col justify-center gap-2 bg-[var(--background-gray-main)] px-5`}
        style={{ paddingLeft: panelFixed ? historyPanelWidth + 20 : 20 }}
      >
        <div className="flex mt-4 ml-5 items-center justify-between w-full" style={{ marginLeft: panelFixed ? historyPanelWidth : 0 }}>
          <div className="flex items-center space-x-4">
            { !panelFixed && (
              <div onClick={() => setPanelFixed(!panelFixed)} className={styles.panelLeftIcon}>
                <PanelLeft size={24} />
              </div>
            )}
            <Bot size={36}/>
            <ManusLogoTextIcon />
          </div>
          <div className="ml-auto">
            <UserInfoComponent />
          </div>
        </div>

        <div className={styles.chatBoxRoot}>
          <div className={styles.greetingContainer}>
          <span className={styles.greetingTextSpan}>
            你好,<br />
            <span style={{ color: 'var(--icon-tertiary)' }}>
              我能为你做什么？
            </span>
          </span>
          </div>

          <div className={styles.chatBoxContainer}>
            <div className={styles.chatBoxInner}>
              <div className={styles.chatBoxHead} />
              <ChatBox
                rows={2}
                modelValue={message}
                onUpdateModelValue={(e) => setMessage(e)}
                onSubmit={handleSubmit}
                disabled={isSubmitting}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Home;
