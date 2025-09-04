import { createStyles } from 'antd-style';

// @ts-ignore
export const useStyles = createStyles(({ token, css }) => {
  return {
    container: css`
      max-width: 100%;
      padding: 0 1.25rem;
      justify-content: flex-start;
      align-items: flex-start;
      flex-direction: column;
      flex: 1 1 0;
      display: flex;
      width: 100%;
      height: 100%;
      background: var(--background-gray-main);
    `,
    header: css`
      padding-top: 1rem;
      background-color: var(--background-gray-main);
      width: 100%;
      position: sticky;
      top: 0;
      //z-index: 1;
    `,
    inlineFlexContainer: css`
      display: inline-flex;
      gap: 0.5rem;
    `,
    logoContainer: css`
      color: var(--text-primary);
      font-size: 1.125rem; /* text-lg */
      font-weight: 500;    /* font-medium */
      width: 100%;         /* w-full */
      display: flex;       /* flex */
      flex-direction: row; /* flex-row */
      align-items: center; /* items-center */
      justify-content: space-between; /* justify-between */
      flex: 1 1 0;        /* flex-1 */
      min-width: 0;        /* min-w-0 */
    `,
    logoIconContainer: css`
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
    botIcon: css`
      display: inline-block;
      width: 24px;
      height: 24px;
      color: var(--icon-secondary);
    `,
    logoSeparator: css`
      display: flex;
      flex-direction: row;
      align-items: center;
      gap: 0.5rem; /* 或 8px */
      flex: 1 1 0;
      min-width: 0;
    `,
    logoTitle: css`
      display: inline-block;
      width: 200px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    `,
    messageList: css`
      display: flex;
      flex-direction: column;
      width: 100%;
      gap: 12px;
      padding-bottom: 80px;
      padding-top: 12px;
      flex: 1;
      overflow-y: auto;
    `,
    loadingIndicatorContainer: css`
      display: flex;
      align-items: center;
      gap: 0.25rem;
      color: var(--text-tertiary);
      font-size: 0.875rem;
      line-height: 1.25rem;
    `,
    animateBounceDotContainer: css`
      display: flex;
      gap: 0.25rem;
      position: relative;
      top: 4px;
    `,
    loadingDot: css`
      width: 3px;
      height: 3px;
      border-radius: 50%;
      background-color: var(--icon-tertiary);
      animation: dot-animation 1.5s infinite;

      @keyframes dot-animation {
        0% {
          transform: translateY(0);
        }
        20% {
          transform: translateY(-4px);
        }
        40% {
          transform: translateY(0);
        }
        100% {
          transform: translateY(0);
        }
      }
    `,
    inputArea: css`
      display: flex;
      flex-direction: column;
      background-color: var(--background-gray-main);
      position: sticky;
      bottom: 0;
    `,
    followButton: css`
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 9999px;
      background-color: var(--background-white-main);
      border-width: 1px;
      border-color: var(--border-main);
      box-shadow: 0px 5px 16px 0px var(--shadow-S), 0px 0px 1.25px 0px var(--shadow-S);
      position: absolute;
      top: -5rem;
      left: 50%;
      transform: translateX(-50%);

      &:hover {
        background-color: var(--background-gray-main);
      }
    `,
    arrowDown: css`
      color: var(--icon-primary);
    `,
    planPanel: css`
      background-color: var(--background-gray-main);
      border-radius: 22px 22px 0 0;

      &:not(:empty) {
        padding-bottom: 0.5rem;
      }
    `,
    planPanelContainer1: css`
      border-width: 1px;
      border-color: rgba(0, 0, 0, 0.05);
      background-color: var(--background-menu-white);
      border-radius: 16px;
      box-shadow:
        0px 0px 1px 0px rgba(0, 0, 0, 0.05),
        0px 8px 32px 0px rgba(0, 0, 0, 0.04);
      z-index: 99;
      display: flex;
      flex-direction: column;
      padding-top: 1rem;
      padding-bottom: 1rem;

      &.dark & {
        border-color: var(--border-main);
      }

      @media (min-width: 640px) {
        border-radius: 12px;
      }
    `,
    planPanelContainer2: css`
      display: flex;
      padding-left: 1rem;
      padding-right: 1rem;
      margin-bottom: 1rem;
      width: 100%;
    `,
    planPanelContainer3: css`
      display: flex;
      align-items: flex-start;
      margin-left: auto;
    `,
    planPanelContainer4: css`
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
    `,
    showPanelButton: css`
      display: flex;
      align-items: center;
      justify-content: center;
      width: 1.75rem;
      height: 1.75rem;
      cursor: pointer;
      border-radius: 0.375rem;

      &:hover {
        background-color: var(--fill-tsp-gray-main);
      }
    `,
    chevronDown: css`
      var(--icon-tertiary);
    `,
    taskProgressContainer1: css`
      background-color: var(--fill-tsp-gray-main);
      border-radius: 0.5rem;
      padding-top: 1rem;
      padding-bottom: 0.5rem;
    `,
    taskProgressFlexBox: css`
      display: flex;
      justify-content: space-between;
      width: 100%;
      padding-left: 1rem;
      padding-right: 1rem;
    `,
    taskProgressText: css`
      color: var(--text-primary);
      font-weight: bold;
    `,
    taskProgressContainer2: css`
      display: flex;
      align-items: center;
      gap: 0.75rem;
    `,
    taskProgressContainer3: css`
      font-size: 0.75rem; /* 12px */
      color: var(--text-tertiary);
    `,
    taskProgressScrollBox: css`
      max-height: min(calc(100vh - 360px), 400px);
      overflow-y: auto;
    `,
    stepIconBox: css`
      display: flex;
      align-items: flex-start;
      gap: 0.625rem; /* 10px */
      width: 100%;
      padding: 0.5rem 1rem;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    `,
    stepDescriptionContainer: css`
      display: flex;
      flex-direction: column;
      width: 100%;
      gap: 2px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    `,
    stepDescription: css`
      font-size: 0.875rem; /* 对应 text-sm */
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: var(--text-primary);
    `,
    anotherPlanPanel: css`
      display: flex;
      flex-direction: row;
      align-items: flex-start;
      justify-content: space-between;
      padding-right: 1rem; /* pe-3 */
      position: relative;
      border: 1px solid rgba(0, 0, 0, 0.08); /* border-black/8 */
      background-color: var(--background-menu-white);
      border-radius: 16px;
      box-shadow:
        0px 0px 1px 0px rgba(0, 0, 0, 0.05),
        0px 8px 32px 0px rgba(0, 0, 0, 0.04);
      z-index: 99;
      cursor: pointer;
      transition: all 0.2s ease;

      @media (max-width: 640px) {
        border-radius: 12px;
      }

      &.dark & {
        border-color: var(--border-main);
      }
    `,
    anotherPlanPanelBox1: css`
      flex: 1;
      min-width: 0;
      position: relative;
      overflow: hidden;
    `,
    anotherPlanPanelBox2: css`
      width: 100%;
      height: 36px;
      --offset: -36px; /
    `,
    anotherPlanPanelBox3: css`
      width: 100%;
    `,
    anotherPlanPanelBox4: css`
      display: flex;
      align-items: flex-start;
      gap: 0.625rem; /* gap-2.5 = 0.625rem = 10px */
      width: 100%;
      padding: 0.5rem 1rem;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    `,
    runningStepContainer: css`
      display: flex;
      flex-direction: column;
      width: 100%;
      gap: 2px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    `,
    runningStepText: css`
      font-size: 0.875rem; /* 对应 text-sm */
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: var(--text-tertiary);
    `,
    runningStepButton: css`
      display: flex;
      height: 100%;
      cursor: pointer;
      justify-content: center;
      align-items: flex-start;
      gap: 0.5rem; /* gap-2 */
      flex-shrink: 0;
      padding-top: 0.625rem; /* py-2.5 = 10px */
      padding-bottom: 0.625rem;
      background: none;
      border: none;
      transition: opacity 0.2s ease;

      &:hover {
        opacity: 0.8;
      }
    `,
    runningStepProgress: css`
      font-size: 0.75rem; /* text-xs */
      color: var(--text-tertiary);
      white-space: nowrap;
      display: none;

      @media (min-width: 640px) {
        display: flex;
      }
    `,
    clock: css`
      position: relative;
      top: 2px;
      flex-shrink: 0;
    `,
  };
});
