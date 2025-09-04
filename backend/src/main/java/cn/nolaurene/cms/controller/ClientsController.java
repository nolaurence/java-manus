package cn.nolaurene.cms.controller;

import cn.nolaurene.cms.common.dto.*;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.common.vo.ClientInfo;
import cn.nolaurene.cms.common.vo.PagedData;
import cn.nolaurene.cms.common.vo.User;
import cn.nolaurene.cms.converter.ClientInfoConverter;
import cn.nolaurene.cms.exception.BusinessException;
import cn.nolaurene.cms.service.ClientsInfoService;
import cn.nolaurene.cms.service.UserLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@Tag(name = "客户信息api")
@RequestMapping("/clients")
public class ClientsController {

    @Resource
    UserLoginService userLoginService;

    @Resource
    ClientsInfoService clientsInfoService;

    @PostMapping("/add")
    @Operation(summary = "添加客户信息")
    public BaseWebResult<Boolean> addClient(@RequestBody ClientAddRequest req,
                                            HttpServletRequest httpServletRequest) {
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        req.setCreatorAccount(currentUserInfo.getAccount());
        return BaseWebResult.success(clientsInfoService.addClient(req));
    }

    @PostMapping("/update")
    @Operation(summary = "更新客户信息")
    public BaseWebResult<Boolean> updateClient(@RequestBody ClientUpdateRequest req,
                                               HttpServletRequest httpServletRequest) {
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        req.setModifierAccount(currentUserInfo.getAccount());
        return BaseWebResult.success(clientsInfoService.updateClient(req));
    }

    @GetMapping("/id")
    @Operation(summary = "根据id查询客户信息")
    public BaseWebResult<ClientInfo> queryClientById(@RequestParam Long clientId) {
        ClientInfoDTO clientInfoDTO = clientsInfoService.getClientInfo(clientId);
        if (null == clientInfoDTO) {
            throw new BusinessException("没有数据");
        }
        return BaseWebResult.success(ClientInfoConverter.fromDataTransferObject(clientInfoDTO));
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询客户信息")
    public BaseWebResult<PagedData<ClientInfo>> pageQueryClient(@RequestBody ClientQueryRequest request,
                                                                HttpServletRequest httpServletRequest) {
        User currentUserInfo = userLoginService.getCurrentUserInfo(httpServletRequest);
        request.setCreator(currentUserInfo.getAccount());
        // 查数据
        if (null == request.getCurrentPage()) {
            request.setCurrentPage(1);
        }
        if (null == request.getPageSize()) {
            request.setPageSize(20);
        }
        List<ClientInfoDTO> pagedClientInfoList = clientsInfoService.getPagedClientInfoList(request);
        if (CollectionUtils.isEmpty(pagedClientInfoList)) {
            throw new BusinessException("没有数据");
        }
        List<ClientInfo> clientInfoList = pagedClientInfoList.stream().map(ClientInfoConverter::fromDataTransferObject).collect(Collectors.toList());

        // 查total count
        Long totalCount = clientsInfoService.getTotalCount(request);
        PagedData<ClientInfo> pagedData = new PagedData<>();
        pagedData.setList(clientInfoList);

        Pagination pagination = new Pagination();
        pagination.setCurrent(request.getCurrentPage());
        pagination.setPageSize(request.getPageSize());
        pagination.setTotal(totalCount);
        pagedData.setPagination(pagination);

        return BaseWebResult.success(pagedData);
    }
}
