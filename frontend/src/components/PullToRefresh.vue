<template>
  <div class="ptr-wrapper" @touchstart="onTouchStart" @touchmove="onTouchMove" @touchend="onTouchEnd" @touchcancel="onTouchEnd">
    <div 
      class="ptr-indicator"
      :style="{
        height: `${currentHeight}px`,
        transition: isPulling ? 'none' : 'height 0.3s cubic-bezier(0.25, 0.8, 0.25, 1)'
      }"
    >
      <div class="ptr-content" :class="{ 'ptr-ready': readyToRefresh, 'ptr-loading': loading }">
        <svg v-if="loading" class="ptr-spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="12" y1="2" x2="12" y2="6"></line>
          <line x1="12" y1="18" x2="12" y2="22"></line>
          <line x1="4.93" y1="4.93" x2="7.76" y2="7.76"></line>
          <line x1="16.24" y1="16.24" x2="19.07" y2="19.07"></line>
          <line x1="2" y1="12" x2="6" y2="12"></line>
          <line x1="18" y1="12" x2="22" y2="12"></line>
          <line x1="4.93" y1="19.07" x2="7.76" y2="16.24"></line>
          <line x1="16.24" y1="7.76" x2="19.07" y2="4.93"></line>
        </svg>
        <svg v-else class="ptr-arrow" :style="{ transform: `rotate(${readyToRefresh ? 180 : 0}deg)` }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="12" y1="5" x2="12" y2="19"></line>
          <polyline points="19 12 12 19 5 12"></polyline>
        </svg>
        <span class="ptr-text">{{ loading ? '正在刷新...' : (readyToRefresh ? '释放立即刷新' : '下拉刷新') }}</span>
      </div>
    </div>
    <div class="ptr-body">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

const props = defineProps<{
  loading: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

const maxPull = 100
const threshold = 60

const startY = ref(0)
const currentY = ref(0)
const isPulling = ref(false)

const distance = computed(() => Math.max(0, currentY.value - startY.value))
const currentHeight = computed(() => {
  if (props.loading) return 50
  if (!isPulling.value) return 0
  // resistance formula
  return Math.min(maxPull, distance.value * 0.4)
})
const readyToRefresh = computed(() => currentHeight.value >= threshold * 0.4)

function onTouchStart(e: TouchEvent) {
  if (window.scrollY > 5) return // Only pull when at top
  startY.value = e.touches[0].clientY
  currentY.value = e.touches[0].clientY
  isPulling.value = true
}

function onTouchMove(e: TouchEvent) {
  if (!isPulling.value) return
  const y = e.touches[0].clientY
  if (y > startY.value && window.scrollY <= 5) {
    if (e.cancelable) e.preventDefault()
    currentY.value = y
  } else if (y < startY.value) {
    // User scrolled down (finger went up)
    isPulling.value = false
  }
}

function onTouchEnd() {
  if (!isPulling.value) return
  
  // Evaluate readyToRefresh BEFORE setting isPulling to false, 
  // because readyToRefresh depends on currentHeight which becomes 0 when isPulling is false.
  const shouldRefresh = readyToRefresh.value && !props.loading
  
  isPulling.value = false
  
  if (shouldRefresh) {
    emit('refresh')
  }
  
  startY.value = 0
  currentY.value = 0
}
</script>

<style scoped>
.ptr-wrapper {
  position: relative;
  overflow: hidden;
}

.ptr-indicator {
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.ptr-content {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.ptr-arrow {
  width: 16px;
  height: 16px;
  transition: transform 0.3s;
}

.ptr-spinner {
  width: 16px;
  height: 16px;
  animation: ptr-spin 1s linear infinite;
  color: var(--color-primary);
}

.ptr-text {
  font-weight: 500;
}

@keyframes ptr-spin {
  100% { transform: rotate(360deg); }
}

.ptr-body {
  position: relative;
  z-index: 1;
}
</style>
