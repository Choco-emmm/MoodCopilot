<template>
  <div class="report-charts-container">
    <div class="chart-section">
      <div class="chart-header">
        <h4 class="chart-title">情绪波动 <span class="chart-title-en">Mood Fluctuation</span></h4>
        <p class="chart-subtitle">随着时间推移的情绪波段起伏</p>
        <p v-if="isMobile" class="chart-hint">双指缩放可放大查看</p>
      </div>
      <div class="chart-box-wrapper">
        <v-chart class="chart-box" :option="lineOption" autoresize @click="handleChartClick" />
      </div>
    </div>

    <div class="chart-section mt-4">
      <div class="chart-header">
        <h4 class="chart-title">情绪象限 <span class="chart-title-en">Emotion Quadrant</span></h4>
        <p class="chart-subtitle">情绪在效价与唤醒度上的散点分布</p>
      </div>
      <div class="chart-box-wrapper">
        <v-chart class="chart-box quadrant-box" :option="scatterOption" autoresize @click="handleChartClick" />
      </div>
    </div>

    <n-modal v-model:show="showModal">
      <n-card
        style="width: 90%; max-width: 400px; border-radius: var(--radius-xl); background: var(--color-surface); border: none;"
        :title="selectedMood?.date"
        :bordered="false"
        size="medium"
        role="dialog"
        aria-modal="true"
      >
        <template #header-extra>
          <span :style="{ color: moodColor(selectedMood?.moodLabel || '') }" style="font-weight: 600; font-family: var(--font-serif)">
            {{ selectedMood?.moodLabel }}
          </span>
        </template>
        
        <p v-if="selectedMood?.contentSnippet" class="snippet-text">
          「{{ selectedMood.contentSnippet }}」
        </p>
        <p v-else class="snippet-text text-muted">
          暂无日记摘要
        </p>

        <template #footer>
          <div class="modal-footer" style="display: flex; justify-content: flex-end; border-top: 1px dashed rgba(255,255,255,0.08); padding-top: 16px;">
            <button class="action-btn" @click="goToDiary" :disabled="!selectedMood?.diaryIds?.length" style="background: transparent; border: none; color: var(--color-primary); cursor: pointer; font-size: 0.95rem;">
              翻开日记 →
            </button>
          </div>
        </template>
      </n-card>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { ScatterChart, LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  VisualMapComponent,
  DataZoomComponent,
  MarkLineComponent
} from 'echarts/components'
import VChart from 'vue-echarts'
import type { DailyMood } from '../stores/report'
import { moodColor } from '../utils/mood'

use([
  CanvasRenderer,
  ScatterChart, LineChart,
  GridComponent, TooltipComponent, VisualMapComponent, DataZoomComponent, MarkLineComponent
])

const props = defineProps<{
  moods: DailyMood[]
}>()

const router = useRouter()
const showModal = ref(false)
const selectedMood = ref<DailyMood | null>(null)

const isMobile = ref(window.innerWidth <= 480)
function onResize() {
  isMobile.value = window.innerWidth <= 480
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

function handleChartClick(params: any) {
  if (params.componentType === 'series') {
    const dataIndex = params.dataIndex
    const mood = props.moods[dataIndex]
    if (mood) {
      selectedMood.value = mood
      setTimeout(() => { showModal.value = true }, 50)
    }
  }
}

function goToDiary() {
  if (selectedMood.value && selectedMood.value.diaryIds && selectedMood.value.diaryIds.length > 0) {
    router.push(`/diary/${selectedMood.value.diaryIds[0]}`)
  }
}

// Add jitter to avoid scatter point overlap
function jitter(val: number, range: number) {
  return val + (Math.random() - 0.5) * range
}

const lineOption = computed(() => {
  const data = props.moods.map(m => ({
    name: m.date,
    value: m.valence ?? 0,
    moodLabel: m.moodLabel,
    snippet: m.contentSnippet || '',
    arousal: m.arousal ?? 0,
    intensity: m.moodIntensity
  }))

  // Build piecewise color stops using native mood colors
  const pieces = data.map((d, i) => ({
    gte: i,
    lte: i + 1,
    color: moodColor(d.moodLabel)
  }))

  return {
    tooltip: {
      show: !isMobile.value,
      trigger: 'axis',
      confine: true,
      backgroundColor: '#fffdf8',
      borderColor: '#e8e6e1',
      borderWidth: 1,
      padding: [12, 16],
      textStyle: { color: '#20201d', fontSize: 13, fontFamily: '"Noto Serif SC", serif' },
      formatter: (params: any) => {
        const d = params[0].data
        const dot = `<span style="display:inline-block;width:6px;height:6px;border-radius:50%;background:${moodColor(d.moodLabel)};margin-right:8px;vertical-align:middle;"></span>`
        return `<div style="font-family:'Noto Serif SC',serif;"><div style="margin-bottom:6px;">${dot}<b style="color:${moodColor(d.moodLabel)};font-size:14px;">${d.moodLabel || '--'}</b> &nbsp;<span style="font-size:11px;color:#a3a3a3;font-family:sans-serif;">${d.name}</span></div>${d.snippet ? `<div style="font-size:12px;color:#67645d;line-height:1.6;border-left:2px solid #eaeaea;padding-left:8px;">${d.snippet}</div>` : ''}</div>`
      }
    },
    grid: { left: 45, right: 20, top: 20, bottom: isMobile.value ? 24 : 40 },
    dataZoom: [
      { type: 'inside', start: 0, end: 100, moveOnTouch: true, zoomOnTouch: true },
      {
        type: 'slider', show: !isMobile.value,
        start: 0, end: 100, height: 12, bottom: 8,
        borderColor: 'transparent', backgroundColor: 'transparent',
        fillerColor: 'rgba(74, 124, 98, 0.1)',
        handleStyle: { color: '#4a7c62', borderColor: 'transparent' },
        textStyle: { color: 'transparent' }
      }
    ],
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map(d => d.name),
      axisLine: { lineStyle: { color: '#e0dfdb', width: 1 } },
      axisTick: { show: false },
      axisLabel: {
        color: '#999',
        fontSize: 10,
        fontFamily: 'sans-serif',
        margin: 12,
        rotate: data.length > 10 ? 30 : 0,
        interval: 'auto',
        formatter: (val: string) => val.slice(5).replace('-', '/') // strip "2026-" and use slash
      }
    },
    yAxis: {
      type: 'value',
      min: -100, max: 100,
      interval: 100,
      splitLine: { show: true, lineStyle: { type: 'dashed', color: '#f0efe9' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        show: true,
        color: '#999',
        fontSize: 10,
        fontFamily: '"Noto Serif SC", serif',
        margin: 16,
        formatter: (val: number) => {
          if (val === 100) return '正向'
          if (val === 0) return '平衡'
          if (val === -100) return '负向'
          return ''
        }
      }
    },
    visualMap: {
      show: false,
      type: 'piecewise',
      dimension: 0,
      pieces,
      seriesIndex: 0
    },
    series: [
      {
        type: 'line',
        data,
        smooth: 0.4,
        symbol: 'emptyCircle',
        symbolSize: (val: number, params: any) => {
          const arousal = data[params.dataIndex]?.arousal ?? 0
          return 4 + ((arousal + 100) / 200) * 8 // smaller dots
        },
        itemStyle: {
          color: '#fff', // white center
          borderColor: (params: any) => moodColor(data[params.dataIndex]?.moodLabel),
          borderWidth: 1.5,
        },
        lineStyle: { 
          color: '#d6d4ce', // neutral line color linking the dots
          width: 1.5, 
          type: 'solid'
        },
        areaStyle: {
          opacity: 0.15,
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#e8e6e1' },
              { offset: 1, color: 'rgba(255,255,255,0)' }
            ]
          }
        },
        markLine: {
          silent: true,
          symbol: 'none',
          label: { show: false },
          data: [{ yAxis: 0, lineStyle: { color: '#e0dfdb', type: 'dashed', width: 1 } }]
        }
      },
      // invisible wide hit area for easier tap on mobile
      {
        type: 'line', data, smooth: true,
        symbolSize: 28, itemStyle: { opacity: 0 },
        lineStyle: { opacity: 0 }, tooltip: { show: false }
      }
    ]
  }
})

const scatterOption = computed(() => {
  const data = props.moods.map(m => ({
    value: [jitter(m.valence ?? 0, 8), jitter(m.arousal ?? 0, 8)],
    moodLabel: m.moodLabel,
    date: m.date,
    snippet: m.contentSnippet || '',
    intensity: m.moodIntensity
  }))

  return {
    tooltip: {
      show: !isMobile.value,
      trigger: 'item',
      confine: true,
      backgroundColor: '#fffdf8',
      borderColor: '#e8e6e1',
      borderWidth: 1,
      padding: [12, 16],
      textStyle: { color: '#20201d', fontSize: 13, fontFamily: '"Noto Serif SC", serif' },
      formatter: (params: any) => {
        const d = params.data
        const dot = `<span style="display:inline-block;width:6px;height:6px;border-radius:50%;background:${moodColor(d.moodLabel)};margin-right:8px;vertical-align:middle;"></span>`
        return `<div style="font-family:'Noto Serif SC',serif;"><div style="margin-bottom:6px;">${dot}<b style="color:${moodColor(d.moodLabel)};font-size:14px;">${d.moodLabel || '--'}</b> &nbsp;<span style="font-size:11px;color:#a3a3a3;font-family:sans-serif;">${d.date}</span></div>${d.snippet ? `<div style="font-size:12px;color:#67645d;line-height:1.6;border-left:2px solid #eaeaea;padding-left:8px;">${d.snippet}</div>` : ''}</div>`
      }
    },
    grid: { left: 55, right: 55, top: 40, bottom: 40 },
    xAxis: {
      type: 'value', min: -100, max: 100,
      interval: 100,
      name: '效价 Valence',
      nameLocation: 'middle',
      nameGap: 28,
      nameTextStyle: { color: '#aaa', fontSize: 11, fontFamily: '"Noto Serif SC", serif' },
      splitLine: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        show: true,
        color: '#999',
        fontSize: 10,
        fontFamily: '"Noto Serif SC", serif',
        formatter: (val: number) => {
          if (val === -100) return '← 消极'
          if (val === 100) return '积极 →'
          return ''
        }
      }
    },
    yAxis: {
      type: 'value', min: -100, max: 100,
      interval: 100,
      name: '唤醒度 Arousal',
      nameLocation: 'middle',
      nameGap: 32,
      nameTextStyle: { color: '#aaa', fontSize: 11, fontFamily: '"Noto Serif SC", serif' },
      splitLine: { show: false },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        show: true,
        color: '#999',
        fontSize: 10,
        fontFamily: '"Noto Serif SC", serif',
        formatter: (val: number) => {
          if (val === 100) return '高能量 ↑'
          if (val === -100) return '↓ 低能量'
          return ''
        }
      }
    },
    series: [
      {
        type: 'scatter',
        symbolSize: (val: any, params: any) => 4 + (params.data.intensity ?? 1) * 3,
        itemStyle: {
          color: (params: any) => moodColor(params.data.moodLabel),
          opacity: 0.7,
        },
        data,
        markLine: {
          silent: true, animation: false, symbol: 'none',
          lineStyle: { color: '#e0dfdb', type: 'dashed', width: 1 },
          label: { show: false },
          data: [{ xAxis: 0 }, { yAxis: 0 }]
        }
      }
    ]
  }
})
</script>

<style scoped>
.report-charts-container {
  margin: 60px 0;
}
.chart-section {
  margin-bottom: 60px;
}
.chart-header {
  margin-bottom: 24px;
  text-align: center;
}
.chart-title {
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.15em;
  color: var(--color-text);
  margin: 0 0 6px 0;
  font-weight: 600;
}
.chart-title-en {
  font-family: var(--font-body);
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0.08em;
  color: var(--color-text-muted);
  text-transform: uppercase;
  margin-left: 6px;
  vertical-align: middle;
}
.chart-subtitle {
  font-family: var(--font-body);
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
  letter-spacing: 0.05em;
}
.chart-hint {
  font-size: 11px;
  color: var(--color-text-muted);
  margin: 8px 0 0;
  font-style: italic;
}
/* 清爽的边框质感 */
.chart-box-wrapper {
  background: var(--color-surface);
  border-radius: 8px;
  padding: 24px 12px;
  border: 1px solid color-mix(in oklab, var(--color-border) 60%, transparent);
  box-shadow: 0 8px 30px rgba(0,0,0,0.02);
}
.chart-box {
  width: 100%;
  height: 280px;
}
.quadrant-box {
  height: 320px;
}
.mt-4 {
  margin-top: 56px;
}

.snippet-text {
  font-size: 1.05rem;
  line-height: 1.7;
  color: var(--color-text);
  margin: 10px 0;
  word-break: break-all;
  font-family: var(--font-serif);
}
.text-muted {
  color: var(--color-text-secondary);
  font-family: var(--font-body);
}
.action-btn:hover {
  color: var(--color-primary-hover) !important;
}
.action-btn:disabled {
  color: var(--color-text-secondary) !important;
  cursor: not-allowed !important;
}
</style>
