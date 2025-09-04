package cn.nolaurene.cms.controller;

import cn.nolaurene.cms.common.dto.AddSalesRecordRequest;
import cn.nolaurene.cms.common.dto.ClientInfoDTO;
import cn.nolaurene.cms.common.dto.UpdateSalesRecordRequest;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.common.vo.SalesRecord;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.converter.SalesRecordConverter;
import cn.nolaurene.cms.dal.enhance.entity.SalesRecordWithClientDTO;
import cn.nolaurene.cms.dal.entity.SalesRecordDO;
import cn.nolaurene.cms.exception.BusinessException;
import cn.nolaurene.cms.service.ClientsInfoService;
import cn.nolaurene.cms.service.SalesRecordService;
import cn.nolaurene.cms.service.UserLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/salesRecord")
@Tag(name = "销售记录api")
public class SalesRecordController {

    @Resource
    SalesRecordService salesRecordService;

    @Resource
    UserLoginService userLoginService;

    @Resource
    ClientsInfoService clientsInfoService;

    @PostMapping("/add")
    @Operation(summary = "添加销售记录")
    public BaseWebResult<Boolean> addSalesRecord(@RequestBody AddSalesRecordRequest req, HttpServletRequest httpServletRequest) {
        boolean result = salesRecordService.addSalesRecord(req, httpServletRequest);
        return BaseWebResult.success(result);
    }

    @PostMapping("/update")
    @Operation(summary = "更新销售记录")
    public BaseWebResult<Boolean> updateSalesRecord(@RequestBody UpdateSalesRecordRequest request, HttpServletRequest httpServletRequest) {
        boolean result = salesRecordService.updateSalesRecord(request, httpServletRequest);
        return BaseWebResult.success(result);
    }

    @GetMapping("/delete")
    @Operation(summary = "删除销售记录")
    public BaseWebResult<Boolean> deleteSalesRecord(@RequestParam Long id, HttpServletRequest httpServletRequest) {
        boolean result = salesRecordService.deleteSalesRecord(id, httpServletRequest);
        return BaseWebResult.success(result);
    }

    @GetMapping("/id/{recordId}")
    @Operation(summary = "通过id查询销售记录")
    public BaseWebResult<SalesRecord> getRecordById(@PathVariable long recordId, HttpServletRequest httpServletRequest) {
        // 查销售记录
        SalesRecordDO databaseObject = salesRecordService.getSalesRecordById(recordId);
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        if (!StringUtils.equals(currentUserInfo.getAccount(), databaseObject.getCreator()) || !StringUtils.equals(currentUserInfo.getAccount(), databaseObject.getModifier())) {
            throw new BusinessException("不是创建者or更新者，不支持修改");
        }
        // 查客户信息
        ClientInfoDTO clientInfo = clientsInfoService.getClientInfo(databaseObject.getClientId());

        // 参数转换
        SalesRecord salesRecord = SalesRecordConverter.fromSalesRecordDO(databaseObject);
        if (null != clientInfo) {
            salesRecord.setClientName(clientInfo.getClientName());
            salesRecord.setClientId(clientInfo.getClientId());
        }
        return BaseWebResult.success(salesRecord);
    }

    @GetMapping("/list")
    @Operation(summary = "搜索获取销售记录列表")
    public BaseWebResult<List<SalesRecord>> getSalesRecordList(@RequestParam Long startTimeMs, @RequestParam Long endTimeMs, @RequestParam(required = false) Long clientId,
                                                               HttpServletRequest httpServletRequest) {
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);

        Date startTime = new Date(startTimeMs);
        Date endTime = new Date(endTimeMs + 24 * 60 * 60 * 1000 - 1);
        List<SalesRecordWithClientDTO> enhancedDataObjectList = salesRecordService.getSalesRecordList(startTime, endTime, currentUserInfo.getAccount(), clientId);
        // 判空
        if (CollectionUtils.isEmpty(enhancedDataObjectList)) {
            return BaseWebResult.success(null);
        }

        List<SalesRecord> viewObjectList = enhancedDataObjectList.stream().map(SalesRecordConverter::fromEnhancedDataObject).collect(Collectors.toList());
        return BaseWebResult.success(viewObjectList);
    }

    @PostMapping("/sum")
    @Operation(summary = "计算列表中金额的总数")
    public BaseWebResult<Double> calculateSumPrice(@RequestBody List<SalesRecord> salesRecordList) {
        BigDecimal sum = new BigDecimal(0);
        for (SalesRecord salesRecord : salesRecordList) {
            sum = sum.add(salesRecord.getAmount());
        }

        return BaseWebResult.success(sum.doubleValue());
    }
}
