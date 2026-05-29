import React, {useState, useEffect} from 'react';
import {useStyles} from '@/assets/panel';
import {
  BarChart3,
  BookOpen,
  Bot,
  Bug,
  Calendar,
  ClipboardList,
  Cloud,
  Code2,
  Database,
  Ellipsis,
  FileText,
  Folder,
  Globe,
  Image as ImageIcon,
  Mail,
  MessageSquare,
  Palette,
  PanelLeft,
  Plus,
  Search,
  Settings,
  Shield,
  Sparkles,
  Terminal,
  Video,
  Wrench,
  Zap,
  type LucideIcon,
} from 'lucide-react';
import { history } from 'umi';
import {useNavigate} from 'react-router';
import {currentUser} from '@/services/api/login';
import {fetchUserSessions, type SessionSummary} from '@/services/api/sandbox';

const conversationIcons: Record<string, LucideIcon> = {
  MessageSquare,
  Code2,
  Globe,
  Database,
  FileText,
  Terminal,
  Search,
  Settings,
  Bot,
  Bug,
  Wrench,
  Palette,
  BarChart3,
  Calendar,
  Mail,
  Image: ImageIcon,
  Video,
  Shield,
  Zap,
  BookOpen,
  Cloud,
  Folder,
  ClipboardList,
  Sparkles,
};

interface PanelProps {
  panelWidth?: number;
  isOpen?: boolean;
  setIsOpen?: (isOpen: boolean) => void;
  fixed?: boolean;
  setFixed?: (fixed: boolean) => void;
}

const Panel: React.FC<PanelProps> = ({panelWidth = 300, isOpen = false, setIsOpen, fixed = false, setFixed}) => {
  const {styles} = useStyles();
  const navigate = useNavigate();

  // const [isOpen, setIsOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<'全部' | '收藏' | '已定时'>('全部');
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  // 登录状态标记，用于触发重新加载
  const [loginState, setLoginState] = useState<number>(0);
  // 用户信息 state，用于显示在 Panel 底部
  const [panelUserInfo, setPanelUserInfo] = useState<{name?: string; avatar?: string} | null>(null);

  // 缓存 userId，供轮询时复用
  const [cachedUserId, setCachedUserId] = useState<string>('');

  const refreshSessions = async (userId: string) => {
    if (!userId) return;
    const list = await fetchUserSessions(userId);
    setSessions(list || []);
  };

  useEffect(() => {
    const loadSessions = async () => {
      try {
        setLoading(true);
        const loginInfo = await currentUser();
        // @ts-ignore
        const user = loginInfo?.data;
        // 更新 Panel 底部的用户信息
        if (user) {
          setPanelUserInfo({ name: user.name, avatar: user.avatar });
        } else {
          setPanelUserInfo(null);
        }
        const userId = (user?.userid?.toString?.() || '').toString();
        setCachedUserId(userId);
        if (!userId) {
          setSessions([]);
          return;
        }
        const list = await fetchUserSessions(userId);
        setSessions(list || []);
      } finally {
        setLoading(false);
      }
    };
    loadSessions();
  }, [loginState]); // 当 loginState 变化时重新加载会话列表

  // 当有运行中的会话时，轮询刷新状态
  useEffect(() => {
    const hasRunning = sessions.some(s => s.status && s.status !== 'idle' && s.status !== 'completed');
    if (!hasRunning || !cachedUserId) return;

    const timer = setInterval(() => {
      refreshSessions(cachedUserId);
    }, 5000);

    return () => clearInterval(timer);
  }, [sessions, cachedUserId]);

  // 监听登录事件，更新 loginState 以触发重新加载
  useEffect(() => {
    const handleLoginEvent = () => {
      setLoginState(prev => prev + 1);
    };

    window.addEventListener('loginSuccess', handleLoginEvent);
    return () => {
      window.removeEventListener('loginSuccess', handleLoginEvent);
    };
  }, []);

  return (
    <div
      className={
        fixed
          ? 'h-full flex flex-col'
          : 'h-full flex flex-col fixed top-0 start-0 bottom-0 z-[1]'
      }
      style={{
        width: fixed ? panelWidth : 24,
        transition: 'width 0.5s cubic-bezier(0.4, 0, 0.2, 1)',
      }}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          background: 'var(--background-nav)',
          position: 'fixed',
          top: fixed ? 0 : '4px', // top-1
          left: fixed ? 0 : '4px', // start-1
          bottom: fixed ? 0 : '4px', // bottom-1
          zIndex: 10,
          borderWidth: '1px',
          borderStyle: 'solid',
          borderColor: 'var(--border-main)',
          borderRadius: fixed ? 0 : '12px', // rounded-xl
          boxShadow: fixed
            ? undefined
            : '0px 8px 32px 0px rgba(0,0,0,0.16),0px 0px 0px 1px rgba(0,0,0,0.06)',
          width: isOpen || fixed ? panelWidth : '0px',
          transition: 'opacity 0.2s, transform 0.2s, width 0.2s',
          opacity: isOpen || fixed ? 1 : 0,
          pointerEvents: isOpen || fixed ? 'auto' : 'none',
          transform: isOpen || fixed ? 'translateX(0)' : 'translateX(-40px)',
        }}
        onMouseLeave={() => {
          if (!fixed) {
            setIsOpen?.(false);
          }
        }}
      >
        {/* header new */}
        <div className="flex">
          <div className="flex items-center px-3 py-3 flex-row h-[52px] gap justify-end w-full">
            <div className="flex justify-between w-full px-1 pt-2">
              <div className="relative flex items-center">
                {/*加一层hover效果*/}
                <div
                  className="flex h-7 w-7 items-center justify-center cursor-pointer hover:bg-[var(--fill-tsp-gray-main)] rounded-md"
                  onClick={() => setFixed?.(!fixed)}
                >
                  <PanelLeft color={'var(--icon-secondary'} size={24} />
                </div>
              </div>
              <div className="flex flex-row gap-1">
                {/* hover效果*/}
                <div className="flex h-7 w-7 items-center justify-center cursor-pointer hover:bg-[var(--fill-tsp-gray-main)] rounded-md">
                  <Search size={24} color={'var(--icon-secondary)'} />
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Action Button */}
        <div className={styles.newTaskBox}>
          <button
            className={styles.newTaskButton}
            type="button"
            onClick={() => navigate('/')}
          >
            <Plus size={24} color={'var(--icon-primary)'} />
            新建任务
          </button>

          {/* Tabs */}
          <div
            style={{
              display: 'flex',
              gap: 6,
              paddingBottom: 8,
            }}
          >
            <button
              type="button"
              className={`flex justify-center items-center clickable rounded-[999px] px-[12px] py-[7px] border-none outline-offset-0 outline-[var(--border-dark)] text-[13px] leading-[18px] ${
                activeTab === '全部'
                  ? 'bg-[var(--tab-active-black)] text-[var(--text-onblack)]'
                  : 'bg-transparent border border-[var(--border-dark)] text-[var(--text-tertiary)] hover:bg-[var(--fill-tsp-white-main)]'
              }`}
              onClick={() => setActiveTab('全部')}
            >
              全部
            </button>
            <button
              type="button"
              className={`flex justify-center items-center clickable rounded-[999px] px-[12px] py-[7px] border-none outline outline-1 outline-offset-0 outline-[var(--border-dark)] text-[var(--text-tertiary)] text-[13px] leading-[18px] hover:bg-[var(--fill-tsp-white-main)]`}
              onClick={() => setActiveTab('收藏')}
            >
              收藏
            </button>
            <button
              type="button"
              className={`flex justify-center items-center clickable rounded-[999px] px-[12px] py-[7px] border-none outline outline-1 outline-offset-0 outline-[var(--border-dark)] text-[var(--text-tertiary)] text-[13px] leading-[18px] hover:bg-[var(--fill-tsp-white-main)]`}
              onClick={() => setActiveTab('已定时')}
            >
              已定时
            </button>
          </div>
        </div>

        {/* Chat List */}
        <div className="flex flex-col flex-1 min-h-0 overflow-auto pb-5 overflow-x-hidden hide-scroll-bar">
          <div className="px-2">
            {loading && (
              <div className="text-center text-xs text-[var(--text-tertiary)] py-2">
                加载中...
              </div>
            )}
            {!loading &&
              sessions.map((s) => {
                const ConversationIcon = conversationIcons[s.icon || ''] || MessageSquare;
                return (
                  <div
                    key={s.sessionId}
                    className="flex items-center rounded-[10px] clickable cursor-pointer transition-colors w-full gap-[12px] h-[36px] hover:bg-[var(--fill-tsp-white-light)] pointer-events-auto ps-[9px] pe-[2px] group"
                    onClick={() =>
                      history.push(`/chat/${encodeURIComponent(s.sessionId)}`)
                    }
                  >
                    <div className="flex-shrink-0 flex items-center justify-center w-6 h-6 text-[var(--icon-secondary)]">
                      {s.status && s.status !== 'idle' && s.status !== 'completed' ? (
                        <span className="relative flex h-3 w-3">
                          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75" />
                          <span className="relative inline-flex rounded-full h-3 w-3 bg-green-500" />
                        </span>
                      ) : (
                        <ConversationIcon size={20} />
                      )}
                    </div>
                    <div
                      className="flex-1 min-w-0 flex gap-[4px] items-center text-[14px] text-[var(--text-primary)]"
                      style={{ opacity: 1, width: 'auto' }}
                    >
                      <span
                        className="truncate"
                        title={s.title || s.lastMessage || s.sessionId}
                      >
                        {s.title || s.lastMessage || s.sessionId}
                      </span>
                    </div>
                    <div className="flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                      <div
                        className="flex items-center justify-center w-7 h-7 rounded-md hover:bg-[var(--fill-tsp-white-dark)]"
                        onClick={(e) => {
                          e.stopPropagation();
                        }}
                      >
                        <Ellipsis
                          size={18}
                          className="text-[var(--icon-secondary)]"
                        />
                      </div>
                    </div>
                  </div>
                );
              })}
          </div>
        </div>

        {/* Footer */}
        <footer className="mt-0 px-3 overflow-x-hidden border-t border-[var(--border-main)]">
          <div className="w-full py-4 flex flex-col justify-between items-center">
            {/* User Info */}
            <div className="w-full flex items-center">
              <div
                className="flex items-center gap-[6px] cursor-pointer flex-1 min-w-0 max-w-fit"
                aria-expanded="false"
                aria-haspopup="dialog"
              >
                <div
                  className="relative flex items-center justify-center font-bold flex-shrink-0 rounded-full overflow-hidden"
                  style={{ width: '24px', height: '24px' }}
                >
                  {panelUserInfo?.avatar ? (
                    <img
                      className="w-full h-full object-cover overflow-hidden"
                      src={panelUserInfo.avatar}
                      alt="User Avatar"
                    />
                  ) : (
                    <span className="text-xs font-bold text-white bg-gray-500 w-full h-full flex items-center justify-center">
                      U
                    </span>
                  )}
                </div>
                <span className="text-sm leading-5 font-medium text-[var(--text-primary)] truncate">
                  {panelUserInfo?.name || 'User'}
                </span>
              </div>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
};

export default Panel;
