package cn.nolaurene.cms.exception.skill;

/**
 * Skill异常基类
 *
 * @author nolaurence
 */
public class SkillException extends RuntimeException {

    public SkillException(String message) {
        super(message);
    }

    public SkillException(String message, Throwable cause) {
        super(message, cause);
    }
}
