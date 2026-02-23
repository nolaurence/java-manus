package cn.nolaurene.cms.service;

import cn.nolaurene.cms.dal.entity.LlmConfigDO;
import cn.nolaurene.cms.dal.mapper.LlmConfigMapper;
import io.mybatis.mapper.example.Example;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Optional;

/**
 * @author nolaurence
 * @description LLM配置服务
 */
@Slf4j
@Service
public class LlmConfigService {

    @Resource
    private LlmConfigMapper llmConfigMapper;

    /**
     * 根据用户ID获取LLM配置
     */
    public LlmConfigDO getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        
        // 使用 tk.mybatis 的 Example 查询
        Example<LlmConfigDO> example = new Example<>();
        Example.Criteria<LlmConfigDO> criteria = example.createCriteria();
        criteria.andEqualTo(LlmConfigDO::getUserId, userId);
        criteria.andEqualTo(LlmConfigDO::getIsDelete, false);
        
        Optional<LlmConfigDO> result = llmConfigMapper.selectOneByExample(example);
        return result.orElse(null);
    }

    /**
     * 保存或更新LLM配置
     */
    public void saveOrUpdate(Long userId, String endpoint, String apiKey, String modelName) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        LlmConfigDO existingConfig = getByUserId(userId);
        
        if (existingConfig != null) {
            // 更新现有配置
            existingConfig.setEndpoint(endpoint);
            existingConfig.setApiKey(apiKey);
            existingConfig.setModelName(modelName);
            existingConfig.setGmtModified(new Date());
            llmConfigMapper.updateByPrimaryKeySelective(existingConfig);
            log.info("更新LLM配置成功: userId={}, endpoint={}", userId, endpoint);
        } else {
            // 创建新配置
            LlmConfigDO newConfig = new LlmConfigDO();
            newConfig.setUserId(userId);
            newConfig.setEndpoint(endpoint);
            newConfig.setApiKey(apiKey);
            newConfig.setModelName(modelName);
            newConfig.setGmtCreate(new Date());
            newConfig.setGmtModified(new Date());
            newConfig.setIsDelete(false);
            llmConfigMapper.insertSelective(newConfig);
            log.info("创建LLM配置成功: userId={}, endpoint={}", userId, endpoint);
        }
    }

    /**
     * 删除用户的LLM配置(软删除)
     */
    public void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        
        LlmConfigDO config = getByUserId(userId);
        if (config != null) {
            config.setIsDelete(true);
            config.setGmtModified(new Date());
            llmConfigMapper.updateByPrimaryKeySelective(config);
            log.info("删除LLM配置成功: userId={}", userId);
        }
    }
}
