package cn.nolaurene.cms.service;

import cn.nolaurene.cms.common.dto.AddSalesRecordRequest;
import cn.nolaurene.cms.common.dto.UpdateSalesRecordRequest;
import cn.nolaurene.cms.common.vo.SalesRecord;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.dal.enhance.entity.SalesRecordWithClientDTO;
import cn.nolaurene.cms.dal.enhance.mapper.SalesRecordEnhanceMapper;
import cn.nolaurene.cms.dal.entity.SalesRecordDO;
import cn.nolaurene.cms.dal.mapper.SalesRecordMapper;
import cn.nolaurene.cms.exception.BusinessException;
import io.mybatis.mapper.example.Example;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class SalesRecordService {

    @Resource
    UserLoginService userLoginService;

    @Resource
    SalesRecordMapper salesRecordMapper;

    @Resource
    SalesRecordEnhanceMapper salesRecordEnhanceMapper;

    public boolean addSalesRecord(AddSalesRecordRequest req, HttpServletRequest httpServletRequest) {
        Date gmtCreate = new Date();
        // 获取当前用户
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        // 转换成DO
        SalesRecordDO databaseObject = new SalesRecordDO();
        databaseObject.setDescription(req.getDescription());
        databaseObject.setAmount(BigDecimal.valueOf(req.getAmount()));
        databaseObject.setImage(req.getImageURL());
        databaseObject.setClientId(req.getClientId());

        // 塞当前用户
        databaseObject.setCreator(currentUserInfo.getAccount());
        databaseObject.setCreatorName(currentUserInfo.getName());
        databaseObject.setModifier(currentUserInfo.getAccount());
        databaseObject.setModifierName(currentUserInfo.getName());

        // 塞时间
        databaseObject.setGmtCreate(gmtCreate);
        databaseObject.setGmtModified(gmtCreate);

        int i = salesRecordMapper.insertSelective(databaseObject);
        if (i == 0) {
            throw new BusinessException("添加记录失败");
        }
        return true;
    }

    public boolean updateSalesRecord(UpdateSalesRecordRequest req, HttpServletRequest httpServletRequest) {
        // 获取当前用户
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        // 捞取库表记录
        SalesRecordDO dataObject = new SalesRecordDO();
        dataObject.setId(req.getId());
        Optional<SalesRecordDO> salesRecordDOResult = salesRecordMapper.selectByPrimaryKey(req.getId());

        if (salesRecordDOResult.isEmpty() || salesRecordDOResult.get().getIsDeleted()) {
            throw new BusinessException("记录不存在");
        }
        SalesRecordDO salesRecordDO = salesRecordDOResult.get();

        // 更新记录
        salesRecordDO.setDescription(req.getDescription());
        salesRecordDO.setAmount(BigDecimal.valueOf(req.getAmount()));
        salesRecordDO.setImage(req.getImageURL());
        salesRecordDO.setClientId(req.getClientId());
        salesRecordDO.setModifierName(currentUserInfo.getName());
        salesRecordDO.setModifier(currentUserInfo.getAccount());
        salesRecordDO.setGmtModified(new Date());

        int i = salesRecordMapper.updateByPrimaryKeySelective(salesRecordDO);
        return i > 0;
    }

    public boolean deleteSalesRecord(long id, HttpServletRequest httpServletRequest) {
        // 获取当前用户
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        // 捞取记录
        Optional<SalesRecordDO> salesRecordDOResult = salesRecordMapper.selectByPrimaryKey(id);
        if (salesRecordDOResult.isEmpty()) {
            throw new BusinessException("记录不存在");
        }

        SalesRecordDO salesRecordDO = salesRecordDOResult.get();
        if (salesRecordDO.getIsDeleted()) {
            return true;
        }

        // 执行删除
        salesRecordDO.setIsDeleted(true);
        salesRecordDO.setModifierName(currentUserInfo.getName());
        salesRecordDO.setModifier(currentUserInfo.getAccount());
        salesRecordDO.setGmtModified(new Date());
        int i = salesRecordMapper.updateByPrimaryKeySelective(salesRecordDO);
        return i > 0;
    }

    public SalesRecordDO getSalesRecordById (Long id) {
        Optional<SalesRecordDO> salesRecordDOResult = salesRecordMapper.selectByPrimaryKey(id);
        if (salesRecordDOResult.isEmpty() || salesRecordDOResult.get().getIsDeleted()) {
            throw new BusinessException("记录不存在");
        }
        return salesRecordDOResult.get();
    }

    public List<SalesRecordWithClientDTO> getSalesRecordList(Date startTime, Date endTime, String currentUser, Long clientId) {
        // 查db
        return salesRecordEnhanceMapper.selectSalesRecordWithClient(currentUser, startTime, endTime, clientId, 0);
    }
}
