package com.moodcopilot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Repairs legacy same-key candidate branches after the schema is upgraded. */
@Component
public class MemoryCandidateRepairRunner {
    private static final Logger log = LoggerFactory.getLogger(MemoryCandidateRepairRunner.class);

    private final MemoryOrchestrator memoryOrchestrator;

    public MemoryCandidateRepairRunner(MemoryOrchestrator memoryOrchestrator) {
        this.memoryOrchestrator = memoryOrchestrator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void repairPendingCandidates() {
        try {
            // Includes pending branches that collide with an already approved candidate.
            int merged = memoryOrchestrator.repairPendingCandidates();
            if (merged > 0) log.info("候选记忆历史重复修复完成，合并数量={}", merged);
        } catch (Exception e) {
            log.warn("候选记忆历史重复修复失败，后续读取或抽取时将再次尝试", e);
        }
    }
}
