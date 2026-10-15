package com.rhs.rag.service.loader;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public String upload(Long kbId, MultipartFile file){
        String objectPath = String.format("kb/%d/%s-%s",
                kbId, UUID.randomUUID().toString().substring(0,8),file.getOriginalFilename());

        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .stream(file.getInputStream(), file.getSize(),-1)
                    .contentType(file.getContentType())
                    .build());
            log.info("[MINIO] 上传成功，path={}",objectPath);
            return objectPath;
        }catch (Exception e){
            log.error("[MINIO] 上传失败，path={},error = {}",objectPath,e.getMessage(),e);
            throw new RuntimeException("文件上传失败" + e.getMessage(),e);
        }

    }

    private void ensureBucketExists() throws Exception{
        boolean bool = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucket).build());
        if (!bool){
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucket).build());
            log.info("[MINIO] bucket已创建,{}",bucket);
        }
    }

    public byte[] download(String objectPath) {
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build());
            return stream.readAllBytes();
        }catch (Exception e){
            log.error("[MINIO] 下载失败，path={},error={}",objectPath,e.getMessage(),e);
            throw new RuntimeException("文件下载失败");
        }
    }

    /**
     * 删除文件
     * @param objectPath
     */
    public void delete(String objectPath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectPath)
                    .build());
            log.info("[MINIO] 删除成功，path={}",objectPath);
        }catch (Exception e){
            log.warn("[MINIO] 删除失败（可能已不存在）path={},error={}",objectPath,e.getMessage());
        }
    }
}