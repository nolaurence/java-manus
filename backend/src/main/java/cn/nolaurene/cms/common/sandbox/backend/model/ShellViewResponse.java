package cn.nolaurene.cms.common.sandbox.backend.model;

import lombok.Data;

import java.util.List;

/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
@Data
public class ShellViewResponse {

    private String output;
    private String sessionId;
    private List<ConsoleRecordDTO> console;

    @Data
    public static class ConsoleRecordDTO {
        private String ps1;
        private String command;
        private String output;
    }
}
