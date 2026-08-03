<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getHealth,
  getStatistic,
  getStatisticsReport,
  getTenant,
  type DayCount,
  type StatisticData,
} from '../api/gateway'

// 网关健康状态文案（GET /gateway/health）
const healthStatus = ref<string>('')
const healthLoading = ref(false)

// 今日统计（GET /gateway/statistic）
const todayStats = ref<StatisticData | null>(null)
const statsLoading = ref(false)

// 近 7 日趋势（GET /gateway/statistics/report）
const reportDays = ref<DayCount[]>([])
const reportLoading = ref(false)

const tenant = computed(() => getTenant() ?? '')

/** 7 日图表最大柱高（简单比例，无第三方图表库） */
const maxCount = computed(() => {
  const counts = reportDays.value.map((d) => d.count)
  return Math.max(...counts, 1)
})

/** 探测网关是否存活 */
async function loadHealth() {
  healthLoading.value = true
  try {
    healthStatus.value = await getHealth()
  } catch (err) {
    healthStatus.value = '不可用'
    ElMessage.error(err instanceof Error ? err.message : '健康检查失败')
  } finally {
    healthLoading.value = false
  }
}

/** 查询当前租户今日请求量 */
async function loadTodayStats() {
  if (!tenant.value) return
  statsLoading.value = true
  try {
    todayStats.value = await getStatistic(tenant.value)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '统计加载失败')
  } finally {
    statsLoading.value = false
  }
}

/** 查询近 7 日请求趋势 */
async function loadReport() {
  if (!tenant.value) return
  reportLoading.value = true
  try {
    const data = await getStatisticsReport(tenant.value)
    reportDays.value = data.days
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '报表加载失败')
  } finally {
    reportLoading.value = false
  }
}

/** 刷新本页全部数据 */
async function refreshAll() {
  await Promise.all([loadHealth(), loadTodayStats(), loadReport()])
}

/** 把 yyyyMMdd 格式化为可读日期 */
function formatDate(raw: string): string {
  if (raw.length !== 8) return raw
  return `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}`
}

onMounted(refreshAll)
</script>

<template>
  <div class="page">
    <!-- 网关存活 -->
    <el-card v-loading="healthLoading" shadow="never">
      <template #header>
        <div class="card-header">
          <span>网关状态</span>
          <el-button link type="primary" @click="refreshAll">刷新</el-button>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="健康检查">
          <el-tag type="success">{{ healthStatus || '检测中...' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="管理 API">
          <el-tag>/gateway/*</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 租户与限流说明（后端白名单，后续可接 MySQL API） -->
    <el-card shadow="never">
      <template #header>租户 / 限流</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="当前租户">{{ tenant || '-' }}</el-descriptions-item>
        <el-descriptions-item label="白名单租户">tenant-a、tenant-b</el-descriptions-item>
        <el-descriptions-item label="限流策略">
          QPS / QPD 由网关 config.yaml 配置，后续可在此对接 MySQL 管理 API
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 今日请求量 -->
    <el-card v-loading="statsLoading" shadow="never">
      <template #header>今日请求统计</template>
      <el-empty v-if="!todayStats" description="暂无数据" />
      <el-descriptions v-else :column="3" border>
        <el-descriptions-item label="租户">{{ todayStats.tenant }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ formatDate(todayStats.date) }}</el-descriptions-item>
        <el-descriptions-item label="请求量">{{ todayStats.count }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 近 7 日趋势（简易柱状图） -->
    <el-card v-loading="reportLoading" shadow="never">
      <template #header>近 7 日请求趋势</template>
      <el-empty v-if="reportDays.length === 0" description="暂无数据" />
      <div v-else class="chart">
        <div v-for="day in reportDays" :key="day.date" class="bar-item">
          <div
            class="bar"
            :style="{ height: `${(day.count / maxCount) * 120}px` }"
            :title="`${formatDate(day.date)}: ${day.count}`"
          />
          <span class="bar-count">{{ day.count }}</span>
          <span class="bar-date">{{ formatDate(day.date).slice(5) }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  min-height: 160px;
  padding: 8px 0;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.bar {
  width: 36px;
  min-height: 2px;
  background: #409eff;
  border-radius: 4px 4px 0 0;
  transition: height 0.3s;
}

.bar-count {
  font-size: 12px;
  color: #606266;
}

.bar-date {
  font-size: 11px;
  color: #909399;
}
</style>
