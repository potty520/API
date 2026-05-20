<template>
  <div class="page-container">
    <div class="page-header">
      <el-form :inline="true">
        <el-form-item label="选择任务">
          <el-select v-model="selectedTaskId" placeholder="请选择任务" clearable style="width: 250px" @change="loadLogs">
            <el-option v-for="task in taskList" :key="task.id" :label="task.taskName" :value="task.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行状态">
          <el-select v-model="statusFilter" placeholder="全部" clearable style="width: 120px" @change="loadLogs">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="运行中" value="RUNNING" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-button icon="Refresh" @click="loadLogs">刷新</el-button>
    </div>

    <el-table :data="logList" border stripe>
      <el-table-column prop="batchId" label="批次ID" width="200" />
      <el-table-column prop="taskName" label="任务名称" width="150" />
      <el-table-column prop="executeStatus" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.executeStatus)">
            {{ getStatusText(scope.row.executeStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="160" />
      <el-table-column prop="endTime" label="结束时间" width="160" />
      <el-table-column prop="duration" label="耗时(ms)" width="100">
        <template #default="scope">
          {{ scope.row.duration || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="totalRecords" label="总记录数" width="100" />
      <el-table-column prop="newInsertCount" label="新增数" width="100">
        <template #default="scope">
          <span style="color: #67C23A;">{{ scope.row.newInsertCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="100">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="viewLogDetail(scope.row)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pagination.pageNum"
      v-model:page-size="pagination.pageSize"
      :total="pagination.total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 20px; text-align: right;"
      @size-change="loadLogs"
      @current-change="loadLogs"
    />

    <el-dialog v-model="detailDialogVisible" title="日志详情" width="900px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="批次ID">{{ currentLog.batchId }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="getStatusType(currentLog.executeStatus)">
            {{ getStatusText(currentLog.executeStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ currentLog.startTime }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ currentLog.endTime }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.duration }} ms</el-descriptions-item>
        <el-descriptions-item label="总记录数">{{ currentLog.totalRecords }}</el-descriptions-item>
        <el-descriptions-item label="新增插入">{{ currentLog.newInsertCount }}</el-descriptions-item>
        <el-descriptions-item label="已存在">{{ currentLog.existingCount }}</el-descriptions-item>
      </el-descriptions>

      <el-divider v-if="currentLog.errorMessage" content-position="left">错误信息</el-divider>
      <pre v-if="currentLog.errorMessage" style="background: #f5f5f5; padding: 15px; border-radius: 4px; max-height: 200px; overflow: auto;">{{ currentLog.errorMessage }}</pre>

      <el-divider content-position="left">请求报文</el-divider>
      <pre style="background: #f5f5f5; padding: 15px; border-radius: 4px; max-height: 200px; overflow: auto;">{{ currentLog.requestBody || 'No request body' }}</pre>

      <el-divider content-position="left">响应报文</el-divider>
      <pre style="background: #f5f5f5; padding: 15px; border-radius: 4px; max-height: 300px; overflow: auto;">{{ currentLog.responseBody || 'No response body' }}</pre>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { logApi, taskApi } from '@/api'

const taskList = ref([])
const selectedTaskId = ref(null)
const statusFilter = ref('')
const logList = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const detailDialogVisible = ref(false)
const currentLog = ref({})

const loadTasks = async () => {
  try {
    const res = await taskApi.getEnabledTasks()
    taskList.value = res.data
  } catch (error) {
    console.error('Failed to load tasks', error)
  }
}

const loadLogs = async () => {
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      taskId: selectedTaskId.value
    }
    const res = await logApi.getList(params)
    logList.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    ElMessage.error('Failed to load logs')
  }
}

const viewLogDetail = (log) => {
  currentLog.value = log
  detailDialogVisible.value = true
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
  loadTasks()
  loadLogs()
})
</script>

<style scoped>
.page-header {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
