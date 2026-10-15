package com.rhs.rag.service.loader;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rhs.rag.entity.DocChunk;
import com.rhs.rag.entity.KbDocument;
import com.rhs.rag.enums.DocumentStatus;
import com.rhs.rag.mapper.DocChunkMapper;
import com.rhs.rag.mapper.KbDocumentMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class DataInitTest {

    @Autowired
    private KbDocumentMapper kbDocumentMapper;

    @Autowired
    private DocChunkMapper docChunkMapper;

    @Test
    void verifyTestDataLoaded() {
//        long docCount = documentRepository.countByStatus(KbDocument.DocumentStatus.DONE);
        Long count = kbDocumentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()
                .eq(KbDocument::getStatus, DocumentStatus.DONE));
//        long chunkCount = chunkRepository.count();
        Long count1 = docChunkMapper.selectCount(new LambdaQueryWrapper<DocChunk>());

        assertThat(count).isGreaterThanOrEqualTo(3);
        assertThat(count1).isGreaterThan(10);

        System.out.printf("已索引文档数：%d，分块总数：%d%n", count, count1);
    }
}