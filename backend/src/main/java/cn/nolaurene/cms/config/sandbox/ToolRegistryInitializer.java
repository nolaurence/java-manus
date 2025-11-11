package cn.nolaurene.cms.config.sandbox;


import cn.nolaurene.cms.service.sandbox.backend.ToolRegistry;
import cn.nolaurene.cms.service.sandbox.backend.tool.CalculatorTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author nolaurence
 * @date 2025/11/11 下午3:56
 * @description:
 */
@Component
public class ToolRegistryInitializer implements CommandLineRunner {

    @Autowired
    private ToolRegistry toolRegistry;

    @Override
    public void run(String... args) throws Exception {
        // 注册默认工具
        toolRegistry.register(new CalculatorTool());

        // 可以在这里注册更多工具
        // toolRegistry.register(new OtherTool());
    }
}
