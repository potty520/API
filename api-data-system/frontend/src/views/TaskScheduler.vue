<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索任务名称或编码"
          style="width: 300px"
          clearable
          @clear="loadTasks"
          @keyup.enter="loadTasks"
        >
          <template #append>
            <el-button icon="Search" @click="loadTasks" />
          </template>
        </el-input>
      </div>
      <el-button type="primary" icon="Plus" @click="openTaskDialog()">
        新建任务
      </el-button>
    </div>

    <el-table :data="taskList" border stripe>
      <el-table-column prop="taskName" label="任务名称" width="150" />
      <el-table-column prop="taskCode" label="任务编码" width="150" />
      <el-table-column prop="cronExpression" label="Cron表达式" width="150" />
      <el-table-column prop="enableStatus" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.enableStatus === 'ENABLED' ? 'success' : 'info'">
            {{ scope.row.enableStatus === 'ENABLED' ? '已启用' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastExecuteTime" label="上次执行" width="160">
        <template #default="scope">
          {{ formatDateTime(scope.row.lastExecuteTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="nextExecuteTime" label="下次执行" width="160">
        <template #default="scope">
          {{ formatDateTime(scope.row.nextExecuteTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="executeCount" label="执行次数" width="100" />
      <el-table-column prop="successCount" label="成功" width="80">
        <template #default="scope">
          <span style="color: #67C23A;">{{ scope.row.successCount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="failCount" label="失败" width="80">
        <template #default="scope">
          <span style="color: #F56C6C;">{{ scope.row.failCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="280">
        <template #default="scope">
          <el-button link type="primary" icon="VideoPlay" @click="executeTask(scope.row)">
            执行
          </el-button>
          <el-button link type="primary" icon="Edit" @click="openTaskDialog(scope.row)">
            编辑
          </el-button>
          <el-button link :type="scope.row.enableStatus === 'ENABLED' ? 'warning' : 'success'" icon="Switch" @click="toggleTask(scope.row)">
            {{ scope.row.enableStatus === 'ENABLED' ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" icon="Delete" @click="deleteTask(scope.row)">
            删除
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
      @size-change="loadTasks"
      @current-change="loadTasks"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="taskForm" :rules="formRules" label-width="140px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="taskForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务编码" prop="taskCode">
          <el-input v-model="taskForm.taskCode" placeholder="请输入任务编码" :disabled="!!taskForm.id" />
        </el-form-item>
        <el-form-item label="关联API配置" prop="apiConfigId">
          <el-select v-model="taskForm.apiConfigId" placeholder="请选择API配置" style="width: 100%">
            <el-option v-for="api in apiConfigList" :key="api.id" :label="api.name" :value="api.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标表名" prop="targetTable">
          <el-input v-model="taskForm.targetTable" placeholder="请输入目标表名" />
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cronExpression">
          <el-input v-model="taskForm.cronExpression" placeholder="例如: 0 0 */5 * * *">
            <template #append>
              <el-button @click="validateAndPreviewCron">验证</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="cronDescription" label="执行说明">
          <span style="color: #67C23A;">{{ cronDescription }}</span>
        </el-form-item>
        <el-form-item v-if="cronNextTime" label="下次执行时间">
          <span style="color: #409EFF;">{{ cronNextTime }}</span>
        </el-form-item>
        <el-form-item label="常用Cron示例">
          <el-space wrap>
            <el-tag v-for="example in cronExamples" :key="example.value" @click="selectCronExample(example.value)" style="cursor: pointer;">
              {{ example.label }}
            </el-tag>
          </el-space>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="taskForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTask">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskApi, apiConfigApi } from '@/api'

const searchKeyword = ref('')
const taskList = ref([])
const apiConfigList = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const dialogVisible = ref(false)
const dialogTitle = ref('新建任务')
const formRef = ref(null)
const taskForm = reactive({
  id: null,
  taskName: '',
  taskCode: '',
  apiConfigId: null,
  targetTable: '',
  cronExpression: '0 0 */5 * * *',
  remark: ''
})

const formRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskCode: [{ required: true, message: '请输入任务编码', trigger: 'blur' }],
  cronExpression: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }]
}

const cronDescription = ref('')
const cronNextTime = ref('')

const cronExamples = [
  { label: '每分钟', value: '0 * * * *' },
  { label: '每5分钟', value: '0 */5 * * *' },
  { label: '每小时', value: '0 0 * * *' },
  { label: '每天8点', value: '0 0 8 * *' },
  { label: '每天0点', value: '0 0 0 * *' },
  { label: '每周一9点', value: '0 0 9 * * 1' }
]

const loadTasks = async () => {
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: searchKeyword.value
    }
    const res = await taskApi.getList(params)
    taskList.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    ElMessage.error('Failed to load tasks')
  }
}

const loadApiConfigs = async () => {
  try {
    const res = await apiConfigApi.getList({ pageNum: 1, pageSize: 100 })
    apiConfigList.value = res.data.records
  } catch (error) {
    console.error('Failed to load API configs', error)
  }
}

const openTaskDialog = async (task) => {
  dialogVisible.value = true
  if (task) {
    dialogTitle.value = '编辑任务'
    Object.assign(taskForm, task)
    if (taskForm.cronExpression) {
      await validateAndPreviewCron()
    }
  } else {
    dialogTitle.value = '新建任务'
    taskForm.id = null
  }
}

const validateAndPreviewCron = async () => {
  if (!taskForm.cronExpression) return

  try {
    const descRes = await taskApi.getCronDescription(taskForm.cronExpression)
    cronDescription.value = descRes.data

    const timeRes = await taskApi.getCronNextTime(taskForm.cronExpression)
    cronNextTime.value = timeRes.data
  } catch (error) {
    cronDescription.value = 'Invalid Cron expression'
    cronNextTime.value = ''
  }
}

const selectCronExample = (cron) => {
  taskForm.cronExpression = cron
  validateAndPreviewCron()
}

const saveTask = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await taskApi.save(taskForm)
        ElMessage.success('Task saved successfully')
        dialogVisible.value = false
        loadTasks()
      } catch (error) {
        ElMessage.error('Failed to save task')
      }
    }
  })
}

const toggleTask = async (task) => {
  try {
    await taskApi.toggle(task.id)
    ElMessage.success('Task status updated')
    loadTasks()
  } catch (error) {
    ElMessage.error('Failed to toggle task status')
  }
}

const executeTask = async (task) => {
  try {
    await ElMessageBox.confirm('Confirm to execute this task?', 'Execute Task', {
      confirmButtonText: 'Execute',
      cancelButtonText: 'Cancel',
      type: 'warning'
    })

    await taskApi.execute(task.id)
    ElMessage.success('Task execution started')
    setTimeout(() => loadTasks(), 2000)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to execute task')
    }
  }
}

const deleteTask = async (task) => {
  try {
    await ElMessageBox.confirm('Confirm to delete this task?', 'Delete Task', {
      confirmButtonText: 'Delete',
      cancelButtonText: 'Cancel',
      type: 'warning'
    })

    await taskApi.delete(task.id)
    ElMessage.success('Task deleted successfully')
    loadTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('Failed to delete task')
    }
  }
}

const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  cronDescription.value = ''
  cronNextTime.value = ''
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString()
}

onMounted(() => {
  loadTasks()
  loadApiConfigs()
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
