import bankLogo from '@/assets/icon/logo.png';

const Slogn: GenieType.FC = () => {
  return (
    <div className='mb-54 flex flex-col items-center'>
      <div className='flex items-center space-x-6'>
        {/* 上海银行官方Logo */}
        <div className='flex items-center'>
          <img
            src={bankLogo}
            alt="上海银行"
            className='w-auto object-contain'
            style={{
              height: '48px',
              width: 'auto'
            }}
          />
        </div>

        {/* 超级智能体 - 单行 */}
        <div
          className='text-5xl font-bold text-transparent bg-clip-text leading-none'
          style={{backgroundImage: 'linear-gradient(270deg, rgba(130,45,255,1) 0%,rgba(62,69,255,1) 20.88266611099243%,rgba(60,196,250,1) 100%)'}}
        >超级智能体</div>
      </div>
      <div className='mt-6 text-lg text-gray-500 font-light'>
        智能助手，为您提供专业服务
      </div>
    </div>
  );
};

export default Slogn;
