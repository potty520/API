<template>
  <div class="page-container">
    <el-row :gutter="20" class="dashboard-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <el-icon :size="40" color="#409EFF"><Timer /></el-icon>
            <div class="stat-info">
              <div class="stat-label">总任务数</div>
              <div class="stat-value">{{ statsData.totalTasks || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <el-icon :size="40" color="#67C23A"><CircleCheck /></el-icon>
            <div class="stat-info">
              <div class="stat-label">运行中任务</div>
              <div class="stat-value">{{ statsData.runningTasks || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <el-icon :size="40" color="#E6A23C"><TrendCharts /></el-icon>
            <div class="stat-info">
              <div class="stat-label">今日成功率</div>
              <div class="stat-value">{{ statsData.successRate || 100 }}%</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <el-icon :size="40" color="#F56C6C"><DataLine /></el-icon>
            <div class="stat-info">
              <div class="stat-label">今日数据量</div>
              <div class="stat-value">{{ statsData.todayDataCount || 0 }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="dashboard-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>任务执行状态分布</span>
            </div>
          </template>
          <div ref="pieChartRef" style="width: 100%; height: 350px;"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>最近执行记录</span>
            </div>
          </template>
          <el-table :data="statsData.recentExecutions || []" style="width: 100%" max-height="350">
            <el-table-column prop="taskName" label="任务名称" width="150" />
            <el-table-column prop="batchId" label="批次ID" width="150" />
            <el-table-column prop="executeStatus" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="getStatusType(scope.row.executeStatus)">
                  {{ getStatusText(scope.row.executeStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="duration" label="耗时(ms)" width="100" />
            <el-table-column prop="newInsertCount" label="新增数" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="dashboard-row">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>今日执行统计</span>
            </div>
          </template>
          <el-row :gutter="20">
            <el-col :span="8">
              <div class="today-stat">
                <el-icon size="30" color="#67C23A"><SuccessFilled /></el-icon>
                <div>
                  <div class="today-stat-label">成功次数</div>
                  <div class="today-stat-value">{{ statsData.todaySuccessCount || 0 }}</div>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="today-stat">
                <el-icon size="30" color="#F56C6C"><CircleCloseFilled /></el-icon>
                <div>
                  <div class="today-stat-label">失败次数</div>
                  <div class="today-stat-value">{{ statsData.todayFailedCount || 0 }}</div>
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="today-stat">
                <el-icon size="30" color="#409EFF"><DocumentAdd /></el-icon>
                <div>
                  <div class="today-stat-label">新增数据</div>
                  <div class="today-stat-value">{{ statsData.todayDataCount || 0 }}</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api'
import { ElMessage } from 'element-plus'
import {
  Timer,
  CircleCheck,
  TrendCharts,
  DataLine,
  SuccessFilled,
  CircleCloseFilled,
  DocumentAdd
} from '@element-plus/icons-vue'

const statsData = ref({})
const pieChartRef = ref(null)
let pieChart = null

const loadStats = async () => {
  try {
    const res = await dashboardApi.getStats()
    statsData.value = res.data
    await nextTick()
    initPieChart()
  } catch (error) {
    ElMessage.error('Failed to load dashboard data')
  }
}

const initPieChart = () => {
  if (!pieChartRef.value) return

  if (pieChart) {
    pieChart.dispose()
  }

  pieChart = echarts.init(pieChartRef.value)

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '任务状态',
        type: 'pie',
        radius: '60%',
        data: statsData.value.taskStatusDistribution || [
          { name: '运行中', value: 0 },
          { name: '已停止', value: 0 }
        ],
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }

  pieChart.setOption(option)
}

const getStatusType = (status) => {
  const typeMap = {
    'SUCCESS': 'success',
    'FAILED': 'danger',
    'RUNNING': 'warning'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    'SUCCESS': '成功',
    'FAILED': '失败',
    'RUNNING': '运行中'
  }
  return textMap[status] || status
}

onMounted(() => {
  loadStats()
  window.addEventListener('resize', () => {
    pieChart && pieChart.resize()
  })
})
</script>

<style scoped>
.dashboard-row {
  margin-bottom: 20px;
}

.card-header {
  font-size: 18px;
  font-weight: 600;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #303133;
}

.today-stat {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 20px;
}

.today-stat-label {
  font-size: 14px;
  color: #909399;
}

.today-stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
</style>
