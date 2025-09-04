package cn.nolaurene.cms.common.dto.tc;

import lombok.Data;

@Data
public class TagStyle {

    /**
     * 标签矩形的圆角大小
     */
    private Integer radius;

    /**
     * 字号，建议文字高度不要大于height
     */
    private Integer fontSize;

    /**
     * 标签矩形的背景颜色
     */
    private String fill;

    /**
     * 标签矩形的高度
     */
    private Integer height;

    /**
     * 水平内边距，如果设置了width，将忽略该配置
     */
    private Integer paddingX;

    /**
     * 标签矩形的宽度，如果不设置，默认以文字的宽度+paddingX*2为宽度
     */
    private Integer width;
}
