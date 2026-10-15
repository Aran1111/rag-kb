package com.rhs.rag.service.loader;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rhs.rag.entity.DocChunk;
import com.rhs.rag.entity.IndexTask;
import com.rhs.rag.entity.KbDocument;
import com.rhs.rag.enums.DocumentStatus;
import com.rhs.rag.enums.TaskStatus;
import com.rhs.rag.mapper.DocChunkMapper;
import com.rhs.rag.mapper.IndexTaskMapper;
import com.rhs.rag.mapper.KbDocumentMapper;
import com.rhs.rag.result.ChunkResult;
import com.rhs.rag.result.ParseResult;
import com.rhs.rag.security.UserContext;
import com.rhs.rag.service.embedding.EmbeddingService;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexService {

    private static final String TASK_TYPE_FROM_MINIO = "INDEX_FROM_MINIO";
    private static final String TASK_TYPE_FROM_TEXT = "INDEX_FROM_TEXT";

    private final KbDocumentMapper kbDocumentMapper;
    private final DocChunkMapper docChunkMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final DocumentLoaderService loaderService;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final MinioStorageService minioStorageService;
    private final IndexTaskLauncher taskLauncher;   // 后面会讲为什么要抽出这个类

    private final ScheduledExecutorService retryScheduler =
            Executors.newSingleThreadScheduledExecutor(r ->{
                Thread t = new Thread(r, "index-retry-scheduler");
                t.setDaemon(true);
                return t;
            });

    @PreDestroy
    void shutDownRetryScheduler(){
        retryScheduler.shutdown();
    }

    /**
     * 提交索引任务（支持直接传入文本，测试时跳过 MinIO）。
     */
    public void submitIndexTask(Long docId, String textContent) {
        IndexTask task = new IndexTask();
        task.setDocId(docId);
        task.setTaskType(TASK_TYPE_FROM_TEXT);
//        taskRepository.save(task);
        indexTaskMapper.insert(task);

        // 通过 taskLauncher 触发异步（不能直接 this.executeWithText，会绕过代理）
        taskLauncher.launchWithText(task.getId(), docId, textContent, UserContext.getUserId(),UserContext.getDepartmentId(),UserContext.getRole());

    }

    /**
     * 提交索引任务（生产模式，从 MinIO 读取文件）。
     */
    public void submitIndexTask(Long docId) {
        IndexTask task = new IndexTask();
        task.setDocId(docId);
        task.setTaskType(TASK_TYPE_FROM_MINIO);
//        taskRepository.save(task);
        indexTaskMapper.insert(task);

        taskLauncher.launchFromMinio(task.getId(), docId,UserContext.getUserId(),UserContext.getDepartmentId(),UserContext.getRole());
    }

    /**
     * 从 MinIO 读取文件并执行索引（由 IndexTaskLauncher 异步调用）。
     */
    @Transactional
    public void executeFromMinio(Long taskId, Long docId) {
//        KbDocument doc = kbDocumentMapper.findById(docId).orElseThrow();
        // 1、取文档元数据-失败说明docId已经被删，不重试
        KbDocument doc;
        try {
            doc = Optional.ofNullable(kbDocumentMapper.selectById(docId)).orElseThrow(() -> new RuntimeException("文档不存在：docId" + docId));
        }catch (Exception e){
            markFailed(taskId,docId,e.getMessage());
            return;
        }

        // 2、从MINIO下载文件失败，肯能网络IO问题，重试
        byte[] fileBytes;
        try {
            fileBytes = minioStorageService.download(doc.getMinioPath());
        } catch (Exception e) {
            markFailed(taskId, docId, "从MinIO读取文件失败：" + e.getMessage());
            retryIfPossible(taskId,docId);
            return;
        }

        // 3、索引失败可能文件损坏或者embedding失败抖动，重试
        try {
            ParseResult parseResult = loaderService.load(
                    new ByteArrayInputStream(fileBytes), doc.getFileName());
            doIndex(taskId, docId, doc, parseResult);
        } catch (Exception e) {
            markFailed(taskId, docId, "文件解析或索引失败" + e.getMessage());
            retryIfPossible(taskId,docId);
        }
    }

    /**
     * 执行索引（直接使用文本内容，由 IndexTaskLauncher 异步调用）。
     */
    public void executeWithText(Long taskId, Long docId, String textContent) {
//        KbDocument doc = kbDocumentMapper.findById(docId).orElseThrow();
        KbDocument doc = Optional.ofNullable(kbDocumentMapper.selectById(docId)).orElseThrow();
        ParseResult parseResult = ParseResult.builder()
                .success(true)
                .pages(List.of(ParseResult.PageContent.builder()
                        .pageNum(1)
                        .text(textContent)
                        .build()))
                .totalPages(1)
                .build();
        doIndex(taskId, docId, doc, parseResult);
    }

    /**
     * 核心索引逻辑：解析 → 分块 → Embedding → 写库。
     *
     * 设计思路：整个管道是"先算后删再写"的顺序，而不是"先删再算再写"。
     * 这样设计是为了保证可用性——如果 Embedding 阶段调 API 失败了，
     * 旧版本的分块还在，用户查询不受影响。
     * 只有当新向量算完、确定能写入了，才去删旧数据。
     */

    private void doIndex(Long taskId, Long docId, KbDocument doc, ParseResult parseResult) {
        updateTaskStatus(taskId, TaskStatus.RUNNING);
        updateDocStatus(docId, DocumentStatus.PROCESSING);

        try {
            if (!parseResult.isSuccess()) {
                throw new RuntimeException("文档解析失败：" + parseResult.getErrormsg());
            }

            // 第一步：分块
            List<ChunkResult> chunks = chunkService.chunk(parseResult);
            if (chunks.isEmpty()) {
                throw new RuntimeException("分块结果为空，文档可能无有效文本内容");
            }
            log.info("[IndexService] docId={}，分块完成，共{}块", docId, chunks.size());

            // 第二步：批量 Embedding
            List<String> texts = chunks.stream().map(ChunkResult::getContent).toList();
            List<float[]> embeddings = embeddingService.embedBatch(texts);

            // 第三步：
//            chunkRepository.deleteByDocIdAndDocVersionLessThan(docId, doc.getVersion());
            int newVersion = (doc.getVersion() == null ? 1 : doc.getVersion() + 1);
            doc.setVersion(newVersion);
            doc.setStatus(DocumentStatus.PROCESSING);
            kbDocumentMapper.updateById(doc);

            // 第四步：批量写入数据库
            List<DocChunk> docChunks = new ArrayList<>();
            int totalTokens = 0;
            for (int i = 0; i < chunks.size(); i++) {
                ChunkResult chunk = chunks.get(i);
                DocChunk docChunk = new DocChunk();
                docChunk.setDocId(docId);
                docChunk.setKbId(doc.getKbId());
                docChunk.setChunkIndex(chunk.getChunkIndex());
                docChunk.setContent(chunk.getContent());
                docChunk.setEmbedding(embeddings.get(i));
                docChunk.setPageNum(chunk.getPageNum());
                docChunk.setSectionTitle(chunk.getSectionTitle());
                docChunk.setTokenCount(chunk.getEstimatedTokens());
                docChunk.setDocVersion(newVersion);
                docChunks.add(docChunk);
                totalTokens += chunk.getEstimatedTokens();
            }

            batchInsertChunks(docChunks);

            // 删除旧版本分块（放在 Embedding入口 成功之后，保证有新数据才删旧数据）
            docChunkMapper.delete(new LambdaQueryWrapper<DocChunk>()
                    .eq(docId != null,DocChunk::getDocId, docId)
                    .lt(DocChunk::getDocVersion, newVersion));

            // 第五步：更新文档状态
            doc.setStatus(DocumentStatus.DONE);
            doc.setChunkCount(chunks.size());
            doc.setTokenCount(totalTokens);
            doc.setIndexedAt(LocalDateTime.now());
//            kbDocumentMapper.save(doc);
            kbDocumentMapper.updateById(doc);

            updateTaskStatus(taskId, TaskStatus.DONE);

            log.info("[IndexService] 索引完成：docId={}，chunks={}，tokens={}",
                    docId, chunks.size(), totalTokens);

        } catch (Exception e) {
            log.error("[IndexService] 索引失败：docId={}，error={}", docId, e.getMessage(), e);
            markFailed(taskId, docId, e.getMessage());
            retryIfPossible(taskId, docId);
        }
    }

    /**
     * 分批写入，每批 50 条。
     * 为什么不直接 saveAll 一把梭？因为一份大文档可能有几百个 chunk，
     * 单次 INSERT 几百行对数据库的压力很大（长事务 + 大量 WAL 日志），
     * 分批写可以减少单次事务大小，也方便观察写入进度。
     */
    private void batchInsertChunks(List<DocChunk> chunks) {
        int batchSize = 50;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<DocChunk> batch = chunks.subList(i, Math.min(i + batchSize, chunks.size()));
//            chunkRepository.saveAll(batch);
            docChunkMapper.insertBatch(batch);
            log.debug("[IndexService] 写入批次 {}/{}",
                    i / batchSize + 1, (chunks.size() + batchSize - 1) / batchSize);
        }
    }

    private void markFailed(Long taskId, Long docId, String errorMsg) {
//        IndexTask task = taskRepository.findById(taskId).orElseThrow();
        IndexTask task = Optional.ofNullable(indexTaskMapper.selectById(taskId)).orElseThrow();
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMsg(errorMsg);
        task.setFinishedAt(LocalDateTime.now());
//        taskRepository.save(task);
        indexTaskMapper.updateById(task);

//        kbDocumentMapper.findById(docId).ifPresent(doc -> {
//            doc.setStatus(KbDocument.DocumentStatus.FAILED);
//            doc.setErrorMsg(errorMsg);
//            kbDocumentMapper.save(doc);
//        });
        KbDocument kbDocument = kbDocumentMapper.selectById(docId);
        if (kbDocument != null){
            kbDocument.setStatus(DocumentStatus.FAILED);
            kbDocument.setErrorMsg(errorMsg);
            kbDocumentMapper.updateById(kbDocument);
        }
    }

    private void retryIfPossible(Long taskId, Long docId) {
//        IndexTask task = taskRepository.findById(taskId).orElseThrow();
        IndexTask task = Optional.ofNullable(indexTaskMapper.selectById(taskId)).orElseThrow();
        if (task.canRetry()) {
            task.setRetryCount(task.getRetryCount() + 1);
            task.setStatus(TaskStatus.PENDING);
//            taskRepository.save(task);
            indexTaskMapper.updateById(task);
            log.info("[IndexService] 任务将重试：taskId={}，retryCount={}", taskId, task.getRetryCount());
            // 延迟重试（指数退避：1s, 2s, 4s）
            scheduleRetry(taskId, docId, task.getRetryCount());
        }
    }

    /**
     * 延迟重试（指数退避）。
     * 这里直接用 Thread.sleep 而不是 ScheduledExecutorService，
     * 因为当前已经在 indexTaskExecutor 的异步线程里了，sleep 不会阻塞主线程。
     * 用 ScheduledExecutorService 反而要引入额外的线程池管理，过度设计了。
     */
    protected void scheduleRetry(Long taskId, Long docId, int retryCount) {
        IndexTask task = indexTaskMapper.selectById(taskId);
        if (task == null){
            log.warn("重试找不到任务，taskId={}",taskId);
            return;
        }

        if (TASK_TYPE_FROM_TEXT.equals(task.getTaskType())){
            log.warn("文本任务不支持重试 taskId = {}",taskId);
            return;
        }

        // 业务线程先拿用户信息
        Long userId = UserContext.getUserId();
        String departmentId = UserContext.getDepartmentId();
        String role = UserContext.getRole();
        long delaySeconds = (long) Math.pow(2, retryCount - 1);

        retryScheduler.schedule(() -> {
            try {
                taskLauncher.launchFromMinio(taskId,docId,userId,departmentId,role);
            }catch (Exception e){
                log.error("[IndexService] 投递任务失败：taskId={},err={}",taskId,e.getMessage(), e);
            }
        },delaySeconds, TimeUnit.SECONDS);
    }

    private void updateTaskStatus(Long taskId, TaskStatus status) {
//        taskRepository.findById(taskId).ifPresent(t -> {
//            t.setStatus(status);
//            if (status == IndexTask.TaskStatus.RUNNING) t.setStartedAt(LocalDateTime.now());
//            if (status == IndexTask.TaskStatus.DONE)    t.setFinishedAt(LocalDateTime.now());
//            taskRepository.save(t);
//        });

        IndexTask task = indexTaskMapper.selectById(taskId);
        if (task != null){
            task.setStatus(status);
            if (status == TaskStatus.RUNNING) task.setStartedAt(LocalDateTime.now());
            if (status == TaskStatus.DONE)    task.setFinishedAt(LocalDateTime.now());
            indexTaskMapper.updateById(task);
        }
    }

    private void updateDocStatus(Long docId, DocumentStatus status) {
//        kbDocumentMapper.findById(docId).ifPresent(d -> {
//            d.setStatus(status);
//            kbDocumentMapper.save(d);
//        });

        KbDocument kbDocument = kbDocumentMapper.selectById(docId);
        if (kbDocument != null) {
            kbDocument.setStatus(status);
            kbDocumentMapper.updateById(kbDocument);
        }
    }
}