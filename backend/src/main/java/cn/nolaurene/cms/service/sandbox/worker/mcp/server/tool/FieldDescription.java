package cn.nolaurene.cms.service.sandbox.worker.mcp.server.tool;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author nolaurence
 * @date 2025/7/8 下午1:26
 * @description: 给字段家标识的注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldDescription {
    String value();
}
