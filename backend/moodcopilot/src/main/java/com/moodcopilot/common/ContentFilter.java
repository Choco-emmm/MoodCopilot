package com.moodcopilot.common;

import java.util.Set;
import java.util.regex.Pattern;

public class ContentFilter {

    private static final Set<String> BLOCKED = Set.of(
            "赌博", "赌场", "彩票", "刷单", "兼职日结", "加微信",
            "色情", "裸聊", "约炮", "小姐", "上门服务",
            "广告", "代开发票", "办证", "高利贷", "网贷"
    );

    private static final Pattern CLEAN = Pattern.compile("[\\s\\p{P}]+");

    public static String filter(String text) {
        if (text == null || text.isBlank()) return text;
        String cleaned = CLEAN.matcher(text).replaceAll("");
        for (String word : BLOCKED) {
            if (cleaned.contains(word)) {
                return "[内容违规，已被过滤]";
            }
        }
        return text;
    }
}
