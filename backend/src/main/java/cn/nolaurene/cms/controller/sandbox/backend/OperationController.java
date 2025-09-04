package cn.nolaurene.cms.controller.sandbox.backend;

import cn.nolaurene.cms.service.sandbox.backend.session.GlobalAgentSessionManager;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ops")
@Tag(name = "sandbox backend - dev ops interface")
public class OperationController {

    @Resource
    private GlobalAgentSessionManager globalAgentSessionManager;

    @GetMapping("/sessions")
    public List<String> getAllSessions() {
        // 获取所有会话ID
        return globalAgentSessionManager.getAllSessionIds();
    }
}
