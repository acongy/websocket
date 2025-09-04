package com.acong.websocket.controller;

import com.acong.websocket.image.ImageService;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author ACong
 * @since 2025/8/22 9:52
 */
@RestController
@RequestMapping("/api/image")
public class ImageController {

    private static final Path UPLOAD_DIR = Paths.get("uploads");
    private static final Path COMPRESS_DIR = Paths.get("uploads_compressed");

    @PostConstruct
    public void init() throws IOException {
        if (Files.exists(UPLOAD_DIR)) {
            FileSystemUtils.deleteRecursively(UPLOAD_DIR);
        }
        if (Files.exists(COMPRESS_DIR)) {
            FileSystemUtils.deleteRecursively(COMPRESS_DIR);
        }
        Files.createDirectories(UPLOAD_DIR);
        Files.createDirectories(COMPRESS_DIR);
    }

    /**
     * 上传
     *
     * @param files
     * @param quality
     * @return
     * @throws IOException
     */
    @PostMapping("/upload")
    public String uploadFiles(@RequestParam("files") List<MultipartFile> files,
                              @RequestParam("quality") float quality) throws IOException {

        for (MultipartFile file : files) {
            // 使用前端的 webkitRelativePath 保留目录
            String relativePath = file.getOriginalFilename().replace("/", File.separator).replace("\\", File.separator);
            Path dest = UPLOAD_DIR.resolve(relativePath).normalize();

            //  确保父目录存在
            Files.createDirectories(dest.getParent());

            //  用 Files.copy 替代 transferTo
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 执行压缩
        ImageService.scanAndCompress(UPLOAD_DIR.toFile(), UPLOAD_DIR.toFile(), COMPRESS_DIR.toFile(), quality);

        return "上传并压缩完成，质量=" + quality;
    }


    /**
     * 下载压缩包
     */
    @GetMapping("/download/zip")
    public void downloadZip(HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"compressed.zip\"");

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            Files.walk(COMPRESS_DIR).filter(Files::isRegularFile).forEach(path -> {
                try {
                    zos.putNextEntry(new ZipEntry(COMPRESS_DIR.relativize(path).toString()));
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * 下载单个文件
     */
    @GetMapping("/download/file")
    public ResponseEntity<byte[]> downloadFile(@RequestParam String path) throws IOException {
        Path filePath = COMPRESS_DIR.resolve(path).normalize();
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = Files.readAllBytes(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePath.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }
}