package com.rhs.rag.service;


import com.rhs.rag.entity.KbDocument;
import com.rhs.rag.enums.DocumentStatus;
import com.rhs.rag.mapper.KbDocumentMapper;
import com.rhs.rag.service.loader.IndexService;
import com.rhs.rag.service.loader.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档更新服务，处理"替换文档"场景：
 * 重新上传新版本文件，替换旧版本，触发重建索引。
 * <p>
 * 关键设计：
 * 1. 数据库更新和异步索引分开——先提交事务，再触发异步任务
 * 2. doc_version 递增，查询时按最新版本的 chunk 检索
 * 3. 新版本索引期间，旧版本 chunk 仍然可以被检索（不中断）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentUpdateService {

    private final KbDocumentMapper kbDocumentMapper;
    private final DocumentTxService documentTxService;
    private final MinioStorageService minioService;
    private final IndexService indexService;

    /**
     * 替换文档内容，保持文档 ID 不变。
     * 注意：不加 @Transactional，让 updateDocumentRecord 的事务先提交，
     * 再触发异步索引，确保异步线程读到的是最新的 minio_path。
     */
    public KbDocument replaceDocument(Long docId, MultipartFile newFile) {

        String oldMinioPath = documentTxService.updateDocumentRecord(docId, newFile);

        indexService.submitIndexTask(docId);

        minioService.delete(oldMinioPath);

        return kbDocumentMapper.selectById(docId);
    }

    /**
     * forceReindex 的外层调用，事务提交后再触发异步索引。
     */
    public void forceReindexAndSubmit(Long docId) {
        documentTxService.forceReindex(docId);
        indexService.submitIndexTask(docId);
        log.info("[DocumentUpdate] 强制重建索引已提交：docId={}", docId);
    }
}