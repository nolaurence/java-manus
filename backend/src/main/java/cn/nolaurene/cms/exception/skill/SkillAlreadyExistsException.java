package cn.nolaurene.cms.exception.skill;

/**
 * Skill已存在异常
 *
 * @author nolaurence
 */
public class SkillAlreadyExistsException extends SkillException {

    public SkillAlreadyExistsException(String message) {
        super(message);
    }
}
