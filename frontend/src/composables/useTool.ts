import React, { useMemo } from 'react';
import type { ToolContent } from '@/types/message';
import {
  TOOL_ICON_MAP,
  TOOL_NAME_MAP,
  TOOL_FUNCTION_MAP,
  TOOL_FUNCTION_ARG_MAP,
  TOOL_COMPONENT_MAP
} from '@/constants/tool';

export type ToolMeta = {
  icon?: React.FC;
  name: String;
  function: string;
  functionArg: any;
  view?: React.FC;
}

export function useToolInfo(tool?: ToolContent | null) {
  const toolInfo = useMemo(() => {
    if (!tool) return null;

    console.log(`useToolInfo called for tool: ${tool.name}`, tool);
    const argKey = TOOL_FUNCTION_ARG_MAP[tool.function];
    let functionArg = tool.args[argKey] || '';

    if (argKey === 'file') {
      functionArg = functionArg.replace(/^\/home\/ubuntu\//, '');
    }

    return {
      icon: TOOL_ICON_MAP[tool.name] || null,
      name: TOOL_NAME_MAP[tool.name] || '',
      // function: TOOL_FUNCTION_MAP[tool.function] || '',
      function: tool.function,
      functionArg,
      view: TOOL_COMPONENT_MAP[tool.name] || null
    };
  }, [tool]);

  return { toolInfo };
}
