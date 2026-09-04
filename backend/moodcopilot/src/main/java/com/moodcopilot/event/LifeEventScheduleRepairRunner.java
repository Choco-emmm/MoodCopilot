package com.moodcopilot.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Repairs legacy event phase and follow-up defaults after Flyway migrations. */
@Component
public class LifeEventScheduleRepairRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LifeEventScheduleRepairRunner.class);

    private final LifeEventService lifeEventService;

    public LifeEventScheduleRepairRunner(LifeEventService lifeEventService) {
        this.lifeEventService = lifeEventService;
    }

    @Override
    public void run(String... args) {
        try {
            lifeEventService.repairLegacyEventSchedules();
        } catch (Exception e) {
            log.error("历史重要事件回访计划修复失败，后续调度仍将按实时业务时区判断", e);
        }
    }
}
