package cn.nolaurene.cms.service;

import cn.nolaurene.cms.dal.entity.AgentSessionServerDO;
import cn.nolaurene.cms.dal.mapper.AgentSessionServerMapper;
import io.mybatis.mapper.example.Example;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class AgentSessionServerService {

    @Resource
    private AgentSessionServerMapper agentSessionServerMapper;

    public AgentSessionServerDO getByAgentId(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return null;
        }

        Example<AgentSessionServerDO> example = new Example<>();
        Example.Criteria<AgentSessionServerDO> criteria = example.createCriteria();
        criteria.andEqualTo(AgentSessionServerDO::getAgentId, agentId);

        Optional<AgentSessionServerDO> result = agentSessionServerMapper.selectOneByExample(example);
        return result.orElse(null);
    }

    public void saveOrUpdate(String agentId, String serverIp, Integer serverPort) {
        if (agentId == null || agentId.isEmpty()) {
            throw new IllegalArgumentException("Agent ID不能为空");
        }
        if (serverIp == null || serverIp.isEmpty()) {
            throw new IllegalArgumentException("Server IP不能为空");
        }

        AgentSessionServerDO existing = getByAgentId(agentId);

        if (existing != null) {
            existing.setServerIp(serverIp);
            existing.setServerPort(serverPort);
            existing.setGmtModified(new Date());
            agentSessionServerMapper.updateByPrimaryKeySelective(existing);
            log.info("更新Agent Session Server信息: agentId={}, serverIp={}, serverPort={}", agentId, serverIp, serverPort);
        } else {
            AgentSessionServerDO newRecord = new AgentSessionServerDO();
            newRecord.setAgentId(agentId);
            newRecord.setServerIp(serverIp);
            newRecord.setServerPort(serverPort);
            newRecord.setGmtCreate(new Date());
            newRecord.setGmtModified(new Date());
            agentSessionServerMapper.insertSelective(newRecord);
            log.info("创建Agent Session Server信息: agentId={}, serverIp={}, serverPort={}", agentId, serverIp, serverPort);
        }
    }

    public void deleteByAgentId(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return;
        }

        AgentSessionServerDO record = getByAgentId(agentId);
        if (record != null) {
            agentSessionServerMapper.deleteByPrimaryKey(record.getId());
            log.info("删除Agent Session Server信息: agentId={}", agentId);
        }
    }

    public String getCurrentServerIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.error("获取当前服务器IP失败", e);
            return "127.0.0.1";
        }
    }
}
