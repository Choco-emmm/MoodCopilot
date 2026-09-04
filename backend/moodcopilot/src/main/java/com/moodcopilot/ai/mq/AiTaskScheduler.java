package com.moodcopilot.ai.mq;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiTaskScheduler {
    private final AiTaskService taskService;

    public AiTaskScheduler(AiTaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelayString = "${moodcopilot.ai-tasks.dispatch-interval-ms:5000}")
    public void dispatch() { taskService.dispatchDueTasks(); }

    @Scheduled(fixedDelayString = "${moodcopilot.ai-tasks.recovery-interval-ms:30000}")
    public void recover() { taskService.recoverExpiredTasks(); }
}
