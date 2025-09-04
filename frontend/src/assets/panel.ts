import { createStyles } from 'antd-style';

export const useStyles = createStyles((utils) => {
  const css = utils.css;

  return {
    panelRoot: css`
      height: 100%;
      display: flex;
      flex-direction: column;
      position: fixed;
      top: 0;
      left: 0;
      bottom: 0;
      z-index: 1;
      width: 24px;
      transition: width 0.36s cubic-bezier(0.4, 0, 0.2, 1);
    `,
    panelLeftIconBox: css`
      display: flex;
      width: 1.75rem;
      height: 1.75rem;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      border-radius: 0.375rem;

      &:hover {
        background-color: var(--fill-tsp-gray-main);
      }
    `,
    searchIconBox: css`
      display: flex;
      flex-direction: row;
      gap: 0.25rem; /* 对应 Tailwind 的 gap-1 */
    `,
    searchIcon: css`
      display: flex;
      width: 1.75rem; /* Tailwind 的 w-7 */
      height: 1.75rem; /* Tailwind 的 h-7 */
      align-items: center;
      justify-content: center;
      cursor: pointer;
      border-radius: 0.375rem; /* Tailwind 的 rounded-md */
      background-color: transparent; /* 默认背景色 */
      transition: background-color 0.2s ease; /* 可选：添加过渡效果 */

      &:hover {
        background-color: var(--fill-tsp-gray-main);
      }
    `,
    newTaskBox: css`
      padding-left: 0.75rem; /* Tailwind 的 px-3 */
      padding-right: 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 12px;
      flex-shrink: 0;
    `,
    newTaskButton: css`
      display: inline-flex;
      align-items: center;
      justify-content: center;
      white-space: nowrap;
      font-weight: 500;
      transition: background-color 0.2s ease, color 0.2s ease, opacity 0.2s ease, box-shadow 0.2s ease, transform 0.1s ease;
      outline: none;
      background-color: var(--Button-primary-white);
      color: var(--text-primary);
      height: 36px;
      padding-left: 12px;
      padding-right: 12px;
      border-radius: 10px;
      box-shadow: 0 1px 3px 0 var(--shadow-S);
      font-size: 0.875rem; /* text-sm */
      min-width: 36px;
      width: 100%;
      gap: 6px;
      border: none;
      cursor: pointer;

      &.focus-visible {
        outline: none;
        box-shadow: 0 0 0 2px #4a90e2;
      }

      &:hover {
        opacity: 0.7;
      }

      &:active {
        opacity: 0.7;
      }
    `,
  };
});
