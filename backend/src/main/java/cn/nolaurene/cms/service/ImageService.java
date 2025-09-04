package cn.nolaurene.cms.service;

import cn.nolaurene.cms.common.dto.SavedImage;
import cn.nolaurene.cms.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ImageService {

    @Value("${image.path-prefix}")
    private String imageStoragePath;

    @Value("${image.host}")
    private String imageHost;

    private static final String allowedImageType = "[\"jpg\",\"jpeg\",\"png\",\"JPG\"]";

    public SavedImage saveImageToLocalDisk(MultipartFile imageFile) throws IOException {
        String originalFilename = imageFile.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw new BusinessException("获取图片文件名失败");
        }
        String fileExt = getFileExtension(originalFilename);

        List<String> allowedImageTypeList = JSON.parseArray(allowedImageType, String.class);
        // 判断图片格式
        if (!allowedImageTypeList.contains(fileExt)) {
            throw new BusinessException(String.format("格式 %s 不支持", fileExt));
        }

        // 存到本地
        String imageUniqueId = UUID.randomUUID() + "." + fileExt;
        File fileObj = new File(pathJoin(imageStoragePath, imageUniqueId));
        log.info("upload image: " + imageFile);

        imageFile.transferTo(fileObj);

        SavedImage imageInfo = new SavedImage();
        imageInfo.setImageUniqueId(imageUniqueId);
        imageInfo.setImageURL(imageHost + "/static/images/" + imageUniqueId);

        return imageInfo;
    }

    private String getFileExtension(String filename) {
        String fileExt = filename.substring(filename.lastIndexOf("."));
        return fileExt.replace(".", "");
    }

    private String pathJoin(String base, String pathToAdd) {
        Path filePath = Paths.get(imageStoragePath);
        filePath = filePath.resolve(pathToAdd);
        return filePath.toString();
    }
}
