package com.rhs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rhs.rag.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /** 删除文档的旧版本分块（重建索引时使用） */
    void deleteByDocIdAndDocVersionLessThan(@Param("docId") Long docId,
                                            @Param("version") Integer version);

}
