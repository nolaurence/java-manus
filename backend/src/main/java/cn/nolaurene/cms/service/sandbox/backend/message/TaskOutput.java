package cn.nolaurene.cms.service.sandbox.backend.message;

import cn.nolaurene.cms.common.sandbox.backend.model.SSEEventType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskOutput {

    private SSEEventType eventType;

    private String data;

    private long timestamp;
}
