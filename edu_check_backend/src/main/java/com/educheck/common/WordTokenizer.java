package com.educheck.common;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 中文分词工具 — 基于 jieba 分词
 * 用于智能问答的关键词提取和匹配
 */
public class WordTokenizer {

    private static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();

    /**
     * 对文本进行分词，返回有意义的词列表（过滤单字和标点）
     */
    public static List<String> segment(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<SegToken> tokens = SEGMENTER.process(text, JiebaSegmenter.SegMode.SEARCH);

        return tokens.stream()
                .map(t -> t.word.trim())
                .filter(w -> w.length() > 1)          // 过滤单字
                .filter(w -> !w.matches("[\\s,，。？?！!、；;：:（）()【】《》\"\"''「」\\d]+")) // 过滤纯标点/数字
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 判断文本是否包含某个词（支持中文精确匹配）
     */
    public static boolean containsWord(String text, String word) {
        if (text == null || word == null) return false;
        return text.toLowerCase().contains(word.toLowerCase());
    }
}
