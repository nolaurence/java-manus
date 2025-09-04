package cn.nolaurene.cms.controller;

import cn.nolaurene.cms.common.dto.SavedImage;
import cn.nolaurene.cms.common.vo.BaseWebResult;
import cn.nolaurene.cms.exception.BusinessException;
import cn.nolaurene.cms.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;

@RestController
@Tag(name = "图片上传api")
@RequestMapping("/image")
public class ImageController {

    @Resource
    ImageService imageService;

    @PostMapping("/upload")
    @Operation(summary = "图片上传")
    public String uploadImage(MultipartFile file) {
        try {
            SavedImage savedImageInfo = imageService.saveImageToLocalDisk(file);
            return savedImageInfo.getImageURL();
        } catch (IOException e) {
            throw new BusinessException("保存图片到磁盘失败");
        }
    }

    @PostMapping("/standardUpload")
    @Operation(summary = "图片上传")
    public BaseWebResult<String> uploadImageWithStandardResp(MultipartFile file) {
        try {
            SavedImage savedImageInfo = imageService.saveImageToLocalDisk(file);
            return BaseWebResult.success(savedImageInfo.getImageURL());
        } catch (IOException e) {
            throw new BusinessException("保存图片到磁盘失败");
        }
    }
}
