import { useState, useCallback, memo } from "react";
import GeneralInput from "@/components/GeneralInput";
import Slogn from "@/components/Slogn";
import ChatView from "@/components/ChatView";
import { productList, defaultProduct } from "@/utils/constants";
import { Image, Layout } from "antd";
import { demoList } from "@/utils/constants";
import ProtectedRoute from "@/components/Auth/ProtectedRoute";
import GlobalSidebar from "@/components/GlobalSidebar";
import UserProfile from "@/components/UserProfile/UserProfile";
import KnowledgeBaseDrawer from "@/components/KnowledgeBase/KnowledgeBaseDrawer";

const { Content, Header } = Layout;

type HomeProps = Record<string, never>;

const Home: GenieType.FC<HomeProps> = memo(() => {
  const [inputInfo, setInputInfo] = useState<CHAT.TInputInfo>({
    message: "",
    deepThink: false,
  });
  const [product, setProduct] = useState(defaultProduct);
  const [videoModalOpen, setVideoModalOpen] = useState();
  const [selectedSessionId, setSelectedSessionId] = useState<string | undefined>(undefined);
  const [hasStartedChat, setHasStartedChat] = useState(false);
  const [chatViewTitleUpdater, setChatViewTitleUpdater] = useState<((sessionId: string, newTitle: string) => void) | null>(null);

  const changeInputInfo = useCallback((info: CHAT.TInputInfo) => {
    setInputInfo(info);
    if (info.message.length > 0) {
      setHasStartedChat(true);
    }
  }, []);

  const handleSessionSelect = useCallback((sessionId: string) => {
    setSelectedSessionId(sessionId);
    setInputInfo({ message: "", deepThink: false });
    setHasStartedChat(true);
  }, []);

  const handleNewSession = useCallback(() => {
    setSelectedSessionId(undefined);
    setInputInfo({ message: "", deepThink: false });
    setHasStartedChat(false);
  }, []);

  const handleSessionTitleUpdate = useCallback((sessionId: string, newTitle: string) => {
    if (chatViewTitleUpdater) {
      chatViewTitleUpdater(sessionId, newTitle);
    }
  }, [chatViewTitleUpdater]);

  const handleChatViewTitleUpdaterReady = useCallback((updater: (sessionId: string, newTitle: string) => void) => {
    setChatViewTitleUpdater(() => updater);
  }, []);

  const CaseCard = ({ title, description, tag, image, url, videoUrl }: any) => {
    return (
      <div className="group flex flex-col rounded-lg bg-white pt-16 px-16 shadow-[0_4px_12px_rgba(0,0,0,0.05)] hover:shadow-[0_8px_20px_rgba(0,0,0,0.1)] hover:-translate-y-[5px] transition-all duration-300 ease-in-out cursor-pointer w-full max-w-xs border border-[rgba(233,233,240,1)]">
        <div className="mb-4 flex items-center justify-between">
          <div className="text-[14px] font-bold truncate">{title}</div>
          <div className="shrink-0 inline-block bg-gray-100 text-gray-600 px-[6px] leading-[20px] text-[12px] rounded-[4px]">
            {tag}
          </div>
        </div>
        <div className="text-[12px] text-[#71717a] h-40 line-clamp-2 leading-[20px]">
          {description}
        </div>
        <div
          className="text-[#4040ff] group-hover:text-[#656cff] text-[12px] flex items-center mb-6 cursor-pointer transition-colors duration-200"
          onClick={() => window.open(url)}
        >
          <span className="mr-1">查看报告</span>
          <i className="font_family icon-xinjianjiantou"></i>
        </div>
        <div className="relative rounded-t-[10px] overflow-hidden h-100 group-hover:scale-105 transition-transform duration-500 ease">
          <Image
            style={{ display: "none" }}
            preview={{
              visible: videoModalOpen === videoUrl,
              destroyOnHidden: true,
              imageRender: () => (
                <video muted width="80%" controls autoPlay src={videoUrl} />
              ),
              toolbarRender: () => null,
              onVisibleChange: () => {
                setVideoModalOpen(undefined);
              },
            }}
            src={image}
          />
          <img
            src={image}
            className="w-full h-full rounded-t-[10px] mt-[-20px]"
          ></img>
          <div
            className="absolute inset-0 flex items-center justify-center cursor-pointer rounded-t-[10px] group hover:bg-[rgba(0,0,0,0.6)] border border-[#ededed]"
            onClick={() => setVideoModalOpen(videoUrl)}
          >
            <i className="font_family icon-bofang hidden group-hover:block text-[#fff] text-[24px]"></i>
          </div>
        </div>
      </div>
    );
  };

  const renderContent = () => {
    // Show ChatView if user has started chatting OR selected a session
    if (!hasStartedChat && !selectedSessionId) {
      return (
        <div className="pt-[120px] flex flex-col items-center">
          <Slogn />
          
          <div className="w-640 rounded-xl shadow-[0_18px_39px_0_rgba(198,202,240,0.1)]">
            <GeneralInput
              placeholder={product.placeholder}
              showBtn={true}
              size="big"
              disabled={false}
              product={product}
              send={changeInputInfo}
            />
          </div>
          <div className="w-640 flex flex-wrap gap-16 mt-[16px]">
            {productList.map((item, i) => (
              <div
                key={i}
                className={`w-[22%] h-[36px] cursor-pointer flex items-center justify-center border rounded-[8px] ${item.type === product.type ? "border-[#4040ff] bg-[rgba(64,64,255,0.02)] text-[#4040ff]" : "border-[#E9E9F0] text-[#666]"}`}
                onClick={() => setProduct(item)}
              >
                <i className={`font_family ${item.img} ${item.color}`}></i>
                <div className="ml-[6px]">{item.name}</div>
              </div>
            ))}
          </div>
          <div className="mt-80 mb-120">
            <div className="text-center">
              <h2 className="text-2xl font-bold mb-2">优秀案例</h2>
              <p className="text-gray-500">和 上海银行超级智能体 一起提升工作效率</p>
            </div>
            <div className="flex gap-16 mt-24">
              {demoList.map((demo, i) => (
                <CaseCard key={i} {...demo} />
              ))}
            </div>
          </div>
        </div>
      );
    }
    return <ChatView inputInfo={inputInfo} product={product} selectedSessionId={selectedSessionId} onSessionTitleUpdate={handleChatViewTitleUpdaterReady} />;
  };

  return (
    <ProtectedRoute>
      <Layout style={{ height: '100vh' }}>
        {/* Header with user profile */}
        <Header style={{
          background: '#fff',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '0 24px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <GlobalSidebar
              onSessionSelect={handleSessionSelect}
              onNewSession={handleNewSession}
              selectedSessionId={selectedSessionId}
              onSessionTitleUpdate={handleSessionTitleUpdate}
            />
            <KnowledgeBaseDrawer />
          </div>
          <UserProfile />
        </Header>

        {/* Main Content */}
        <Content style={{ background: '#fff' }}>
          <div className="h-full flex flex-col items-center">
            {renderContent()}
          </div>
        </Content>
      </Layout>
    </ProtectedRoute>
  );
});

Home.displayName = "Home";

export default Home;
