package com.rhs.rag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RAG查询响应")
public class RagResponse {

    @Schema(description = "AI生成的回答", example = "根据公司规定，员工年假天数为...")
    private String answer;
    
    @Schema(description = "引用的来源文档列表")
    private List<Source> sources;
    
    @Schema(description = "查询耗时（毫秒）", example = "1234")
    private int latencyMs;
    
    @Schema(description = "是否未找到相关内容", example = "false")
    private boolean notFound;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "来源文档信息")
    public static class Source {
        
        @Schema(description = "分块ID", example = "1001")
        private Long chunkId;
        
        @Schema(description = "文档ID", example = "1")
        private Long docId;
        
        @Schema(description = "页码", example = "5")
        private Integer pageNum;
        
        @Schema(description = "章节标题", example = "年假政策")
        private String sectionTitle;
        
        @Schema(description = "相关段落摘要（前200字）", example = "根据公司规定，员工入职满一年后可享受带薪年假...")
        private String excerpt;
        
        @Schema(description = "相关性分数", example = "0.85")
        private double score;
        
        @Schema(description = "文档名称", example = "员工手册v2.3.pdf")
        private String docName;
    }

    public static RagResponse notFound() {
        return RagResponse.builder()
                .answer("在知识库中未找到与该问题相关的内容。建议您：\n" +
                        "1. 确认问题是否属于该知识库的覆盖范围\n" +
                        "2. 尝试更换关键词提问\n" +
                        "3. 联系相关部门获取准确信息")
                .sources(List.of())
                .notFound(true)
                .build();
    }
}