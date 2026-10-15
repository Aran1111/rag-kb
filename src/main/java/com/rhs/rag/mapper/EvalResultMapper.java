package com.rhs.rag.mapper;



import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rhs.rag.dto.EvalReport;
import com.rhs.rag.entity.EvalResult;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EvalResultMapper extends BaseMapper<EvalResult> {

    List<EvalReport> aggregateByVersion(@Param("kbId") Long kbId);
}