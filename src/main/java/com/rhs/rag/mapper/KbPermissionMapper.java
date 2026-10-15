package com.rhs.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rhs.rag.entity.KbDocument;
import com.rhs.rag.entity.KbPermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbPermissionMapper extends BaseMapper<KbPermission> {
}
