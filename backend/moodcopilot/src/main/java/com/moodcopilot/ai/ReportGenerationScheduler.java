package com.moodcopilot.ai;

import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationScheduler.class);
    private static final int BATCH_SIZE = 200;

    private final UserMapper userMapper;
    private final DiaryService diaryService;

    public ReportGenerationScheduler(UserMapper userMapper, DiaryService diaryService) {
        this.userMapper = userMapper;
        this.diaryService = diaryService;
    }

    @Scheduled(cron = "0 0 0 ? * MON")
    public void generateWeeklyReports() {
        log.info("开始执行周报预生成任务");
        generateForUsers(userMapper.selectList(null).stream().map(UserEntity::getId).toList(), true);
    }

    @Scheduled(cron = "0 0 0 1 * ?")
    public void generateMonthlyReports() {
        log.info("开始执行月报预生成任务");
        generateForUsers(userMapper.selectList(null).stream().map(UserEntity::getId).toList(), false);
    }

    private void generateForUsers(List<Long> userIds, boolean weekly) {
        int generated = 0;
        for (int i = 0; i < userIds.size(); i += BATCH_SIZE) {
            List<Long> batch = userIds.subList(i, Math.min(i + BATCH_SIZE, userIds.size()));
            for (Long userId : batch) {
                try {
                    if (weekly) {
                        diaryService.generateWeeklyAiSummaryForUser(userId, -1);
                    } else {
                        diaryService.generateMonthlyAiSummaryForUser(userId, -1);
                    }
                    generated++;
                } catch (Exception e) {
                    log.warn("报告预生成失败，userId={}，weekly={}", userId, weekly, e);
                }
            }
        }
        log.info("报告预生成任务完成，weekly={}，generated={}", weekly, generated);
    }
}