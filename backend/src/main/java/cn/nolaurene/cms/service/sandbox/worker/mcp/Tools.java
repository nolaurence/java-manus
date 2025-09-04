package cn.nolaurene.cms.service.sandbox.worker.mcp;

import cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool.Tool;
import cn.nolaurene.cms.service.sandbox.worker.mcp.tools.*;

import java.util.ArrayList;
import java.util.List;

public class Tools {

    public List<Tool> getSnapshotTools() {
        List<Tool> tools = new ArrayList<>();
        tools.addAll(CommonTool.getAllTools(true));
        tools.addAll(ConsoleTool.getAllTools(true));
        tools.addAll(DialogTool.getAllTools(true));
        tools.addAll(FilesTool.getAllTools(true));
        tools.addAll(KeyboardTool.getAllTools(true));
        tools.addAll(NavigateTool.getAllTools(true));
        tools.addAll(NetworkTool.getAllTools(true));
        tools.addAll(PdfTool.getAllTools(true));
        tools.addAll(ScreenshotTool.getAllTools(true));
        tools.addAll(SnapshotTool.getAllTools(true));
        tools.addAll(TabsTool.getAllTools(true));
        tools.addAll(TestingTool.getAllTools(true));
        tools.addAll(WaitTool.getAllTools(true));

        return tools;
    }
}
