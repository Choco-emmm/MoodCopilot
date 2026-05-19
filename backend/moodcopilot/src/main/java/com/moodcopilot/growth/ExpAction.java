package com.moodcopilot.growth;

public enum ExpAction {
    CHECKIN(1, 10, "签到"),
    DIARY(2, 20, "日记"),
    CHAT(4, 5, "聊天"),
    COMMENT(5, 3, "回复"),
    LIKE(10, 2, "点赞");

    private final int maxPerDay;
    private final int baseExp;
    private final String label;

    ExpAction(int maxPerDay, int baseExp, String label) {
        this.maxPerDay = maxPerDay;
        this.baseExp = baseExp;
        this.label = label;
    }

    public int getMaxPerDay() {
        return maxPerDay;
    }

    public int getBaseExp() {
        return baseExp;
    }

    public String getLabel() {
        return label;
    }
}
