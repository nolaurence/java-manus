import React, {useState, useRef, useEffect, useCallback} from 'react';
import ChatBox from '@/components/ChatBox';
import ChatMessage from '@/components/ChatMessage';
import SimpleBar, {type ScrollableContentRef} from '@/components/SimpleBar';
import ToolPanel from '@/components/ToolPanel';
import {chatWithAgent, fetchSessionMessages, type ConversationMessage} from '@/services/api/sandbox';
import type {Message, MessageContent, ToolContent, StepContent} from '@/types/message';
// @ts-ignore
import {ArrowDown, Bot, Clock, ChevronUp, ChevronDown, PanelLeft} from 'lucide-react';
// import { history } from '@umijs/max';
import {useNavigate, useLocation} from "react-router";
import { useParams } from 'umi';
import StepSuccessIcon from '@/components/icons/StepSuccessIcon';
import type {MessageEventData, StepEventData, ToolEventData, PlanEventData} from '@/types/sseEvent';
// import '@/assets/global.css';
// import '@/assets/theme.css';
import {useStyles} from '@/assets/chatPageStyle';
import Panel from '@/components/Panel';
import { Button, message as antdMessage } from 'antd';
import ScrollableFeed from 'react-scrollable-feed';
import LoginModal from '@/components/LoginModal';
import dayjs from 'dayjs';

const ChatComponent: React.FC = () => {

  const historyPanelWidth = 300;

  const {styles} = useStyles();

  const navigate = useNavigate();

  // 状态管理
  const [inputMessage, setInputMessage] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [messages, setMessages] = useState<Message[]>([]);
  const [title, setTitle] = useState<string>('New Chat');
  const [isShowPlanPanel, setIsShowPlanPanel] = useState<boolean>(false);
  const [plan, setPlan] = useState<PlanEventData | undefined>(undefined);
  const [realTime, setRealTime] = useState<boolean>(true);
  const [follow, setFollow] = useState<boolean>(true);
  const [lastNoMessageTool, setLastNoMessageTool] = useState<ToolContent | undefined>(undefined);

  // const [reasoningContentDelta, setReasoningContentDelta] = useState<string>('Thought:\n');
  // const [stagingReasoningContent, setStagingReasoningContent] = useState<string>('');
  // const [contentDelta, setContentDelta] = useState<string>('');
  const reasoningContentDeltaRef = useRef<string>('**Thought:**\n');
  const stagingReasoningContentRef = useRef<string>('');
  const contentDeltaRef = useRef<string>('');
  // const [agentId, setAgentId] = useState<string>();

  const panelWidth = 300;
  const [panelOpen, setPanelOpen] = useState<boolean>(false);
  const [panelFixed, setPanelFixed] = useState<boolean>(false);
  const [toolPanelShow, setToolPanelShow] = useState<boolean>(false);
  const [toolContent, setToolContent] = useState<ToolContent | undefined>(undefined);

  // Refs
  const simpleBarRef = useRef<ScrollableContentRef>(null);
  // const toolPanelRef = useRef<ToolPanelRef>(null);
  const toolPanelOps = {
    show: (content: ToolContent) => {
      setToolContent(content);
      setToolPanelShow(true);
    },
    hide: () => {
      setToolPanelShow(false);
    },
    isShow: () => toolPanelShow,
  };
  // const navigate = useNavigate();
  // const {search} = useLocation();
  const params = useParams();
  const agentId = params.agentId;

  // 获取最后一步
  const getLastStep = useCallback((): StepContent | undefined => {
    return messages.filter(message => message.type === 'step').pop()?.content as StepContent;
  }, [messages]);

  // 处理滚动事件
  const handleScroll = useCallback(() => {
    const isBottom = simpleBarRef.current?.isScrolledToBottom(10) ?? false;
    setFollow(isBottom);
  }, []);

  // 自动滚动到底部
  useEffect(() => {
    if (follow && simpleBarRef.current) {
      simpleBarRef.current.scrollToBottom();
    }
  }, [messages, follow]);

  const increaseLastMessage = (thoughtDelta: string, localContentDelta: string) => {
    let newContent: string = '';
    if (thoughtDelta) {
      if ("[DONE]" === thoughtDelta) {
        setIsLoading(false);
        stagingReasoningContentRef.current = reasoningContentDeltaRef.current;
        newContent = reasoningContentDeltaRef.current;  // do not append if done;
        reasoningContentDeltaRef.current = '**Thought:** \n';  // added "Thought: \n" in '[START]' signal
        return;  // 收到停止信号，停止追加消息
      } else {
        reasoningContentDeltaRef.current += thoughtDelta;
        newContent = reasoningContentDeltaRef.current;
      }
    }
    if (localContentDelta) {
      if ("[DONE]" === localContentDelta) {
        setIsLoading(false);
        newContent = contentDeltaRef.current;  // do not append if done;
        contentDeltaRef.current = "";
        return;  // 收到停止信号，停止追加消息
      } else {
        if (!contentDeltaRef.current) {
          newContent = `${stagingReasoningContentRef.current}\n**Response:**\n${localContentDelta}`;
          stagingReasoningContentRef.current = "";
        } else {
          newContent = contentDeltaRef.current + localContentDelta;  //append delta
        }
        contentDeltaRef.current = newContent;
      }
    }
    setMessages(prevMsgs => {
      const lastMsg = prevMsgs[prevMsgs.length - 1];
      if (lastMsg && lastMsg.type === 'assistant') {
        // 更新最后一条 assistant 消息
        const updatedMsgs = [...prevMsgs];
        updatedMsgs[updatedMsgs.length - 1] = {
          ...lastMsg,
          content: {
            ...lastMsg.content,
            content: newContent as string,
            timestamp: lastMsg.content.timestamp,
          } as MessageContent,
        };
        return updatedMsgs;
      } else {
        // 新增一条 assistant 消息
        return [
          ...prevMsgs,
          {
            type: 'assistant',
            content: {
              content: newContent as string,
              timestamp: Date.now(),
            } as MessageContent,
          },
        ];
      }
    });
  }

  // 处理消息事件
  const handleMessageEvent = (messageData: MessageEventData) => {
    if (messageData.reasoningContentDelta === '[START]') {
      setMessages(prevMsgs => {
        // 新增一条 assistant 消息
        return [
          ...prevMsgs,
          {
            type: 'assistant',
            content: {
              content: '**Thought:** \n',
              timestamp: Date.now(),
            } as MessageContent,
          },
        ];
      })
    } else {
      increaseLastMessage(messageData.reasoningContentDelta, messageData.contentDelta);
    }
  };

  // 处理工具事件
  const handleToolEvent = (toolData: ToolEventData) => {
    const lastStep = getLastStep();
    if (lastStep?.status === 'running') {
      setMessages(prevMsgs => {
        // 添加到步骤工具列表
        return prevMsgs.map(msg => {
          if (msg.type === 'step' && (msg.content as StepContent).id === lastStep.id) {
            return {
              ...msg,
              content: {
                ...msg.content,
                tools: [...((msg.content as StepContent).tools || []), toolData],
              },
            };
          }
          return msg;
        });
      })
    } else {
      // 新增工具消息
      setMessages(prev => [
        ...prev,
        {
          type: 'tool',
          content: toolData,
        },
      ]);
    }

    // 处理非消息工具
    if (toolData.name !== 'message') {
      setLastNoMessageTool(toolData);
      if (realTime) {
        toolPanelOps.show(toolData);
      }
    }
  };

  // 处理步骤事件
  const handleStepEvent = (stepData: StepEventData) => {
    // const lastStep = getLastStep();
    if (stepData.status === 'running') {
      setMessages(prevMsgs => {
        return [
          ...prevMsgs,
          {
            type: 'step',
            content: {
              ...stepData,
              tools: [],
            } as StepContent,
          }
        ]
      });
    } else if (stepData.status === 'completed') {
      // 找到最后一个 type 为 'step' 的消息，修改其 status 字段
      setMessages(prevMessages => {
        // 找到最后一个 step 类型消息的索引
        const lastStepIndex = prevMessages.findLastIndex(msg => msg.type === 'step');

        // 如果没找到，直接返回原数组
        if (lastStepIndex === -1) return prevMessages;

        // 创建新的消息数组和更新的消息对象
        return prevMessages.map((msg, index) => {
          if (index === lastStepIndex) {
            // 返回新的消息对象，而不是修改原对象
            return {
              ...msg,
              content: {
                ...msg.content,
                status: stepData.status
              } as StepContent
            };
          }
          return msg;
        });
      });
    } else if (stepData.status === 'failed') {
      setIsLoading(false);
    }
  };

  // 处理错误事件
  const handleErrorEvent = (errorData: any) => {
    setIsLoading(false);
    setMessages(prev => [
      ...prev,
      {
        type: 'assistant',
        content: {
          content: errorData.error,
          timestamp: errorData.timestamp,
        },
      },
    ]);
  };

  // 事件处理
  const handleEvent = (event: any) => {
    if (event.event === 'message') {
      handleMessageEvent(event.data);
    } else if (event.event === 'tool') {
      handleToolEvent(event.data);
    } else if (event.event === 'step') {
      handleStepEvent(event.data);
    } else if (event.event === 'done') {
      setIsLoading(false);
    } else if (event.event === 'error') {
      handleErrorEvent(event.data);
    } else if (event.event === 'title') {
      setTitle(event.data.title);
    } else if (event.event === 'plan') {
      setPlan(event.data);
    }
  };

  // 发送消息
  const sendMessage = async (message: string = '') => {
    if (!agentId) return;

    if (message.trim()) {
      setMessages(prev => [
        ...prev,
        {
          type: 'user',
          content: {
            content: message,
            timestamp: Math.floor(Date.now() / 1000),
          },
        },
      ]);
    }
 
    setFollow(true);
    setInputMessage('');
    setIsLoading(true);

    try {
      await chatWithAgent(
        agentId,
        message,
        handleEvent,  // on message
        (error: any) => {
          console.error('Chat error:', error);
          setIsLoading(false);
        },
      );
    } catch (error) {
      console.error('Chat error:', error);
      setIsLoading(false);
    }
  };

  // 初始化：如果带 sessionId，则加载历史；否则按原逻辑
  useEffect(() => {
    const init = async () => {
      if (agentId) {
        try {
          // TODO: 重写下渲染逻辑
          const history: ConversationMessage[] = await fetchSessionMessages(agentId);
          if (history.length === 0) {
            // send first message for new chat
            const msg = localStorage.getItem('firstMessage') || '';
            if (msg) {
              sendMessage(msg);
            } else {
              sendMessage();
            }
            return;
          }
          const mapped: Message[] = history?.map((m) => {
            if (m.eventType === 'MESSAGE') {
              return {
                type: m.messageType === 'USER' ? 'user' : 'assistant',
                content: m.content as MessageContent,
              };
            } else if (m.eventType === 'PLAN') {
              // @ts-ignore
              return {
                type: 'plan',
                content: m.content as PlanEventData
              };
            } else if (m.eventType === 'TOOL') {
              return {
                type: 'tool',
                content: m.content as ToolContent,
              };
            } else if (m.eventType === 'STEP') {
              return {
                type: 'step',
                content: m.content as StepContent,
              };
            }
            return {
              type: 'assistant',
              content: m.content as MessageContent,
            };
          });
          console.log('mapped message',  mapped);
          setMessages(mapped);
          setTitle('History');
          return;
        } catch (e) {
          console.error('load history failed', e);
        }
      }

      const aid = localStorage.getItem('agentId') || '';
      if (aid) {
        const msg = localStorage.getItem('firstMessage') || '';
        if (msg) {
          sendMessage(msg);
        } else {
          sendMessage();
        }
      }
    };
    init();
  }, []);


  // 计划相关计算
  const runningStep = (): string => {
    for (const step of plan?.steps ?? []) {
      if (step.status === 'running') {
        return step.description;
      }
    }
    return 'Confirm Task Completion';
  };

  const planCompleted = (): boolean => {
    return plan?.steps.every(step => step.status === 'completed') ?? false;
  };

  const planProgress = (): string => {
    const completedSteps = plan?.steps.filter(step => step.status === 'completed').length ?? 0;
    return `${completedSteps} / ${plan?.steps.length ?? 1}`;
  };

  // 其他处理函数
  const handleToolClick = (tool: ToolContent) => {
    setRealTime(false);
    if (tool && agentId) {
      toolPanelOps.show(tool);
    }
  };

  const jumpToRealTime = () => {
    setRealTime(true);
    if (lastNoMessageTool) {
      toolPanelOps.show(lastNoMessageTool);
    }
  };

  const handleFollow = () => {
    setFollow(true);
    simpleBarRef.current?.scrollToBottom();
  };

  const handleGoHome = () => {
    navigate("/");
  };

  return (
    <>
      <div
        className="absolute top-0 left-0 w-6 h-screen z-[1]"
        onMouseEnter={() => {
          if (!panelFixed) {
            setPanelOpen(true);
          }
        }}
      >
        <Panel panelWidth={historyPanelWidth} isOpen={panelOpen} fixed={panelFixed} setIsOpen={setPanelOpen} setFixed={setPanelFixed}/>
      </div>

      <div className={`flex h-screen ${panelFixed ? `ml-[${historyPanelWidth}px]` : 'ml-0'} bg-[var(--background-gray-main)]`} >
        <div className={`flex flex-col h-full transition-all duration-300 ${toolPanelShow ? 'w-[calc(100%-768px)]' : 'w-full'}`}>

          {/*header*/}
          <div className="flex items-center justify-center px-6 py-4" >
            <div className="relative flex items-center" >
              { !panelFixed && (
                <Button type="text" onClick={() => setPanelFixed(!panelFixed)} icon={<PanelLeft/>}/>
              )}
              <div className={styles.logoContainer}>
                <div onClick={handleGoHome} className={styles.logoIconContainer}>
                  <Bot className={styles.botIcon} size={24}/>
                </div>
                <div className={styles.logoSeparator}>
                  <span className={styles.logoTitle}>{title}</span>
                </div>
              </div>
            </div>
            <div className="ml-auto flex items-center">
              <LoginModal />
            </div>
          </div>

          {/* message feed */}
          <ScrollableFeed className="mx-auto max-w-full sm:max-w-[768px] sm:min-w-[390px] justify-center flex-grow pb-3">
            {messages.map((message, index) => (
              <ChatMessage key={index} message={message} onToolClick={handleToolClick}/>
            ))}

            {/* 加载指示器 loading indicator */}
            {isLoading && (
              <div className={styles.loadingIndicatorContainer}>
                <span>Thinking</span>
                <span className={styles.animateBounceDotContainer}>
                  <span className={styles.loadingDot} style={{animationDelay: '0ms'}}/>
                  <span className={styles.loadingDot} style={{animationDelay: '200ms'}}/>
                  <span className={styles.loadingDot} style={{animationDelay: '400ms'}}/>
                </span>
              </div>
            )}
          </ScrollableFeed>

          {/* input area*/}
          <div className="mx-auto w-full max-w-full sm:max-w-[768px] sm-min:w-[390px] justify-center mt-auto">
            {/* TODO: extract plan to a single element*/}
            {plan && plan.steps.length > 0 && (
              <>
                {/* 跟随按钮 */}
                {!follow && (
                  <button type="button" onClick={handleFollow}
                          className={styles.followButton}>
                    <ArrowDown className={styles.arrowDown} size={20}/>
                  </button>
                )}

                {/* 计划面板 */}
                {isShowPlanPanel ? (
                  <div className={styles.planPanel}>
                    <div className={styles.planPanelContainer1}>
                      <div className={styles.planPanelContainer2}>
                        <div className={styles.planPanelContainer3}>
                          <div className={styles.planPanelContainer4}>
                            <div
                              onClick={() => setIsShowPlanPanel(false)}
                              className={styles.showPanelButton}
                            >
                              <ChevronDown className={styles.chevronDown}
                                           size={16}/>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div style={{paddingLeft: 16, paddingRight: 16}}>
                        <div className={styles.taskProgressContainer1}>
                          <div className={styles.taskProgressFlexBox}>
                            <span className={styles.taskProgressText}>Task Progress</span>
                            <div className={styles.taskProgressContainer2}>
                              <span className={styles.taskProgressContainer3}>{planProgress()}</span>
                            </div>
                          </div>

                          <div className={styles.taskProgressScrollBox}>
                            {plan.steps.map((step) => (
                              <div key={step.id}
                                   className={styles.stepIconBox}>
                                {step.status === 'completed' ? (
                                  <StepSuccessIcon/>
                                ) : (
                                  <Clock className={styles.clock}
                                         size={16}/>
                                )}

                                <div
                                  className={styles.stepDescriptionContainer}>
                                  <div
                                    className={styles.stepDescription}
                                    title={step.description}>
                                    {step.description}
                                  </div>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div onClick={() => setIsShowPlanPanel(true)}
                       className={styles.anotherPlanPanel}>
                    <div className={styles.anotherPlanPanelBox1}>
                      <div className={styles.anotherPlanPanelBox2}>
                        <div className={styles.anotherPlanPanelBox3}>
                          <div className={styles.anotherPlanPanelBox4}>
                            {planCompleted() ? (
                              <StepSuccessIcon/>
                            ) : (
                              <Clock className={styles.clock} size={16}/>
                            )}

                            <div className={styles.runningStepContainer}>
                              <div className={styles.runningStepText}
                                   title={runningStep()}>
                                {runningStep()}
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <button
                      type="button"
                      className={styles.runningStepButton}
                    >
                      <span className={styles.runningStepProgress}>{planProgress()}</span>
                      <ChevronUp style={{color: 'var(--icon-tertiary)'}} size={16}/>
                    </button>
                  </div>
                )}
              </>
            )}

            <div className="flex flex-col sticky bottom-0" >
              <div className="pb-3" >
                <ChatBox
                  modelValue={inputMessage}
                  onUpdateModelValue={(value) => setInputMessage(value)}
                  onSubmit={() => sendMessage(inputMessage)}
                />
              </div>
            </div>
          </div>
        </div>
        <ToolPanel
          agentId={agentId}
          realTime={realTime}
          onJumpToRealTime={jumpToRealTime}
          isShow={toolPanelShow}
          setIsShow={setToolPanelShow}
          content={toolContent}
        />
      </div>
    </>
  );
};

export default ChatComponent;
