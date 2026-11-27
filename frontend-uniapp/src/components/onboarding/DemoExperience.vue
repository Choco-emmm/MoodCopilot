<template>
  <view class="demo-experience">
    <!-- Top Sandbox Disclaimer Banner -->
    <view class="demo-banner">
      <view class="demo-banner-head">
        <text class="demo-badge">{{ story.badge }} · {{ story.storyTitle }}</text>
        <text class="demo-step-counter">{{ currentStep.stepNumber }} / 0{{ steps.length }}</text>
      </view>
      <text class="demo-banner-text">{{ story.bannerText }}</text>
    </view>

    <!-- 4-Step Editorial Stepper -->
    <view class="demo-stepper">
      <view
        v-for="(step, idx) in steps"
        :key="step.id"
        class="step-tab"
        :class="{ active: idx === session.stepIndex, done: idx < session.stepIndex }"
        role="button"
        @click="goToStep(idx)"
      >
        <text class="step-num">{{ step.stepNumber }}</text>
        <text class="step-label">{{ step.title }}</text>
      </view>
    </view>

    <!-- Step Heading -->
    <view class="step-header">
      <text class="step-title">{{ currentStep.title }}</text>
      <text class="step-subtitle">{{ currentStep.subtitle }}</text>
    </view>

    <!-- Step 1: 示例日记 -->
    <view v-if="currentStep.id === 'diary'" class="demo-panel">
      <view class="diary-meta-row">
        <text class="diary-date">{{ story.diary.dateLabel }}</text>
        <text class="meta-pill">{{ story.diary.visibilityLabel }}</text>
        <text class="meta-pill">{{ story.diary.moodLabel }}</text>
      </view>
      <text class="diary-title">{{ story.diary.title }}</text>
      <text class="diary-body">{{ story.diary.content }}</text>

      <view class="diary-attachments">
        <view class="attachment-row">
          <text class="attachment-tag">照片示例</text>
          <text class="attachment-text">{{ story.diary.photoCaption }}</text>
        </view>
        <view class="attachment-row">
          <text class="attachment-tag">音乐示例</text>
          <text class="attachment-text">{{ story.diary.musicCaption }}</text>
        </view>
      </view>

      <text class="demo-footnote">{{ story.disclaimer }}</text>
    </view>

    <!-- Step 2: 示例情绪分析 -->
    <view v-else-if="currentStep.id === 'analysis'" class="demo-panel">
      <view class="analysis-head">
        <text class="section-kicker">{{ story.analysis.demoTag }}</text>
        <text class="analysis-intensity">强度 {{ story.analysis.moodIntensity }} / 5</text>
      </view>
      <text class="analysis-mood">{{ story.analysis.primaryMood }}</text>

      <view class="mood-meter">
        <view
          v-for="level in 5"
          :key="level"
          class="meter-bar"
          :class="{ filled: level <= story.analysis.moodIntensity }"
        />
      </view>

      <view class="tag-row">
        <text
          v-for="tag in story.analysis.emotionTags"
          :key="tag"
          class="emotion-tag"
        >
          {{ tag }}
        </text>
      </view>

      <text class="analysis-summary">{{ story.analysis.summary }}</text>

      <view class="insight-list">
        <view
          v-for="(point, index) in story.analysis.insightPoints"
          :key="index"
          class="insight-row"
        >
          <view class="insight-mark" />
          <text class="insight-text">{{ point }}</text>
        </view>
      </view>

      <view class="gentle-box">
        <text class="gentle-kicker">示例回应</text>
        <text class="gentle-text">{{ story.analysis.gentlePrompt }}</text>
      </view>
    </view>

    <!-- Step 3: 示例记忆与依据 -->
    <view v-else-if="currentStep.id === 'memory'" class="demo-panel">
      <view class="local-note-strip">
        <text class="local-note-text">{{ story.memoryDisclaimer }}</text>
      </view>

      <view class="memory-list">
        <view
          v-for="item in story.memories"
          :key="item.id"
          class="memory-row"
        >
          <view class="memory-row-head">
            <text class="memory-key">{{ item.attributeKey }}</text>
            <text class="memory-status">
              {{ memoryStatusText(item.kind, item.statusLabel) }}
            </text>
          </view>

          <text class="memory-value">{{ item.value }}</text>

          <text v-if="item.candidateNote" class="memory-note">{{ item.candidateNote }}</text>

          <view class="memory-controls">
            <text
              class="evidence-toggle"
              role="button"
              @click="toggleEvidence(item.id)"
            >
              {{ isEvidenceExpanded(item.id) ? copy.hideEvidenceLabel : copy.showEvidenceLabel }}
              {{ isEvidenceExpanded(item.id) ? '⌃' : '⌄' }}
            </text>

            <view v-if="item.kind === 'candidate'" class="candidate-actions">
              <view
                class="candidate-btn candidate-approve"
                :class="{ active: session.candidateDecision === 'approved' }"
                role="button"
                @click="setCandidateDecision('approved')"
              >
                <text class="candidate-btn-text">{{ copy.approveCandidateLabel }}</text>
              </view>
              <view
                class="candidate-btn candidate-reject"
                :class="{ active: session.candidateDecision === 'rejected' }"
                role="button"
                @click="setCandidateDecision('rejected')"
              >
                <text class="candidate-btn-text">{{ copy.rejectCandidateLabel }}</text>
              </view>
            </view>
          </view>

          <view v-if="isEvidenceExpanded(item.id)" class="evidence-quote">
            <text class="evidence-source">{{ item.sourceLabel }}</text>
            <text class="evidence-excerpt">「{{ item.evidence }}」</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Step 4: 预设聊天与引用卡 -->
    <view v-else class="demo-panel">
      <view class="local-note-strip">
        <text class="local-note-text">{{ story.chatDisclaimer }}</text>
      </view>

      <text class="chat-preset-heading">{{ copy.chatPresetHeading }}</text>
      <view class="preset-list">
        <view
          v-for="preset in story.chatPresets"
          :key="preset.id"
          class="preset-item"
          :class="{ selected: session.selectedChatPresetIds.includes(preset.id) }"
          role="button"
          @click="selectChatPreset(preset.id)"
        >
          <text class="preset-prompt">{{ preset.userPrompt }}</text>
          <text class="preset-cue">
            {{ session.selectedChatPresetIds.includes(preset.id) ? '已演示' : '点按查看' }}
          </text>
        </view>
      </view>

      <view v-if="renderedPresets.length" class="chat-thread">
        <view
          v-for="preset in renderedPresets"
          :key="preset.id"
          class="chat-turn"
        >
          <view class="chat-user-msg">
            <text class="chat-role-tag">示例提问</text>
            <text class="chat-user-text">{{ preset.userPrompt }}</text>
          </view>

          <view class="chat-reply-msg">
            <view class="chat-reply-head">
              <text class="chat-demo-badge">预设演示回复 · 非实时 AI</text>
            </view>
            <text class="chat-reply-text">{{ preset.reply }}</text>

            <view v-if="preset.citations.length" class="citation-strip">
              <text class="citation-strip-label">{{ copy.citationToggleLabel }}</text>
              <view class="citation-chips">
                <view
                  v-for="cite in preset.citations"
                  :key="cite.id"
                  class="citation-chip"
                  :class="{ active: session.activeCitationId === cite.id }"
                  role="button"
                  @click="toggleCitation(cite.id)"
                >
                  <text class="citation-chip-text">{{ cite.label }}</text>
                </view>
              </view>
              <view
                v-for="cite in activeCitationsFor(preset.citations)"
                :key="`detail-${cite.id}`"
                class="citation-detail"
              >
                <text class="citation-detail-label">{{ cite.label }}</text>
                <text class="citation-detail-excerpt">「{{ cite.excerpt }}」</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- Bottom Action Bar -->
    <view class="demo-footer">
      <view class="footer-step-row">
        <button
          v-if="session.stepIndex > 0"
          class="demo-btn demo-btn-secondary"
          @click="prevStep"
        >
          {{ copy.prevStepLabel }}
        </button>
        <button
          v-if="session.stepIndex < steps.length - 1"
          class="demo-btn demo-btn-primary"
          @click="nextStep"
        >
          {{ copy.nextStepLabel }}
        </button>
        <button
          v-else
          class="demo-btn demo-btn-primary"
          @click="emit('start-real-write')"
        >
          {{ copy.startRealWriteLabel }}
        </button>
      </view>

      <view class="footer-util-row">
        <text class="footer-link" role="button" @click="replayDemo">{{ copy.replayLabel }}</text>
        <text
          v-if="session.stepIndex < steps.length - 1"
          class="footer-link footer-link-accent"
          role="button"
          @click="emit('start-real-write')"
        >
          {{ copy.startRealWriteLabel }}
        </text>
        <text class="footer-link" role="button" @click="emit('exit')">{{ copy.exitLabel }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  ONBOARDING_DEMO_COPY,
  ONBOARDING_DEMO_STEPS,
  ONBOARDING_DEMO_STORY,
  createInitialDemoSessionState,
} from '@/constants/onboardingDemo';
import type {
  DemoCandidateDecision,
  DemoChatCitation,
  DemoChatPreset,
  DemoMemoryKind,
  DemoSessionState,
} from '@/constants/onboardingDemo';

const emit = defineEmits<{
  (event: 'exit'): void;
  (event: 'start-real-write'): void;
}>();

const story = ONBOARDING_DEMO_STORY;
const steps = ONBOARDING_DEMO_STEPS;
const copy = ONBOARDING_DEMO_COPY;

const session = ref<DemoSessionState>(createInitialDemoSessionState());

const currentStep = computed(() => steps[session.value.stepIndex] || steps[0]);

const renderedPresets = computed<DemoChatPreset[]>(() => {
  const result: DemoChatPreset[] = [];
  for (const id of session.value.selectedChatPresetIds) {
    const match = story.chatPresets.find((item) => item.id === id);
    if (match) {
      result.push(match);
    }
  }
  return result;
});

function goToStep(index: number) {
  if (index < 0 || index >= steps.length) return;
  session.value.stepIndex = index;
}

function prevStep() {
  if (session.value.stepIndex > 0) {
    session.value.stepIndex -= 1;
  }
}

function nextStep() {
  if (session.value.stepIndex < steps.length - 1) {
    session.value.stepIndex += 1;
  }
}

function replayDemo() {
  session.value = createInitialDemoSessionState();
}

function isEvidenceExpanded(memoryId: string): boolean {
  return session.value.expandedEvidenceIds.includes(memoryId);
}

function toggleEvidence(memoryId: string) {
  if (isEvidenceExpanded(memoryId)) {
    session.value.expandedEvidenceIds = session.value.expandedEvidenceIds.filter(
      (id) => id !== memoryId,
    );
  } else {
    session.value.expandedEvidenceIds = [...session.value.expandedEvidenceIds, memoryId];
  }
}

function setCandidateDecision(decision: DemoCandidateDecision) {
  session.value.candidateDecision = decision;
}

function memoryStatusText(kind: DemoMemoryKind, fallbackLabel: string): string {
  if (kind !== 'candidate') return fallbackLabel;
  if (session.value.candidateDecision === 'approved') return copy.candidateApprovedLabel;
  if (session.value.candidateDecision === 'rejected') return copy.candidateRejectedLabel;
  return fallbackLabel;
}

function selectChatPreset(presetId: string) {
  if (!session.value.selectedChatPresetIds.includes(presetId)) {
    session.value.selectedChatPresetIds = [...session.value.selectedChatPresetIds, presetId];
  }
}

function toggleCitation(citationId: string) {
  session.value.activeCitationId =
    session.value.activeCitationId === citationId ? null : citationId;
}

function activeCitationsFor(citations: readonly DemoChatCitation[]): DemoChatCitation[] {
  if (!session.value.activeCitationId) return [];
  return citations.filter((item) => item.id === session.value.activeCitationId);
}
</script>

<style scoped>
.demo-experience {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 24rpx var(--theme-page-padding) calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.demo-banner {
  padding: 22rpx 26rpx;
  background: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
  border: 1rpx solid var(--theme-border);
  border-radius: 20rpx;
}

.demo-banner-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 10rpx;
}

.demo-badge {
  padding: 2rpx 14rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: 999rpx;
  background: var(--theme-surface);
  font-size: 22rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
  color: var(--theme-primary);
}

.demo-step-counter {
  font-size: 22rpx;
  color: var(--theme-text-placeholder);
}

.demo-banner-text {
  display: block;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--theme-text-secondary);
}

.demo-stepper {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12rpx;
}

.step-tab {
  padding: 16rpx 8rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: 16rpx;
  background: var(--theme-surface);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.step-tab.active {
  border-color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 8%, var(--theme-surface));
}

.step-tab.done {
  border-color: color-mix(in oklab, var(--theme-primary) 40%, var(--theme-border));
}

.step-num {
  font-size: 22rpx;
  font-weight: 600;
  color: var(--theme-text-placeholder);
}

.step-tab.active .step-num,
.step-tab.done .step-num {
  color: var(--theme-primary);
}

.step-label {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
  text-align: center;
}

.step-tab.active .step-label {
  color: var(--theme-text-primary);
  font-weight: 600;
}

.step-header {
  padding-bottom: 16rpx;
  border-bottom: 1rpx solid var(--theme-divider);
}

.step-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 36rpx;
  font-weight: 600;
  line-height: 1.4;
  color: var(--theme-text-primary);
}

.step-subtitle {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: var(--theme-text-secondary);
}

.demo-panel {
  padding: 32rpx 28rpx;
  background: var(--theme-surface);
  border: 1rpx solid var(--theme-border);
  border-radius: 24rpx;
  box-shadow: var(--theme-shadow-panel);
}

.diary-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 18rpx;
}

.diary-date {
  font-size: 23rpx;
  font-weight: 600;
  color: var(--theme-primary);
}

.meta-pill {
  padding: 4rpx 12rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  font-size: 20rpx;
  color: var(--theme-text-secondary);
}

.diary-title {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--theme-text-primary);
  margin-bottom: 16rpx;
}

.diary-body {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 28rpx;
  line-height: 1.9;
  color: var(--theme-text-primary);
}

.diary-attachments {
  margin-top: 24rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid var(--theme-border);
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.attachment-row {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
}

.attachment-tag {
  flex-shrink: 0;
  padding: 3rpx 10rpx;
  border-radius: var(--theme-radius-sm);
  background: color-mix(in oklab, var(--theme-primary) 8%, var(--theme-surface));
  font-size: 20rpx;
  color: var(--theme-primary);
}

.attachment-text {
  flex: 1;
  font-size: 23rpx;
  line-height: 1.6;
  color: var(--theme-text-secondary);
}

.demo-footnote {
  display: block;
  margin-top: 22rpx;
  padding-top: 16rpx;
  border-top: 1rpx dashed var(--theme-border);
  font-size: 21rpx;
  line-height: 1.6;
  color: var(--theme-text-placeholder);
}

.analysis-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10rpx;
}

.section-kicker {
  font-size: 21rpx;
  font-weight: 700;
  letter-spacing: 1rpx;
  color: var(--theme-primary);
}

.analysis-intensity {
  font-size: 22rpx;
  color: var(--theme-text-secondary);
}

.analysis-mood {
  display: block;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 34rpx;
  font-weight: 700;
  color: var(--theme-text-primary);
  margin-bottom: 16rpx;
}

.mood-meter {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10rpx;
  margin-bottom: 20rpx;
}

.meter-bar {
  height: 10rpx;
  border-radius: 6rpx;
  background: var(--theme-border);
}

.meter-bar.filled {
  background: var(--theme-primary);
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-bottom: 20rpx;
}

.emotion-tag {
  padding: 4rpx 14rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  font-size: 22rpx;
  color: var(--theme-text-secondary);
}

.analysis-summary {
  display: block;
  font-size: 27rpx;
  line-height: 1.8;
  color: var(--theme-text-primary);
}

.insight-list {
  margin-top: 20rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid var(--theme-border);
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.insight-row {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
}

.insight-mark {
  width: 8rpx;
  height: 8rpx;
  margin-top: 14rpx;
  flex-shrink: 0;
  border-radius: 2rpx;
  background: var(--theme-primary);
}

.insight-text {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.65;
  color: var(--theme-text-secondary);
}

.gentle-box {
  margin-top: 22rpx;
  padding: 18rpx 20rpx;
  border-left: 4rpx solid var(--theme-primary);
  border-radius: var(--theme-radius-sm);
  background: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
}

.gentle-kicker {
  display: block;
  font-size: 20rpx;
  font-weight: 600;
  color: var(--theme-primary);
  margin-bottom: 6rpx;
}

.gentle-text {
  display: block;
  font-size: 24rpx;
  line-height: 1.65;
  color: var(--theme-text-primary);
}

.local-note-strip {
  margin-bottom: 20rpx;
  padding: 14rpx 18rpx;
  border-left: 4rpx solid var(--theme-primary);
  border-radius: var(--theme-radius-sm);
  background: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
}

.local-note-text {
  display: block;
  font-size: 22rpx;
  line-height: 1.55;
  color: var(--theme-text-secondary);
}

.memory-list {
  display: flex;
  flex-direction: column;
}

.memory-row {
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--theme-border);
}

.memory-row:last-child {
  padding-bottom: 4rpx;
  border-bottom: none;
}

.memory-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 8rpx;
}

.memory-key {
  font-size: 24rpx;
  font-weight: 700;
  color: var(--theme-primary);
}

.memory-status {
  font-size: 20rpx;
  color: var(--theme-text-placeholder);
}

.memory-value {
  display: block;
  font-size: 27rpx;
  line-height: 1.65;
  color: var(--theme-text-primary);
}

.memory-note {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  line-height: 1.55;
  color: var(--theme-text-secondary);
}

.memory-controls {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 14rpx;
}

.evidence-toggle {
  font-size: 22rpx;
  color: var(--theme-primary);
}

.candidate-actions {
  display: flex;
  gap: 12rpx;
}

.candidate-btn {
  padding: 8rpx 18rpx;
  border-radius: var(--theme-radius-sm);
  border: 1rpx solid var(--theme-border);
  background: transparent;
}

.candidate-approve.active {
  border-color: var(--theme-primary);
  background: var(--theme-primary);
}

.candidate-approve.active .candidate-btn-text {
  color: var(--theme-text-on-primary);
}

.candidate-reject.active {
  border-color: var(--theme-accent);
  background: color-mix(in oklab, var(--theme-accent) 10%, var(--theme-surface));
}

.candidate-btn-text {
  font-size: 22rpx;
  color: var(--theme-text-primary);
}

.evidence-quote {
  margin-top: 14rpx;
  padding: 14rpx 16rpx;
  border-left: 3rpx solid var(--theme-border);
  background: var(--theme-bg);
  border-radius: var(--theme-radius-sm);
}

.evidence-source {
  display: block;
  font-size: 20rpx;
  color: var(--theme-text-placeholder);
  margin-bottom: 6rpx;
}

.evidence-excerpt {
  display: block;
  font-size: 23rpx;
  line-height: 1.6;
  color: var(--theme-text-secondary);
}

.chat-preset-heading {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: var(--theme-text-primary);
  margin-bottom: 14rpx;
}

.preset-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.preset-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
  padding: 18rpx 20rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: var(--theme-bg);
}

.preset-item.selected {
  border-color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 5%, var(--theme-surface));
}

.preset-prompt {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.55;
  color: var(--theme-text-primary);
}

.preset-cue {
  flex-shrink: 0;
  font-size: 21rpx;
  color: var(--theme-primary);
}

.chat-thread {
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid var(--theme-border);
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.chat-turn {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.chat-user-msg {
  padding: 16rpx 20rpx;
  border-radius: var(--theme-radius-md);
  background: var(--theme-primary);
}

.chat-role-tag {
  display: block;
  font-size: 19rpx;
  color: color-mix(in oklab, var(--theme-text-on-primary) 80%, transparent);
  margin-bottom: 4rpx;
}

.chat-user-text {
  display: block;
  font-size: 25rpx;
  line-height: 1.6;
  color: var(--theme-text-on-primary);
}

.chat-reply-msg {
  padding: 20rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-md);
  background: var(--theme-bg);
}

.chat-reply-head {
  margin-bottom: 10rpx;
}

.chat-demo-badge {
  display: inline-block;
  padding: 4rpx 12rpx;
  border-radius: var(--theme-radius-sm);
  background: color-mix(in oklab, var(--theme-primary) 10%, var(--theme-surface));
  font-size: 20rpx;
  font-weight: 600;
  color: var(--theme-primary);
}

.chat-reply-text {
  display: block;
  font-size: 26rpx;
  line-height: 1.75;
  color: var(--theme-text-primary);
}

.citation-strip {
  margin-top: 16rpx;
  padding-top: 14rpx;
  border-top: 1rpx dashed var(--theme-border);
}

.citation-strip-label {
  display: block;
  font-size: 20rpx;
  color: var(--theme-text-placeholder);
  margin-bottom: 10rpx;
}

.citation-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.citation-chip {
  padding: 6rpx 14rpx;
  border: 1rpx solid var(--theme-border);
  border-radius: var(--theme-radius-sm);
  background: var(--theme-surface);
}

.citation-chip.active {
  border-color: var(--theme-primary);
  background: color-mix(in oklab, var(--theme-primary) 8%, var(--theme-surface));
}

.citation-chip-text {
  font-size: 21rpx;
  color: var(--theme-primary);
}

.citation-detail {
  margin-top: 12rpx;
  padding: 14rpx 16rpx;
  border-left: 3rpx solid var(--theme-primary);
  background: var(--theme-surface);
  border-radius: var(--theme-radius-sm);
}

.citation-detail-label {
  display: block;
  font-size: 20rpx;
  font-weight: 600;
  color: var(--theme-primary);
  margin-bottom: 6rpx;
}

.citation-detail-excerpt {
  display: block;
  font-size: 23rpx;
  line-height: 1.6;
  color: var(--theme-text-secondary);
}

.demo-footer {
  padding-top: 16rpx;
  border-top: 1rpx solid var(--theme-border);
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.footer-step-row {
  display: flex;
  gap: 16rpx;
}

.demo-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  margin: 0;
  padding: 0 24rpx;
  border-radius: var(--theme-radius-sm);
  font-size: 27rpx;
  font-weight: 600;
}

.demo-btn::after {
  border: none;
}

.demo-btn-primary {
  background: var(--theme-primary);
  color: var(--theme-text-on-primary);
}

.demo-btn-secondary {
  background: transparent;
  color: var(--theme-text-primary);
  border: 1rpx solid var(--theme-border);
}

.footer-util-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 4rpx 6rpx;
}

.footer-link {
  font-size: 24rpx;
  color: var(--theme-text-secondary);
  padding: 8rpx 4rpx;
}

.footer-link-accent {
  color: var(--theme-primary);
  font-weight: 600;
}
</style>
