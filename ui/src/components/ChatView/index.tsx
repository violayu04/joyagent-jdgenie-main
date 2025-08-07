import { useEffect, useState, useRef, useImperativeHandle, forwardRef } from "react";
import { getUniqId, scrollToTop, ActionViewItemEnum } from "@/utils";
import querySSE from "@/utils/querySSE";
import {  handleTaskData, combineData } from "@/utils/chat";
import { saveUserMessage } from "@/utils/chatApi";
import Dialogue from "@/components/Dialogue";
import GeneralInput from "@/components/GeneralInput";
import ActionView from "@/components/ActionView";
import { RESULT_TYPES } from '@/utils/constants';
import { useMemoizedFn } from "ahooks";
import classNames from "classnames";
import Logo from "../Logo";
import { Modal } from "antd";

type Props = {
  inputInfo: CHAT.TInputInfo;
  product?: CHAT.Product;
  selectedSessionId?: string;
  onSessionTitleUpdate?: (updateFn: (sessionId: string, newTitle: string) => void) => void;
};

const ChatView: GenieType.FC<Props> = (props) => {
  const { inputInfo: inputInfoProp, product, selectedSessionId, onSessionTitleUpdate } = props;

  console.log('ChatView rendered with selectedSessionId:', selectedSessionId);

  const [chatTitle, setChatTitle] = useState("");
  const [taskList, setTaskList] = useState<MESSAGE.Task[]>([]);
  const chatList = useRef<CHAT.ChatItem[]>([]);
  const [activeTask, setActiveTask] = useState<CHAT.Task>();
  const [plan, setPlan] = useState<CHAT.Plan>();
  const [showAction, setShowAction] = useState(false);
  const [loading, setLoading] = useState(false);
  const chatRef = useRef<HTMLInputElement>(null);
  const actionViewRef = ActionView.useActionView();
  const [sessionId, setSessionId] = useState<string | undefined>(undefined);
  const [modal, contextHolder] = Modal.useModal();

  // Load existing chat data when selectedSessionId changes
  useEffect(() => {
    console.log('ChatView useEffect triggered:', { selectedSessionId, sessionId });
    if (selectedSessionId && selectedSessionId !== sessionId) {
      console.log('Loading existing session:', selectedSessionId);
      loadExistingSession(selectedSessionId);
    } else if (!selectedSessionId && sessionId && selectedSessionId !== undefined) {
      // Only clear if we explicitly switched to a new session (selectedSessionId was set to undefined)
      // Don't clear if we're just starting up or continuing in new session mode
      console.log('Clearing chat data for new session');
      clearChatData();
    }
  }, [selectedSessionId]);

  // Method to update session title
  const updateSessionTitle = (sessionId: string, newTitle: string) => {
    if (sessionId === selectedSessionId) {
      console.log('Updating chat title to:', newTitle);
      setChatTitle(newTitle);
    }
  };

  // Expose the update method to parent
  useEffect(() => {
    if (onSessionTitleUpdate) {
      onSessionTitleUpdate(updateSessionTitle);
    }
  }, [onSessionTitleUpdate, selectedSessionId]);

  const clearChatData = () => {
    chatList.current = [];
    setChatTitle("");
    setSessionId(undefined);
    setLoading(false);
    setShowAction(false);
    setTaskList([]);
    setPlan(undefined);
    setActiveTask(undefined);
  };

  const loadExistingSession = async (sessionIdToLoad: string) => {
    try {
      console.log('Starting to load existing session:', sessionIdToLoad);
      setLoading(true);

      // 获取会话信息和消息
      const token = localStorage.getItem('genie_token');
      console.log('Using token for session load:', token ? 'Token exists' : 'No token');

      // 首先获取会话信息（包含正确的标题）
      const sessionResponse = await fetch(`/api/sessions/${sessionIdToLoad}`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      let sessionTitle = '';
      if (sessionResponse.ok) {
        const sessionInfo = await sessionResponse.json();
        sessionTitle = sessionInfo.title;
        console.log('Found session title:', sessionTitle);
      }

      // 获取会话消息
      const response = await fetch(`/api/sessions/${sessionIdToLoad}/messages`, {
        headers: {
          'Authorization': `Bearer ${token}`,
        },
      });

      console.log('Session messages response status:', response.status);
      if (response.ok) {
        const messages = await response.json();
        console.log('Loaded messages from API:', messages);
        setSessionId(sessionIdToLoad);

        // 设置正确的会话标题
        if (sessionTitle) {
          setChatTitle(sessionTitle);
        } else if (messages.length > 0 && messages[0].role === 'user') {
          // 如果没有获取到会话标题，使用第一条用户消息作为备选
          setChatTitle(messages[0].content);
        }

        // 将消息转换为聊天项目
        const chatItems: CHAT.ChatItem[] = [];
        let currentUserMessage = '';
        let messageIndex = 0;

        for (const message of messages) {
          console.log('Processing message:', message);
          if (message.role === 'user') {
            // 如果有之前的用户消息但没有助手回复，先创建一个只有用户消息的聊天项
            if (currentUserMessage) {
              const chatItem: CHAT.ChatItem = {
                query: currentUserMessage,
                response: "",
                responseType: "txt",
                sessionId: sessionIdToLoad,
                requestId: `loaded-user-${messageIndex}`,
                loading: false,
                forceStop: false,
                tip: "",
                files: [],
                multiAgent: { tasks: [] },
                tasks: []
              };
              chatItems.push(chatItem);
            }

            currentUserMessage = message.content;
          } else if (message.role === 'assistant' && currentUserMessage) {
            const chatItem: CHAT.ChatItem = {
              query: currentUserMessage,
              response: message.content,
              responseType: "txt",
              sessionId: sessionIdToLoad,
              requestId: `loaded-${message.id}`,
              loading: false,
              forceStop: false,
              tip: "",
              files: [],
              multiAgent: { tasks: [] },
              tasks: []
            };
            chatItems.push(chatItem);
            currentUserMessage = '';
          }
          messageIndex++;
        }

        // 处理最后一条用户消息（如果没有对应的助手回复）
        if (currentUserMessage) {
          const chatItem: CHAT.ChatItem = {
            query: currentUserMessage,
            response: "",
            responseType: "txt",
            sessionId: sessionIdToLoad,
            requestId: `loaded-final-${messageIndex}`,
            loading: false,
            forceStop: false,
            tip: "",
            files: [],
            multiAgent: { tasks: [] },
            tasks: []
          };
          chatItems.push(chatItem);
        }

        console.log('Created chat items:', chatItems);
        chatList.current = chatItems;
      } else {
        console.error('Failed to load session messages');
        Modal.error({
          title: '加载失败',
          content: '无法加载会话历史消息',
        });
      }
    } catch (error) {
      console.error('Error loading session:', error);
      Modal.error({
        title: '加载失败',
        content: '网络错误，无法加载会话历史',
      });
    } finally {
      setLoading(false);
    }
  };

  const combineCurrentChat = (
    inputInfo: CHAT.TInputInfo,
    sessionId: string,
    requestId: string
  ): CHAT.ChatItem => {
    return {
      query: inputInfo.message!,
      files: inputInfo.files!,
      responseType: "txt",
      sessionId,
      requestId,
      loading: true,
      forceStop: false,
      tasks: [],
      thought: "",
      response: "",
      taskStatus: 0,
      tip: "已接收到你的任务，将立即开始处理...",
      multiAgent: {tasks: []},
    };
  };

  const sendMessage = useMemoizedFn(async (inputInfo: CHAT.TInputInfo) => {
    const {message, deepThink, outputStyle, files} = inputInfo;
    const requestId = getUniqId();
    
    // 如果没有指定outputStyle，使用product的type作为默认值
    const finalOutputStyle = outputStyle || product?.type;
    
    // Prepare document context for LLM
    let contextualQuery = message!;
    if (files && files.length > 0) {
      const documentsContext = files
        .filter(f => f.analysis?.success)
        .map(f => {
          const analysis = f.analysis!;
          return `文档《${analysis.metadata.filename}》内容分析：\n${analysis.analysis}\n`;
        })
        .join('\n');
      
      if (documentsContext) {
        contextualQuery = `基于以下文档内容回答问题：\n\n${documentsContext}\n\n用户问题：${message}`;
      }
    }
    
    // 1. 首先创建聊天UI对象并显示
    let currentChat = combineCurrentChat(inputInfo, sessionId || '', requestId);
    chatList.current =  [...chatList.current, currentChat];
    if (!chatTitle) {
      setChatTitle(message!);
    }
    setLoading(true);
    
    // 2. 立即保存用户消息 - 解耦流程的关键步骤
    // 优先使用selectedSessionId（如果用户选择了现有会话），否则使用当前sessionId
    let actualSessionId = selectedSessionId || sessionId;
    console.log('Using sessionId for message:', { selectedSessionId, sessionId, actualSessionId });

    try {
      const saveResponse = await saveUserMessage(message!, actualSessionId, deepThink, finalOutputStyle);
      if (saveResponse.success) {
        actualSessionId = saveResponse.sessionId; // 使用返回的会话ID（可能是新创建的）
        console.log('User message saved immediately:', saveResponse);
        
        // 更新组件的 sessionId 状态
        setSessionId(actualSessionId);
        
        // 更新聊天对象的sessionId
        currentChat.sessionId = actualSessionId;
        const newChatList = [...chatList.current];
        newChatList.splice(newChatList.length - 1, 1, currentChat);
        chatList.current = newChatList;
      } else {
        throw new Error(saveResponse.error || 'Failed to save user message');
      }
    } catch (error) {
      console.error('Failed to save user message:', error);
      // 更新UI显示错误状态，但保留用户消息在界面上
      currentChat.loading = false;
      currentChat.tip = "消息保存失败，请稍后重试";
      setLoading(false);
      
      const newChatList = [...chatList.current];
      newChatList.splice(newChatList.length - 1, 1, currentChat);
      chatList.current = newChatList;
      
      // 提供更详细的错误信息
      const errorMessage = error instanceof Error ? error.message : '未知错误';
      console.error('Save user message error details:', {
        error: errorMessage,
        token: localStorage.getItem('genie_token') ? 'Present' : 'Missing',
        message: message,
        sessionId: actualSessionId
      });
      
      Modal.error({
        title: '消息保存失败',
        content: `无法保存您的消息到会话历史：${errorMessage}`,
      });
      return;
    }
    
    // 3. 调用AI处理（与消息保存解耦）
    const params = {
      sessionId: actualSessionId, // 使用实际的会话ID
      requestId: requestId,
      query: contextualQuery, // Send contextual query to LLM
      deepThink: deepThink ? 1 : 0,
      outputStyle: finalOutputStyle
    };
    const handleMessage = (data: MESSAGE.Answer) => {
      const { finished, resultMap, packageType, status } = data;
      if (status === "tokenUseUp") {
        modal.info({
          title: '您的试用次数已用尽',
          content: '如需额外申请，请联系 liyang.1236@jd.com',
        });
        const taskData = handleTaskData(
          currentChat,
          deepThink,
          currentChat.multiAgent
        );
        currentChat.loading = false;
        setLoading(false);

        setTaskList(taskData.taskList);
        return;
      }
      if (packageType !== "heartbeat") {
        requestAnimationFrame(() => {
          if (resultMap?.eventData) {
            currentChat = combineData(resultMap.eventData || {}, currentChat);
            const taskData = handleTaskData(
              currentChat,
              deepThink,
              currentChat.multiAgent
            );
            setTaskList(taskData.taskList);
            updatePlan(taskData.plan!);
            openAction(taskData.taskList);
            if (finished) {
              currentChat.loading = false;
              setLoading(false);
            }
            const newChatList = [...chatList.current];
            newChatList.splice(newChatList.length - 1, 1, currentChat);
            chatList.current = newChatList;
          }
        });
        scrollToTop(chatRef.current!);
      }
    };

    const openAction = (taskList:MESSAGE.Task[]) =>{
      if (taskList.filter((t)=>!RESULT_TYPES.includes(t.messageType)).length) {
        setShowAction(true);
      }
    };

    const handleError = (error: unknown) => {
      console.error('AI processing error:', error);
      // 用户消息已经保存，只需要更新UI状态
      currentChat.loading = false;
      currentChat.tip = "AI 处理失败，但您的消息已保存到会话历史中";
      setLoading(false);
      
      const newChatList = [...chatList.current];
      newChatList.splice(newChatList.length - 1, 1, currentChat);
      chatList.current = newChatList;
    };

    const handleClose = () => {
      console.log('🚀 ~ close');
    };

    // 获取JWT token用于SSE认证
    const token = localStorage.getItem('genie_token');
    const authHeaders = token ? { 'Authorization': `Bearer ${token}` } : {};

    querySSE({
      body: params,
      handleMessage,
      handleError,
      handleClose,
      headers: authHeaders,
    });
  });

  const changeTask = (task: CHAT.Task) => {
    actionViewRef.current?.changeActionView(ActionViewItemEnum.follow);
    changeActionStatus(true);
    setActiveTask(task);
  };

  const updatePlan = (plan: CHAT.Plan) => {
    setPlan(plan);
  };

  const changeFile = (file: CHAT.TFile) => {
    changeActionStatus(true);
    actionViewRef.current?.setFilePreview(file);
  };

  const changePlan = () => {
    changeActionStatus(true);
    actionViewRef.current?.openPlanView();
  };

  const changeActionStatus = (status: boolean) => {
    setShowAction(status);
  };

  useEffect(() => {
    if (inputInfoProp.message?.length !== 0) {
      sendMessage(inputInfoProp).catch(error => {
        console.error('Send message failed:', error);
      });
    }
  }, [inputInfoProp, sendMessage]);

  return (
    <div className="h-full w-full flex justify-center">
      <div
        className={classNames("p-24 flex flex-col flex-1 w-0", { 'max-w-[1200px]': !showAction })}
        id="chat-view"
      >
        <div className="w-full flex justify-between">
          <div className="w-full flex items-center pb-8">
            <Logo />
            {inputInfoProp.deepThink && <div className="rounded-[4px] px-6 border-1 border-solid border-gray-300 flex items-center shrink-0">
              <i className="font_family icon-shendusikao mr-6 text-[12px]"></i>
              <span className="ml-[-4px]">深度研究</span>
            </div>}
          </div>
        </div>
        <div
          className="w-full flex-1 overflow-auto no-scrollbar mb-[36px]"
          ref={chatRef}
        >
          {chatList.current.map((chat) => {
            return <div key={chat.requestId}>
              <Dialogue
                chat={chat}
                deepThink={inputInfoProp.deepThink}
                changeTask={changeTask}
                changeFile={changeFile}
                changePlan={changePlan}
              />
            </div>;
          })}
        </div>
        <GeneralInput
          placeholder={loading ? "任务进行中" : "希望上海银行超级智能体为你做哪些任务呢？"}
          showBtn={false}
          size="medium"
          disabled={loading}
          product={product}
          // 多轮问答也不支持切换deepThink，使用传进来的
          send={(info) => {
            sendMessage({
              ...info,
              deepThink: inputInfoProp.deepThink
            }).catch(error => {
              console.error('Send message failed:', error);
            });
          }}
        />
      </div>
      {contextHolder}
      <div className={classNames('transition-all w-0', {
        'opacity-0 overflow-hidden': !showAction,
        'flex-1': showAction,
      })}>
        <ActionView
          activeTask={activeTask}
          taskList={taskList}
          plan={plan}
          ref={actionViewRef}
          onClose={() => changeActionStatus(false)}
        />
      </div>
    </div>
  );
};

export default ChatView;
