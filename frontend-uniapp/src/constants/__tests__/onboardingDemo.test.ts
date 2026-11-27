import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ONBOARDING_DEMO_COPY,
  ONBOARDING_DEMO_STEPS,
  ONBOARDING_DEMO_STORY,
  createInitialDemoSessionState,
} from '../onboardingDemo';

describe('shared onboarding demo sandbox data & isolation', () => {
  const requestSpy = vi.fn();

  beforeEach(() => {
    requestSpy.mockReset();
    vi.stubGlobal('uni', {
      request: requestSpy,
    });
  });

  it('defines the fixed 摄影比赛 story with 4 ordered steps and explicit demo disclaimers', () => {
    expect(ONBOARDING_DEMO_STORY.storyTitle).toBe('摄影比赛');
    expect(ONBOARDING_DEMO_STEPS.map((s) => s.id)).toEqual([
      'diary',
      'analysis',
      'memory',
      'chat',
    ]);
    expect(ONBOARDING_DEMO_STORY.bannerText).toContain('不会写入');
    expect(ONBOARDING_DEMO_STORY.chatDisclaimer).toContain('预设演示回复');
    expect(ONBOARDING_DEMO_STORY.chatDisclaimer).toContain('非实时 AI');
    expect(ONBOARDING_DEMO_COPY.startRealWriteLabel).toBe('开始真实记录');
  });

  it('ensures all memory evidence and chat citations are verbatim substrings of diary content and attributeKeys are Chinese', () => {
    const diaryContent = ONBOARDING_DEMO_STORY.diary.content;
    const chineseKeyPattern = /^[\u4e00-\u9fa5]{2,12}$/;

    expect(ONBOARDING_DEMO_STORY.memories.length).toBeGreaterThanOrEqual(2);
    expect(ONBOARDING_DEMO_STORY.memories.some((m) => m.kind === 'candidate')).toBe(true);

    for (const memory of ONBOARDING_DEMO_STORY.memories) {
      expect(chineseKeyPattern.test(memory.attributeKey)).toBe(true);
      expect(memory.evidence.length).toBeGreaterThan(0);
      expect(diaryContent.includes(memory.evidence)).toBe(true);
    }

    expect(ONBOARDING_DEMO_STORY.chatPresets.length).toBeGreaterThanOrEqual(2);
    for (const preset of ONBOARDING_DEMO_STORY.chatPresets) {
      expect(preset.reply.length).toBeGreaterThan(0);
      for (const citation of preset.citations) {
        expect(diaryContent.includes(citation.excerpt)).toBe(true);
      }
    }
  });

  it('creates isolated local demo session state that resets cleanly with zero network calls', () => {
    const first = createInitialDemoSessionState();
    first.stepIndex = 3;
    first.expandedEvidenceIds.push('demo-mem-hobby');
    first.candidateDecision = 'approved';
    first.selectedChatPresetIds.push('contest-nerves');
    first.activeCitationId = 'cite-diary-1';

    const replayed = createInitialDemoSessionState();
    expect(replayed).toEqual({
      stepIndex: 0,
      expandedEvidenceIds: [],
      candidateDecision: 'pending',
      selectedChatPresetIds: [],
      activeCitationId: null,
    });
    expect(requestSpy).not.toHaveBeenCalled();
  });
});
