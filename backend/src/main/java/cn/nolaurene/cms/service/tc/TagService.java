package cn.nolaurene.cms.service.tc;

import cn.nolaurene.cms.common.constants.TagConstants;
import cn.nolaurene.cms.common.dto.Pagination;
import cn.nolaurene.cms.common.dto.tc.TagRequest;
import cn.nolaurene.cms.common.vo.PagedData;
import cn.nolaurene.cms.common.vo.tc.TagVO;
import cn.nolaurene.cms.dal.entity.CaseTagDO;
import cn.nolaurene.cms.dal.mapper.CaseTagMapper;
import cn.nolaurene.cms.exception.BusinessException;
import io.mybatis.mapper.example.Example;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TagService {

    @Resource
    CaseTagMapper tagMapper;

    public boolean addTag(String tagName, String creatorName) {
        if (TagConstants.systemTags.contains(tagName)) {
            throw new BusinessException("标签已存在, 系统标签");
        }
        Example<CaseTagDO> example = new Example<>();
        example.createCriteria().andEqualTo(CaseTagDO::getTag, tagName);
        Optional<CaseTagDO> caseTagDO = tagMapper.selectOneByExample(example);

        if (caseTagDO.isPresent()) {
            throw new BusinessException("标签已存在, id是 " + caseTagDO.get().getId());
        }
        CaseTagDO tagToAdd = new CaseTagDO();
        tagToAdd.setTag(tagName);
        tagToAdd.setCreatorName(creatorName);

        return tagMapper.insertSelective(tagToAdd) > 0;
    }

    public boolean deleteTag(long tagId) {
        Example<CaseTagDO> example = new Example<>();
        example.createCriteria().andEqualTo(CaseTagDO::getId, tagId);
        Optional<CaseTagDO> caseTagDO = tagMapper.selectOneByExample(example);
        if (caseTagDO.isEmpty()) {
            return true;
        }

        CaseTagDO dataObject = caseTagDO.get();
        dataObject.setGmtDeleted(new Date());
        return tagMapper.updateByPrimaryKeySelective(dataObject) > 0;
    }

    public PagedData<TagVO> getTagList(TagRequest request) {
        Example<CaseTagDO> example = new Example<>();
        Example.Criteria<CaseTagDO> criteria = example.createCriteria();

        if (StringUtils.isNotBlank(request.getTagName())) {
            criteria.andLike(CaseTagDO::getTag, "%" + request.getTagName() + "%");
        }
        if (StringUtils.isNotBlank(request.getCreatorName())) {
            criteria.andEqualTo(CaseTagDO::getCreatorName, request.getCreatorName());
        }

        criteria.andIsNull(CaseTagDO::getGmtDeleted);
        example.orderBy(CaseTagDO::getGmtCreate, Example.Order.DESC);

        RowBounds rowBounds = new RowBounds((request.getCurrent() - 1) * request.getPageSize(), request.getPageSize());

        List<CaseTagDO> caseTagDOS = tagMapper.selectByExample(example, rowBounds);
        long count = tagMapper.countByExample(example);

        PagedData<TagVO> tagPagedData = new PagedData<>();
        List<TagVO> tagVOList = caseTagDOS.stream().map(dataObject -> {
            TagVO tagVO = new TagVO();
            tagVO.setId(dataObject.getId());
            tagVO.setTagName(dataObject.getTag());
            tagVO.setCreatorName(dataObject.getCreatorName());
            tagVO.setGmtCreate(dataObject.getGmtCreate());
            return tagVO;
        }).collect(Collectors.toList());

        tagPagedData.setList(tagVOList);
        Pagination pagination = new Pagination();
        pagination.setCurrent(request.getCurrent());
        pagination.setPageSize(request.getPageSize());
        pagination.setTotal(count);
        tagPagedData.setPagination(pagination);
        return tagPagedData;
    }
}
