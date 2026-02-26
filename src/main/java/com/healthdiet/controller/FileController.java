package com.healthdiet.controller;

import com.healthdiet.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Value("${file.upload-path}")
    private String uploadPath; // 这里拿到的是 "diet-uploads/"

    @Value("${file.access-path}")
    private String accessPath;

    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file) {
        if (file.isEmpty()) return Result.error("上传文件不能为空");

        // 1. 获取文件名和后缀
        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String newFileName = UUID.randomUUID().toString() + suffix;

        // 2. 【核心修改】动态获取项目根目录，拼接出绝对路径
        // System.getProperty("user.dir") 会自动获取你项目的 backend 文件夹路径
        String projectPath = System.getProperty("user.dir");

        // 最终路径变成: C:\Users\...\backend\diet-uploads\
        File dir = new File(projectPath, uploadPath);

        if (!dir.exists()) {
            dir.mkdirs(); // 自动创建文件夹
        }

        try {
            // 3. 保存文件
            // 注意：这里要用 new File(dir, newFileName) 来组合路径
            File dest = new File(dir, newFileName);
            file.transferTo(dest);

            // 4. 返回 URL
            String url = accessPath + newFileName;
            return Result.success(url);

        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }
}