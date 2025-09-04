package cn.nolaurene.cms.common.sandbox.backend.prompt;


/**
 * @author nolau
 * @date 2025/6/24
 * @description
 */
public class PromptTemplate {

    public static String render(String template, String input) {
        return template.replace("{{input}}", input);
    }
}
