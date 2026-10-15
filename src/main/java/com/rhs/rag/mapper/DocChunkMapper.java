package com.rhs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rhs.rag.entity.DocChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocChunkMapper extends BaseMapper<DocChunk> {

    int insertBatch(@Param("list") List<DocChunk> list);

    /**
     * 在指定知识库中，查询与给定向量最相似的 topK 个文档块
     *
     * @param kbId         知识库 ID
     * @param embeddingStr 向量字符串，如 "[0.1,0.2,...]"
     * @param topK         返回条数
     * @return 相似文档块列表
     */
    List<DocChunk> findByVectorSimilarity(
            @Param("kbId") Long kbId,
            @Param("embeddingStr") String embeddingStr,
            @Param("topK") int topK
    );

    /**
     * 全文检索：在指定知识库中，根据 tsquery 查找匹配的文档块
     *
     * @param kbId    知识库 ID
     * @param tsQuery tsquery 字符串，如 "API & 限流 & 策略"
     * @param topK    返回条数
     * @return 匹配的文档块列表
     */
    List<DocChunk> findByFullTextSearch(
            @Param("kbId") Long kbId,
            @Param("tsQuery") String tsQuery,
            @Param("topK") int topK
    );
}
