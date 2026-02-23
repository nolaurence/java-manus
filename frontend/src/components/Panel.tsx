import React, {useState, useEffect} from 'react';
import {useStyles} from '@/assets/panel';
import {PanelLeft, Plus, Search} from 'lucide-react';
import {useNavigate} from 'react-router';
import {currentUser} from '@/services/api/login';
import {fetchUserSessions, type SessionSummary} from '@/services/api/sandbox';


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

  useEffect(() => {
    const loadSessions = async () => {
      try {
        setLoading(true);
        const loginInfo = await currentUser();
        // @ts-ignore
        const user = loginInfo?.data;
        const userId = (user?.userid?.toString?.() || '').toString();
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
  }, []);

  return (
    <div
      className={fixed ? "h-full flex flex-col" : "h-full flex flex-col fixed top-0 start-0 bottom-0 z-[1]"}
      style={{width: fixed ? panelWidth : 24, transition: 'width 0.5s cubic-bezier(0.4, 0, 0.2, 1)'}}
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
          boxShadow: fixed ? undefined : '0px 8px 32px 0px rgba(0,0,0,0.16),0px 0px 0px 1px rgba(0,0,0,0.06)',
          width: (isOpen || fixed) ? panelWidth : '0px',
          transition: 'opacity 0.2s, transform 0.2s, width 0.2s',
          opacity: (isOpen || fixed) ? 1 : 0,
          pointerEvents: (isOpen || fixed) ? 'auto' : 'none',
          transform: (isOpen || fixed) ? 'translateX(0)' : 'translateX(-40px)',
        }}
        onMouseLeave={() => {
          if (!fixed) {
            setIsOpen?.(false);
          }
        }}
      >
        {/* header new */}
        <div className="flex" >
          <div className="flex items-center px-3 py-3 flex-row h-[52px] gap justify-end w-full" >
            <div className="flex justify-between w-full px-1 pt-2">
              <div className="relative flex items-center" >
                {/*加一层hover效果*/}
                <div
                  className="flex h-7 w-7 items-center justify-center cursor-pointer hover:bg-[var(--fill-tsp-gray-main)] rounded-md"
                  onClick={() => setFixed?.(!fixed)}
                >
                  <PanelLeft color={'var(--icon-secondary'} size={24}/>
                </div>
              </div>
              <div className="flex flex-row gap-1" >
                {/* hover效果*/}
                <div className="flex h-7 w-7 items-center justify-center cursor-pointer hover:bg-[var(--fill-tsp-gray-main)] rounded-md">
                  <Search size={24} color={'var(--icon-secondary)'}/>
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
            <Plus size={24} color={'var(--icon-primary)'}/>
            新建任务
          </button>

          {/* Tabs */}
          <div style={{
            display: "flex",
            gap: 6,
            paddingBottom: 8,
          }}>
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
          <div className="px-1">
            {loading && (
              <div className="text-center text-xs text-[var(--text-tertiary)] py-2">加载中...</div>
            )}
            {!loading && sessions.map((s) => (
              <div key={s.sessionId}
                   className="group flex h-14 cursor-pointer items-center gap-2 rounded-[10px] px-2 transition-colors hover:bg-[var(--fill-tsp-gray-main)]"
                   onClick={() => navigate(`/chat/${encodeURIComponent(s.sessionId)}`)}>
                <div className="relative">
                  <div className="h-8 w-8 rounded-full flex items-center justify-center relative bg-[var(--fill-tsp-white-dark)]">
                    <div className="relative overflow-hidden h-4 w-4 object-cover brightness-0 opacity-75 dark:opacity-100 dark:brightness-100">
                      <img alt={s.lastMessage || s.sessionId}
                           className="w-full h-full object-cover"
                           referrerPolicy="no-referrer"
                           src="https://files.manuscdn.com/assets/icon/session/cloud-star.svg"/>
                    </div>
                  </div>
                </div>
                <div className="min-w-20 flex-1">
                  <div className="flex items-center gap-1 overflow-x-hidden">
                    <span className="truncate text-sm font-medium text-[var(--text-primary)] flex-1 min-w-0"
                          title={s.lastMessage || s.sessionId}>
                      {s.lastMessage || s.sessionId}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 h-[18px] relative">
                    <span className="min-w-0 flex-1 truncate text-xs text-[var(--text-tertiary)]"
                          title={s.sessionId}>
                      {s.sessionId}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Footer */}
        <footer className="mt-0 px-3 overflow-x-hidden border-t border-[var(--border-main)]">
          <div className="w-full py-4 gap-4 flex flex-col justify-between items-center">
            <button type="button"
                    className="relative w-full rounded-[12px] border border-[var(--border-light)] clickable hover:opacity-90 text-sm text-[var(--text-primary)] whitespace-nowrap">
              <div
                className="flex w-full items-center justify-between gap-1 px-[12px] py-[8px] bg-[var(--background-menu-white)] rounded-[12px]">
                <div className="flex-1 min-w-0 flex items-center gap-2">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="var(--icon-primary)"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="lucide lucide-hand-heart flex-shrink-0"
                  >
                    <path d="M11 14h2a2 2 0 1 0 0-4h-3c-.6 0-1.1.2-1.4.6L3 16"></path>
                    <path
                      d="m7 20 1.6-1.4c.3-.4.8-.6 1.4-.6h4c1.1 0 2.1-.4 2.8-1.2l4.6-4.4a2 2 0 0 0-2.75-2.91l-4.2 3.9"></path>
                    <path d="m2 15 6 6"></path>
                    <path
                      d="M19.5 8.5c.7-.7 1.5-1.6 1.5-2.7A2.73 2.73 0 0 0 16 4a2.78 2.78 0 0 0-5 1.8c0 1.2.8 2 1.5 2.8L16 12Z"></path>
                  </svg>
                  <div className="flex flex-col text-left w-full overflow-hidden">
                    <span className="text-[var(--text-primary)] font-georgia text-sm leading-[22px] truncate w-full">
                      与好友分享 Manus
                    </span>
                    <span
                      className="text-[var(--text-tertiary)] text-[13px] leading-[18px] truncate w-full">各得 500 积分</span>
                  </div>
                </div>
                <div className="flex items-center gap-1">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="var(--text-primary)"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="lucide lucide-chevron-right"
                  >
                    <path d="m9 18 6-6-6-6"></path>
                  </svg>
                </div>
              </div>
            </button>

            {/* User Info & Icons */}
            <div className="w-full flex justify-between items-center">
              <div className="flex items-center gap-[6px] cursor-pointer flex-1 min-w-0 max-w-fit" aria-expanded="false"
                   aria-haspopup="dialog">
                <div
                  className="relative flex items-center justify-center font-bold flex-shrink-0 rounded-full overflow-hidden"
                  style={{width: '24px', height: '24px'}}>
                  <img
                    className="w-full h-full object-cover overflow-hidden"
                    src="https://lh3.googleusercontent.com/a/ACg8ocLlk4s4LmeoNYI8UxGXQA7Gp67dvd4bhDyxKBzbx_DjRaBaMPA=s96-c"
                    alt="User Avatar"
                  />
                </div>
                <span className="text-sm leading-5 font-medium text-[var(--text-primary)] truncate">Laurence Guo</span>
              </div>

              <div className="flex items-center gap-1">
                <div className="relative">
                  <div
                    className="flex items-center justify-center cursor-pointer hover:bg-[var(--fill-tsp-gray-main)] rounded-md w-8 h-8"
                    id="js-update-notification-button">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="24"
                      height="24"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      className="lucide lucide-bell size-5 text-[var(--icon-secondary)]"
                    >
                      <path d="M10.268 21a2 2 0 0 0 3.464 0"></path>
                      <path
                        d="M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"></path>
                    </svg>
                  </div>
                </div>
                <div
                  className="flex items-center justify-center cursor-pointer hover:bg-[var(--fill-tsp-gray-main)] rounded-md w-8 h-8"
                  aria-expanded="false" aria-haspopup="dialog">
                  <svg viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg"
                       className="size-5 text-[var(--icon-secondary)]">
                    <g id="phone-02">
                      <path
                        id="Backup (Stroke)"
                        fillRule="evenodd"
                        clipRule="evenodd"
                        d="M6.80187 0.833985H13.1994C13.6387 0.83397 14.0177 0.833957 14.3298 0.85946C14.6593 0.886378 14.987 0.945808 15.3023 1.10647C15.7727 1.34615 16.1552 1.7286 16.3948 2.19901C16.5555 2.51433 16.6149 2.84201 16.6418 3.17146C16.6673 3.48361 16.6673 3.86259 16.6673 4.30188V15.6994C16.6673 16.1387 16.6673 16.5177 16.6418 16.8298C16.6149 17.1593 16.5555 17.487 16.3948 17.8023C16.1552 18.2727 15.7727 18.6552 15.3023 18.8948C14.987 19.0555 14.6593 19.1149 14.3298 19.1418C14.0177 19.1673 13.6387 19.1673 13.1995 19.1673H6.80185C6.36256 19.1673 5.9836 19.1673 5.67146 19.1418C5.34201 19.1149 5.01433 19.0555 4.69901 18.8948C4.22861 18.6552 3.84615 18.2727 3.60647 17.8023C3.44581 17.487 3.38638 17.1593 3.35946 16.8298C3.33396 16.5177 3.33397 16.1387 3.33399 15.6994V4.30187C3.33397 3.86258 3.33396 3.48361 3.35946 3.17146C3.38638 2.84201 3.44581 2.51433 3.60647 2.19901C3.84615 1.7286 4.2286 1.34615 4.69901 1.10647C5.01433 0.945808 5.34201 0.886378 5.67146 0.85946C5.98361 0.833957 6.36257 0.83397 6.80187 0.833985ZM5.80718 2.52059C5.5808 2.53909 5.49686 2.57049 5.45566 2.59148C5.29886 2.67137 5.17137 2.79886 5.09148 2.95566C5.07049 2.99686 5.03909 3.0808 5.02059 3.30718C5.0013 3.5433 5.00065 3.85352 5.00065 4.33399V15.6673C5.00065 16.1478 5.0013 16.458 5.02059 16.6941C5.03909 16.9205 5.07049 17.0044 5.09148 17.0456C5.17137 17.2024 5.29886 17.3299 5.45566 17.4098C5.49686 17.4308 5.5808 17.4622 5.80718 17.4807C6.0433 17.5 6.35352 17.5007 6.83399 17.5007H13.1673C13.6478 17.5007 13.958 17.5 14.1941 17.4807C14.4205 17.4622 14.5044 17.4308 14.5456 17.4098C14.7024 17.3299 14.8299 17.2024 14.9098 17.0456C14.9308 17.0044 14.9622 16.9205 14.9807 16.6941C15 16.458 15.0007 16.1478 15.0007 15.6673V4.33399C15.0007 3.85352 15 3.5433 14.9807 3.30718C14.9622 3.0808 14.9308 2.99686 14.9098 2.95566C14.8299 2.79886 14.7024 2.67137 14.5456 2.59148C14.5044 2.57049 14.4205 2.53909 14.1941 2.52059C13.958 2.5013 13.6478 2.50065 13.1673 2.50065H6.83399C6.35352 2.50065 6.0433 2.5013 5.80718 2.52059ZM6.66732 15.834C6.66732 15.3737 7.04042 15.0007 7.50065 15.0007H12.5007C12.9609 15.0007 13.334 15.3737 13.334 15.834C13.334 16.2942 12.9609 16.6673 12.5007 16.6673H7.50065C7.04042 16.6673 6.66732 16.2942 6.66732 15.834Z"
                        fill="currentColor"
                      ></path>
                    </g>
                  </svg>
                </div>
                <div
                  className="flex items-center justify-center cursor-pointer hover:bg-[var(--fill-tsp-gray-main)] rounded-md w-8 h-8">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    viewBox="0 0 20 20"
                    fill="none"
                    className="size-5 text-[var(--icon-secondary)]"
                  >
                    <path
                      d="M13.3574 13.3291C14.6331 12.6198 15.6295 11.5263 16.1923 10.2181C16.7551 8.90992 16.8528 7.46002 16.4704 6.09308C16.0879 4.72614 15.2466 3.51847 14.0768 2.65719C12.907 1.79591 11.474 1.3291 9.9998 1.3291C8.52566 1.3291 7.09266 1.79591 5.92284 2.65719C4.75303 3.51847 3.91171 4.72614 3.52924 6.09308C3.14677 7.46002 3.2445 8.90992 3.8073 10.2181C4.37009 11.5263 5.36653 12.6198 6.64221 13.3291"
                      stroke="currentColor"
                      strokeWidth="1.67"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    ></path>
                    <path
                      d="M11.6998 18.7C10.5696 18.8183 9.43005 18.8183 8.2998 18.7"
                      stroke="currentColor"
                      strokeWidth="1.6"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    ></path>
                    <path
                      d="M12.1998 16.2C10.746 16.4751 9.25357 16.4751 7.7998 16.2"
                      stroke="currentColor"
                      strokeWidth="1.6"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    ></path>
                    <path
                      d="M10.5388 5.5L9.01779 7.195C8.97045 7.24796 8.93807 7.31258 8.92397 7.3822C8.90987 7.45183 8.91457 7.52395 8.93759 7.59116C8.9606 7.65836 9.0011 7.71823 9.05491 7.7646C9.10873 7.81097 9.17392 7.84217 9.24379 7.855L10.7318 8.126C10.8055 8.13937 10.874 8.17318 10.9295 8.22359C10.9849 8.27399 11.0251 8.33896 11.0454 8.41109C11.0658 8.48322 11.0654 8.55961 11.0444 8.63155C11.0235 8.7035 10.9827 8.7681 10.9268 8.818L9.03879 10.5"
                      stroke="currentColor"
                      strokeWidth="1.67"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    ></path>
                  </svg>
                </div>
              </div>
            </div>
          </div>
        </footer>
      </div>
    </div>
  );
};

export default Panel;
