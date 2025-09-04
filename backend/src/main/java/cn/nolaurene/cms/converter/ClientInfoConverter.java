package cn.nolaurene.cms.converter;

import cn.nolaurene.cms.common.dto.ClientInfoDTO;
import cn.nolaurene.cms.common.vo.ClientInfo;
import cn.nolaurene.cms.dal.entity.ClientsDO;
import org.springframework.stereotype.Service;

@Service
public class ClientInfoConverter {

    public static ClientInfoDTO fromDataBaseObject(ClientsDO dataBaseObject) {
        ClientInfoDTO clientInfoDTO = new ClientInfoDTO();
        clientInfoDTO.setClientId(dataBaseObject.getId());
        clientInfoDTO.setClientName(dataBaseObject.getName());
        clientInfoDTO.setCreatorAccount(dataBaseObject.getCreatorAccount());
        clientInfoDTO.setModifierAccount(dataBaseObject.getModifierAccount());
        clientInfoDTO.setGmtCreate(dataBaseObject.getGmtCreate());
        clientInfoDTO.setGmtModified(dataBaseObject.getGmtModified());
        return clientInfoDTO;
    }

    public static ClientInfo fromDataTransferObject(ClientInfoDTO dataTransferObject) {
        ClientInfo clientInfo = new ClientInfo();
        clientInfo.setClientId(dataTransferObject.getClientId());
        clientInfo.setClientName(dataTransferObject.getClientName());
        return clientInfo;
    }
}
