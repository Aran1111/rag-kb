package com.rhs.rag.service;

import com.rhs.rag.entity.KbDocument;
import com.rhs.rag.enums.DocumentStatus;
import com.rhs.rag.mapper.KbDocumentMapper;
import com.rhs.rag.service.loader.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentTxService {
    private final KbDocumentMapper kbDocumentMapper;
    private final MinioStorageService minioService;

    /**
     * 第一步：更新数据库记录 + 上传新文件（事务内）。
     * 返回旧文件路径，供第二步删除。
     */
    @Transactional
    public String updateDocumentRecord(Long docId, MultipartFile newFile) {
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null){
            throw new RuntimeException("文档不存在：" + docId);
        }

        String oldMinioPath = doc.getMinioPath();

        String newMinioPath = minioService.upload(doc.getKbId(), newFile);

        doc.setFileName(newFile.getOriginalFilename());
        doc.setFileSize(newFile.getSize());
        doc.setMinioPath(newMinioPath);
        doc.setVersion(doc.getVersion() + 1);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setErrorMsg(null);
        doc.setChunkCount(null);
        doc.setTokenCount(null);
        doc.setIndexedAt(null);
        kbDocumentMapper.updateById(doc);

        log.info("[DocumentUpdate] 文档记录更新：docId={}，newVersion={}，newFile={}",
                docId, doc.getVersion(), newFile.getOriginalFilename());
        return oldMinioPath;
    }

    /**
     * 强制重建索引（文档内容未变，但解析参数或分块策略更新了）。
     */
    @Transactional
    public void forceReindex(Long docId) {
        KbDocument doc = kbDocumentMapper.selectById(docId);
        if (doc == null && Boolean.TRUE.equals(doc.getIsDeleted())){
            throw new RuntimeException("文档不存在：" + docId);
        }

        doc.setVersion(doc.getVersion() + 1);
        doc.setStatus(DocumentStatus.PENDING);
        doc.setErrorMsg(null);
        doc.setChunkCount(null);
        doc.setTokenCount(null);
        doc.setIndexedAt(null);
        kbDocumentMapper.updateById(doc);
    }
}
