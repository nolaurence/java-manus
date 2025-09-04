import React, {useState} from 'react';
import {Button} from 'antd';

const SidePanel = () => {
  const [isPanelVisible, setIsPanelVisible] = useState(false);
  const [isPinned, setIsPinned] = useState(false);

  return (
    <div className="relative h-screen bg-gray-50">
      {/* 左侧触发区域 - 24px 宽度 */}
      <div
        className="absolute top-0 left-0 w-6 h-screen z-10 cursor-pointer"
        onMouseEnter={() => {
          if (!isPinned) {
            setIsPanelVisible(true);
          }
        }}
      />

      {/* 可折叠/固定面板 */}
      {(isPinned || isPanelVisible) && (
        <div
          className={`absolute top-0 left-0 w-1/6 h-screen bg-white shadow-lg z-20 transition-all duration-200
            ${isPinned ? 'fixed' : 'absolute'} ${isPanelVisible ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
          onMouseLeave={() => {
            if (!isPinned) {
              setIsPanelVisible(false);
            }
          }}
        >
          <div className="p-4 h-full flex flex-col justify-between">
            <div>
              <h2 className="text-lg font-semibold mb-6">功能面板</h2>
              <ul className="space-y-2">
                <li>功能项 1</li>
                <li>功能项 2</li>
                <li>功能项 3</li>
                <li>功能项 4</li>
              </ul>
            </div>
            <Button
              type="primary"
              onClick={() => setIsPinned(!isPinned)}
              className="mt-4"
            >
              {isPinned ? '取消固定' : '固定面板'}
            </Button>
          </div>
        </div>
      )}

      {/* 主要内容区域 */}
      <main
        className="h-screen p-6 overflow-auto transition-all duration-300"
        style={{marginLeft: isPinned ? '16.6667%' : '0'}}
      >
        <h1 className="text-2xl font-bold mb-6">主内容区域</h1>
        <div className="prose max-w-none">
          <p>这里是主内容区域，当面板未固定时，将左侧 24px 区域悬停即可显示功能面板。</p>
          <p>点击面板中的「固定面板」按钮可将面板固定在左侧 1/6 的位置，再次点击可取消固定。</p>
          <p>面板固定后将始终显示，不再随鼠标动作消失。</p>
        </div>
      </main>
    </div>
  );
};

export default SidePanel;