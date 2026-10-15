package com.rhs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rhs.rag.entity.KbDocument;
import com.rhs.rag.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
