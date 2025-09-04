package cn.nolaurene.cms.common.dto.tc;

import cn.nolaurene.cms.common.vo.tc.StyledTag;
import lombok.Data;

import java.util.List;

@Data
public class NodeData {

    private String text;

    private List<String> generalization;

    /**
     * @see cn.nolaurene.cms.common.vo.tc.StyledTag
     */
    private List<StyledTag> tag;

    private boolean expand;

    private boolean isActive;

    private String uid;

    // 以下字段暂时未用到，先定义
    private String richText;

    // 图片相关字段 start #################
    private String image;

    private String imageTitle;

    private ImageSize imageSize;

    // 图片相关字段 end #################

    private String hyperlink;

    private String hyperlinkTitle;

    private String note;

    private String attachmentUrl;

    private String attachmentName;

    private List<String> associativeLineTargets;

    private String associativeLineText;
}
