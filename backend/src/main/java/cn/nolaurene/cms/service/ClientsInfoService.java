package cn.nolaurene.cms.service;

import cn.nolaurene.cms.common.dto.ClientAddRequest;
import cn.nolaurene.cms.common.dto.ClientInfoDTO;
import cn.nolaurene.cms.common.dto.ClientQueryRequest;
import cn.nolaurene.cms.common.dto.ClientUpdateRequest;
import cn.nolaurene.cms.converter.ClientInfoConverter;
import cn.nolaurene.cms.dal.entity.ClientsDO;
import cn.nolaurene.cms.dal.mapper.ClientsMapper;
import cn.nolaurene.cms.exception.BusinessException;
import io.mybatis.mapper.example.Example;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ClientsInfoService {

    @Resource
    ClientsMapper clientsMapper;

    public boolean addClient(ClientAddRequest req) {
        // 参数校验
        if (StringUtils.isBlank(req.getClientName())) {
            throw new BusinessException("客户名称不能为空");
        }
        if (StringUtils.isBlank(req.getCreatorAccount())) {
            throw new BusinessException("创建人账号不能为空");
        }
        // 先看有没有重名的
        Example<ClientsDO> example = new Example<>();
        Example.Criteria<ClientsDO> criteria = example.createCriteria();

        criteria.andEqualTo(ClientsDO::getName, req.getClientName());
        criteria.andEqualTo(ClientsDO::getCreatorAccount, req.getCreatorAccount());

        long count = clientsMapper.countByExample(example);
        if (count > 0) {
            throw new BusinessException("已经添加过");
        }

        // 添加
        ClientsDO clientsDO = new ClientsDO();
        Date createTime = new Date();
        clientsDO.setName(req.getClientName());
        clientsDO.setCreatorAccount(req.getCreatorAccount());
        clientsDO.setGmtCreate(createTime);
        clientsDO.setGmtModified(createTime);

        int i = clientsMapper.insertSelective(clientsDO);
        return i > 0;
    }

    public boolean updateClient(ClientUpdateRequest req) {
        // 参数校验
        if (req.getClientId() == null) {
            throw new BusinessException("客户id不能为空");
        }
        if (StringUtils.isBlank(req.getClientName())) {
            throw new BusinessException("客户名称不能为空");
        }
        if (StringUtils.isBlank(req.getModifierAccount())) {
            throw new BusinessException("修改人账号不能为空");
        }

        // 更新
        ClientsDO clientsDO = new ClientsDO();
        clientsDO.setId(req.getClientId());
        clientsDO.setName(req.getClientName());
        clientsDO.setModifierAccount(req.getModifierAccount());
        clientsDO.setGmtModified(new Date());

        int i = clientsMapper.updateByPrimaryKeySelective(clientsDO);
        return i > 0;
    }

    public ClientInfoDTO getClientInfo(Long clientId) {
        Optional<ClientsDO> clientsDOOptional = clientsMapper.selectByPrimaryKey(clientId);

        if (clientsDOOptional.isPresent()) {

            // 转换成DTO
            ClientsDO clientsDO = clientsDOOptional.get();
            return ClientInfoConverter.fromDataBaseObject(clientsDO);
        }

        return null;
    }

    public List<ClientInfoDTO> getPagedClientInfoList(ClientQueryRequest request) {
        // 查询数据库
        Example<ClientsDO> example = new Example<>();
        Example.Criteria<ClientsDO> criteria = example.createCriteria();

        if (StringUtils.isNotBlank(request.getName())) {
            criteria.andLike(ClientsDO::getName, "%" + request.getName() + "%");
        }
        if (StringUtils.isNotBlank(request.getCreator())) {
            criteria.andEqualTo(ClientsDO::getCreatorAccount, request.getCreator());
        }

        // 分页查询
        RowBounds rowBounds = new RowBounds((request.getCurrentPage() - 1) * request.getPageSize(), request.getPageSize());

        List<ClientsDO> clientsDOList = clientsMapper.selectByExample(example, rowBounds);

        // 转换成DTO
        if (clientsDOList == null || clientsDOList.isEmpty()) {
            return null;
        }

        return clientsDOList.stream().map(ClientInfoConverter::fromDataBaseObject).collect(Collectors.toList());
    }

    public Long getTotalCount(ClientQueryRequest request) {
        Example<ClientsDO> example = new Example<>();
        Example.Criteria<ClientsDO> criteria = example.createCriteria();

        if (StringUtils.isNotBlank(request.getName())) {
            criteria.andLike(ClientsDO::getName, "%" + request.getName() + "%");
        }
        if (StringUtils.isNotBlank(request.getCreator())) {
            criteria.andEqualTo(ClientsDO::getCreatorAccount, request.getCreator());
        }

        return clientsMapper.countByExample(example);
    }
}
