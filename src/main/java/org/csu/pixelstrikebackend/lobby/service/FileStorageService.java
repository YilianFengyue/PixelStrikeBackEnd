package org.csu.pixelstrikebackend.lobby.service;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService() {
        // 定义文件上传的根目录
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    public Mono<String> storeFile(FilePart file) { // 返回类型修改为 Mono<String>
        String originalFileName = StringUtils.cleanPath(file.filename());

        try {
            if(originalFileName.contains("..")) {
                return Mono.error(new RuntimeException("Sorry! Filename contains invalid path sequence " + originalFileName));
            }

            String fileExtension = "";
            try {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            } catch (Exception e) {
                // ignore
            }
            String newFileName = UUID.randomUUID().toString() + fileExtension;

            Path targetLocation = this.fileStorageLocation.resolve(newFileName);

            // transferTo 本身返回一个 Mono<Void>，表示操作完成的信号
            // 我们用 then(Mono.just(newFileName)) 来在操作成功后返回新的文件名
            return file.transferTo(targetLocation).then(Mono.just(newFileName));

        } catch (Exception ex) {
            return Mono.error(new RuntimeException("Could not store file " + originalFileName + ". Please try again!", ex));
        }
    }
}