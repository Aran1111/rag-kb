package com.rhs.rag.service.loader.impl.parser;


import com.rhs.rag.result.ParseResult;
import com.rhs.rag.service.loader.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class PdfParser implements DocumentParser {

    public static final Pattern HEADING_PATTERN =
            Pattern.compile("^(第[一二三四五六七八九十百\\d]+[章节]|[一二三四五六七八九十]+、|\\d+\\.)\\s*.+");


    @Override
    public String supportType() {
        return "PDF";
    }

    @Override
    public ParseResult parse(InputStream input, String filename) {
        try {
            // 1、拿到pdf文件
            PDDocument document = Loader.loadPDF(input.readAllBytes());
            // 2、获取pdf总页数
            int total = document.getNumberOfPages();
            // 装pdf每页的容器
            ArrayList<ParseResult.PageContent> pages = new ArrayList<>();
            // 3、获取文档分割器
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            // 4、遍历每页
            for (int pageNum = 1; pageNum <= total; pageNum++) {
                try {
                    // 从这页开始
                    stripper.setStartPage(pageNum);
                    // 到这页结束
                    stripper.setEndPage(pageNum);
                    // 获取这页的内容（包含各种换行符等）
                    String text = stripper.getText(document);
                    // 过滤得到纯文本
                    text = cleanText(text);

                    if (text.isBlank()) {
                        log.debug("[PDF解析] 第{}页内容为空，可能是图片页，跳过", pageNum);
                        continue;
                    }
                    pages.add(ParseResult.PageContent.builder()
                            .pageNum(pageNum)
                            .text(text)
                            .sectionTitle(detectHeading(text))
                            .build());
                }catch (Exception e){
                    log.warn("[PDF解析]: {} 文件第 {} 页解析失败:", filename,e.getMessage());
                }
            }
            if (pages.isEmpty()) {
                return ParseResult.failure("PDF 解析后无有效文本内容，可能是纯图片 PDF，需要 OCR 处理");
            }

            log.info("[PDF解析] 文件={}，总页数={}，有效页数={}", filename, total, pages.size());

            return ParseResult.builder()
                    .success(true)
                    .title(extractTitle(pages))
                    .pages(pages)
                    .totalPages(total)
                    .build();
        } catch (Exception e) {
            log.error("[PDF]解析失败。文件={}，错误={}",filename,e.getMessage());
            return ParseResult.failure("PDF 解析失败：" + e.getMessage());
        }
    }

    /**
     * 清理 PDF 解析出的文本：去除多余空白、修复换行
     */
    private String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("[ \\t]+", " ")          // 多个空格合并
                .replaceAll("\\n{3,}", "\n\n")        // 多个空行合并为两个
                .strip();
    }

    /**
     * 从文本开头几行识别章节标题
     */
    private String detectHeading(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            String line = lines[i].strip();
            if (line.length() > 2 && line.length() < 50) {
                Matcher m = HEADING_PATTERN.matcher(line);
                if (m.matches()) {
                    return line;
                }
            }
        }
        return null;
    }

    /**
     * 取第一页文本的第一行作为文档标题
     */
    private String extractTitle(List<ParseResult.PageContent> pages) {
        if (pages.isEmpty()) {
            return null;
        }
        String firstPageText = pages.get(0).getText();
        String[] lines = firstPageText.split("\n");
        for (String line : lines) {
            line = line.strip();
            if (!line.isBlank() && line.length() < 100) {
                return line;
            }
        }
        return null;
    }
}
