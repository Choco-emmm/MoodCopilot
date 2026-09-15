package com.moodcopilot.common;

public class TextSnippetUtil {

    public static String generateSnippet(String content, int maxLen) {
        if (content == null || content.isBlank()) {
            return "";
        }

        // 1. Remove HTML tags
        String clean = content.replaceAll("<[^>]*>", "");

        // 2. Remove markdown images and links
        clean = clean.replaceAll("!\\[.*?\\]\\(.*?\\)", "[图片]")
                .replaceAll("\\[.*?\\]\\(.*?\\)", "[链接]")
                .replaceAll("[#*_~`>]", "");

        // 3. Normalize whitespace and newlines
        clean = clean.replaceAll("\\s+", " ").trim();

        int codePointCount = clean.codePointCount(0, clean.length());
        if (codePointCount <= maxLen) {
            return clean;
        }

        // 4. Robust truncation avoiding surrogate pair splitting
        int endIndex = clean.offsetByCodePoints(0, maxLen);
        return clean.substring(0, endIndex) + "...";
    }

    /**
     * 富文本 → 纯文本，**保留段落结构**。
     * <p>
     * 和 {@link #generateSnippet} 的区别：后者把所有空白压成一个空格，适合一行的摘要卡片，
     * 但会把日记的条目与列表黏成一坨（例如「3/5这次烤着吃了」），不能用来给模型看正文。
     * 这里把块级标签的边界转成换行，列表项补上 "- " 前缀。
     */
    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li|h[1-6]|tr|blockquote)>", "\n")
                .replaceAll("(?i)<li[^>]*>", "- ")
                .replaceAll("<[^>]*>", "")
                // 实体解码放在剥标签之后，否则 &lt;script&gt; 会被当成真标签吃掉
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        text = text.replaceAll("[ \\t]+", " ").replaceAll(" *\n *", "\n");
        return text.replaceAll("\\n{3,}", "\n\n").trim();
    }
}
