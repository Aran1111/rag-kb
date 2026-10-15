package com.rhs.rag.result;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ParseResult {

    /** 是否解析成功 */
    private boolean success;

    /** 错误信息(success=false 时有值) */
    private String errormsg;

    /** 解析结果 */
    private List<PageContent> pages;

    /** 总页数 */
    private int totalPages;

    /** 文档标题（如果能识别）*/
    private String title;


    @Data
    @Builder
    public static class PageContent {
        /** 页码 */
        private int pageNum;
        /** 每页纯文本内容 */
        private String text;
        /** 本页识别到的章节标题 可为空 */
        private String sectionTitle;
    }

    /**
     * 解析失败调用
     * @param errormsg
     * @return
     */
    public static ParseResult failure(String errormsg){
        return ParseResult.builder()
                .errormsg(errormsg)
                .pages(List.of())
                .totalPages(0)
                .build();
    }

    public String getFullText(){
        if (pages == null) {
            return "";
        }
        return pages.stream()
                .map(PageContent::getText)
                .filter(t -> t != null && !t.isBlank())
                .reduce("", (a, b) -> a + "\n\n" + b)
                .strip();
    }
}
