import React, { useRef, forwardRef, useImperativeHandle } from 'react';
import { createStyles } from 'antd-style';

// 定义组件props类型
interface ScrollableContentProps {
  children: React.ReactNode;
  onScroll?: (event: React.UIEvent<HTMLDivElement>) => void;
}

// TODO: 样式治理
// 创建样式
// @ts-ignore
const useStyles = createStyles(({ token, css }) => ({
  scrollContainer: css`
    flex: 1 1 0%;
    min-width: 0;
    min-height: 0;

    & .simplebar-scrollable-y {
      --simplebar-scrollbar-width: 6px;

      &[data-simplebar] {
        position: relative;
        flex-direction: column;
        flex-wrap: wrap;
        justify-content: flex-start;
        align-content: flex-start;
        align-items: flex-start;
      }
    }

    & .simplebar-wrapper {
      overflow: hidden;
      width: inherit;
      height: inherit;
      max-width: inherit;
      max-height: inherit;
    }

    & .simplebar-mask {
      direction: inherit;
      overflow: hidden;
      width: auto !important;
      height: auto !important;
      z-index: 0;
      position: absolute;
      padding: 0;
      margin: 0;
      left: 0;
      top: 0;
      bottom: 0;
      right: 0;
    }

    & .simplebar-offset {
      direction: inherit !important;
      box-sizing: inherit !important;
      resize: none !important;
      -webkit-overflow-scrolling: touch;
      position: absolute;
      padding: 0;
      margin: 0;
      left: 0;
      top: 0;
      bottom: 0;
      right: 0;
    }

    & .simplebar-content-wrapper {
      direction: inherit;
      box-sizing: border-box !important;
      position: relative;
      display: block;
      height: 100%;
      width: auto;
      max-width: 100%;
      max-height: 100%;
      overflow: auto;
      scrollbar-width: none;
      -ms-overflow-style: none;
      flex: 1 1 0%;
      min-width: 0;
      min-height: 0;
      display: flex;
      flex-direction: column;
      height: 100%;
    }

    & .simplebar-content-wrapper::-webkit-scrollbar,
    & .simplebar-hide-scrollbar::-webkit-scrollbar {
      display: none;
      width: 0;
      height: 0;
    }

    & .simplebar-content:after,
    & .simplebar-content:before {
      content: " ";
      display: table;
    }

    & .simplebar-placeholder {
      max-height: 100%;
      max-width: 100%;
      width: 100%;
      pointer-events: none;
    }

    & .simplebar-height-auto-observer-wrapper {
      box-sizing: inherit !important;
      height: 100%;
      width: 100%;
      max-width: 1px;
      position: relative;
      float: left;
      max-height: 1px;
      overflow: hidden;
      z-index: -1;
      padding: 0;
      margin: 0;
      pointer-events: none;
      flex-grow: inherit;
      flex-shrink: 0;
      flex-basis: 0;
    }

    & .simplebar-scrollbar {
      position: absolute;
      left: 0;
      right: 0;
      min-height: 10px;
      opacity: 0;
      transition: opacity 0.2s linear 0.5s;

      &:hover,
      &.simplebar-visible {
        opacity: 1;
        transition-delay: 0s;
        transition-duration: 0s;
      }

      &::before {
        position: absolute;
        content: "";
        background: #000;
        border-radius: 7px;
        opacity: 0;
        transition: opacity 0.2s linear 0.5s;
        top: 2px;
        bottom: 2px;
        left: 2px;
        right: 2px;
      }
    }

    & .simplebar-track.simplebar-vertical {
      z-index: 1;
      right: 0;
      bottom: 0;
      top: 0;
      width: 11px;
      margin-right: 1px;

      & .simplebar-scrollbar::before {
        background: var(--text-disable);
      }

      &:hover .simplebar-scrollbar::before,
      &.simplebar-hover .simplebar-scrollbar::before {
        background: var(--text-tertiary);
      }
    }

    /* 滚动条悬停样式 */
    &:hover .simplebar-scrollbar {
      opacity: 1;
    }

    & .simplebar-dummy-scrollbar-size {
      direction: rtl;
      position: fixed;
      opacity: 0;
      visibility: hidden;
      height: 500px;
      width: 500px;
      overflow-y: hidden;
      overflow-x: scroll;
      -ms-overflow-style: scrollbar !important;
    }

    & .simplebar-dummy-scrollbar-size > div {
      width: 200%;
      height: 200%;
      margin: 10px 0;
    }

    & .simplebar-hide-scrollbar {
      position: fixed;
      left: 0;
      visibility: hidden;
      overflow-y: scroll;
      scrollbar-width: none;
      -ms-overflow-style: none;
    }
  `,
}));

export type ScrollableContentRef = {
  scrollToBottom: () => void;
  isScrolledToBottom: (threshold?: number) => boolean;
  canScroll: () => boolean;
};

// 组件定义
const ScrollableContent = forwardRef<ScrollableContentRef, ScrollableContentProps>(
  ({ children, onScroll }, ref) => {
    const { styles } = useStyles();
    // 外层容器引用（非实际滚动元素）
    const contentWrapperRef = useRef<HTMLDivElement>(null);
    // 实际滚动元素引用（.simplebar-content-wrapper）
    const scrollableRef = useRef<HTMLDivElement>(null);

    const scrollToBottom = () => {
      if (scrollableRef.current) {
        scrollableRef.current.scrollTop = scrollableRef.current.scrollHeight;
      }
    };

    const isScrolledToBottom = (threshold = 10) => {
      if (!scrollableRef.current) return false;
      const { scrollTop, scrollHeight, clientHeight } = scrollableRef.current;
      return scrollHeight - scrollTop - clientHeight <= threshold;
    };

    const canScroll = () => {
      if (!scrollableRef.current) return false;
      return scrollableRef.current.scrollHeight > scrollableRef.current.clientHeight;
    };

    // 暴露方法给父组件
    useImperativeHandle(ref, () => ({
      scrollToBottom,
      isScrolledToBottom,
      canScroll
    }));

    const handleScroll = (event: React.UIEvent<HTMLDivElement>) => {
      onScroll?.(event);
    };

    return (
      <div className={styles.scrollContainer}>
        <div
          ref={contentWrapperRef}
          className="simplebar-scrollable-y"
          style={{ '--simplebar-scrollbar-width': '6px' } as React.CSSProperties}
        >
          <div className="simplebar-wrapper" style={{ margin: 0 }}>
            <div className="simplebar-height-auto-observer-wrapper">
              <div className="simplebar-height-auto-observer"></div>
            </div>
            <div className="simplebar-mask">
              <div className="simplebar-offset" style={{ right: '0px', bottom: '0px' }}>
                <div
                  ref={scrollableRef}
                  className="simplebar-content-wrapper"
                  tabIndex={0}
                  role="region"
                  aria-label="scrollable content"
                  style={{ height: '100%', overflow: 'hidden scroll' }}
                  onScroll={handleScroll}
                >
                  <div className="simplebar-content" style={{ padding: '0px' }}>
                    {children}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }
);

export default ScrollableContent;
