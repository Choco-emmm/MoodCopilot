package com.moodcopilot.onboarding;

import com.moodcopilot.diary.DiaryCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OnboardingBusinessEventListener {

    private static final Logger log = LoggerFactory.getLogger(OnboardingBusinessEventListener.class);

    private final OnboardingService onboardingService;

    public OnboardingBusinessEventListener(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDiaryCreated(DiaryCreatedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        try {
            onboardingService.recordBusinessTaskCompleted(event.userId(), GuideKey.FIRST_DIARY);
        } catch (Exception e) {
            log.warn("记录首次日记引导进度失败 userId={} errorType={}",
                    event.userId(), e.getClass().getSimpleName());
        }
    }
}
