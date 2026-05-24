<template>
  <div class="report-charts-container">
    <div class="chart-header">
      <h4>情绪波浪线</h4>
    </div>
    <v-chart class="chart-box" :option="lineOption" autoresize @click="handleChartClick" />

    <div class="chart-header mt-4">
      <h4>情绪象限密度图</h4>
      <p class="chart-subtitle">横轴：效价（负向至正向） &nbsp;|&nbsp; 纵轴：唤醒度（低能至高能）</p>
    </div>
    <v-chart class="chart-box quadrant-box" :option="scatterOption" autoresize @click="handleChartClick" />

    <n-modal v-model:show="showModal">
      <n-card
        style="width: 90%; max-width: 400px; border-radius: 12px"
        :title="selectedMood?.date"
        :bordered="false"
        size="medium"
        role="dialog"
        aria-modal="true"
      >
        <template #header-extra>
          <n-tag :color="{ color: moodColor(selectedMood?.moodLabel || ''), textColor: '#fff' }" round>
            {{ selectedMood?.moodLabel }}
          </n-tag>
        </template>
        
        <p v-if="selectedMood?.contentSnippet" class="snippet-text">
          「{{ selectedMood.contentSnippet }}」
        </p>
        <p v-else class="snippet-text text-muted">
          暂无日记摘要
        </p>

        <template #footer>
          <div class="modal-footer">
            <n-button type="primary" block @click="goToDiary" :disabled="!selectedMood?.diaryIds?.length">查看日记详情</n-button>
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
import type { DailyMood } from '../stores/diary'
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
  const data = props.moods.map(m => {
    return {
      name: m.date,
      value: m.valence ?? 0,
      moodLabel: m.moodLabel,
      snippet: m.contentSnippet || '',
      arousal: m.arousal ?? 0,
      intensity: m.moodIntensity
    }
  })

  return {
    tooltip: {
      show: !isMobile.value,
      trigger: 'axis',
      confine: true,
      formatter: (params: any) => {
        const d = params[0].data
        return `${d.name}<br/>${d.moodLabel} (效价 ${d.value}, 唤醒度 ${d.arousal})<br/>${d.snippet}`
      }
    },
    grid: { left: 15, right: 15, top: 15, bottom: isMobile.value ? 20 : 40 },
    dataZoom: [
      { 
        type: 'inside', 
        start: 0, 
        end: 100,
        moveOnTouch: true,
        zoomOnTouch: true
      },
      { 
        type: 'slider', 
        show: !isMobile.value, 
        start: 0, 
        end: 100, 
        height: 24, 
        bottom: 4 
      }
    ],
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map(d => d.name)
    },
    yAxis: {
      type: 'value',
      min: -100,
      max: 100,
      splitLine: { show: true, lineStyle: { type: 'dashed' } }
    },
    visualMap: {
      show: false,
      type: 'continuous',
      min: -100,
      max: 100,
      color: ['#4a7c62', '#e9b44c', '#d36135']
    },
    series: [
      {
        type: 'line',
        data: data,
        smooth: true,
        symbolSize: (val: number, params: any) => {
          const arousal = data[params.dataIndex]?.arousal ?? 0
          return 4 + ((arousal + 100) / 200) * 8
        },
        itemStyle: {
          color: (params: any) => moodColor(data[params.dataIndex]?.moodLabel)
        },
        markLine: {
          silent: true,
          data: [{ yAxis: 0, lineStyle: { color: '#ccc' } }]
        }
      },
      {
        type: 'line',
        data: data,
        smooth: true,
        symbolSize: 24,
        itemStyle: { opacity: 0 },
        lineStyle: { opacity: 0 },
        tooltip: { show: false }
      }
    ]
  }
})

const scatterOption = computed(() => {
  const data = props.moods.map(m => {
    return {
      value: [jitter(m.valence ?? 0, 8), jitter(m.arousal ?? 0, 8)],
      moodLabel: m.moodLabel,
      date: m.date,
      snippet: m.contentSnippet || '',
      intensity: m.moodIntensity
    }
  })

  return {
    tooltip: {
      show: !isMobile.value,
      trigger: 'item',
      confine: true,
      formatter: (params: any) => {
        const d = params.data
        return `${d.date} - ${d.moodLabel}<br/>${d.snippet}`
      }
    },
    grid: { left: 15, right: 15, top: 20, bottom: 20 },
    xAxis: {
      type: 'value',
      min: -110,
      max: 110,
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      min: -110,
      max: 110,
      splitLine: { show: false }
    },
    series: [
      {
        type: 'scatter',
        symbolSize: (val: any, params: any) => {
          return 6 + (params.data.intensity * 2)
        },
        itemStyle: {
          color: (params: any) => moodColor(params.data.moodLabel),
          opacity: 0.7
        },
        data: data,
        markLine: {
          silent: true,
          animation: false,
          symbol: 'none',
          lineStyle: { color: '#ccc', type: 'dashed' },
          data: [
            { xAxis: 0 },
            { yAxis: 0 }
          ]
        }
      }
    ]
  }
})
</script>

<style scoped>
.snippet-text {
  font-size: 15px;
  line-height: 1.6;
  color: #334155;
  margin: 10px 0;
  word-break: break-all;
}
.text-muted {
  color: #94a3b8;
}
.modal-footer {
  margin-top: 10px;
}
.report-charts-container {
  margin: 1.5rem 0;
}
.chart-header {
  margin-bottom: 0.5rem;
}
.chart-subtitle {
  font-size: 12px;
  color: #64748b;
  margin: 2px 0 8px;
}
.chart-box {
  width: 100%;
  height: 250px;
  background: var(--bg-card);
  border-radius: 8px;
  border: 1px solid var(--border-light);
}
.quadrant-box {
  height: 300px;
}
.mt-4 {
  margin-top: 1.5rem;
}
</style>
