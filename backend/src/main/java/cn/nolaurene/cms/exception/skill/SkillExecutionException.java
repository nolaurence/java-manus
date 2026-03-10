package cn.nolaurene.cms.exception.skill;

/**
 * Skill执行异常
 *
 * @author nolaurence
 */
public class SkillExecutionException extends SkillException {

    public SkillExecutionException(String message) {
        super(message);
    }

    public SkillExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
