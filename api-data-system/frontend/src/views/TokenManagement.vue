<template>
  <div class="page-container">
    <div class="page-header">
      <div></div>
      <el-button type="primary" icon="Plus" @click="openDialog()">
        新建Token配置
      </el-button>
    </div>

    <el-table :data="tokenList" border stripe>
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="tokenType" label="类型" width="120">
        <template #default="scope">
          <el-tag :type="scope.row.tokenType === 'FIXED' ? 'info' : 'success'">
            {{ scope.row.tokenType === 'FIXED' ? '固定Token' : '动态获取' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tokenUrl" label="Token URL" min-width="200" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ getStatusText(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tokenExpireTime" label="过期时间" width="180">
        <template #default="scope">
          {{ formatDateTime(scope.row.tokenExpireTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="200">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="openDialog(scope.row)">
            编辑
          </el-button>
          <el-button link type="danger" icon="Delete" @click="deleteToken(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="700px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="tokenForm" :rules="formRules" label-width="140px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="tokenForm.name" placeholder="请输入Token配置名称" />
        </el-form-item>
        <el-form-item label="Token类型" prop="tokenType">
          <el-radio-group v-model="tokenForm.tokenType">
            <el-radio label="FIXED">固定Token</el-radio>
            <el-radio label="DYNAMIC">动态获取</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="tokenForm.tokenType === 'FIXED'" label="固定Token" prop="fixedToken">
          <el-input v-model="tokenForm.fixedToken" type="textarea" :rows="3" placeholder="请输入Token" />
        </el-form-item>
        <template v-if="tokenForm.tokenType === 'DYNAMIC'">
          <el-form-item label="Token URL" prop="tokenUrl">
            <el-input v-model="tokenForm.tokenUrl" placeholder="请输入Token获取接口URL" />
          </el-form-item>
          <el-form-item label="请求方式" prop="tokenMethod">
            <el-radio-group v-model="tokenForm.tokenMethod">
              <el-radio label="GET">GET</el-radio>
              <el-radio label="POST">POST</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="请求参数" prop="tokenParams">
            <el-input v-model="tokenForm.tokenParams" type="textarea" :rows="3" placeholder='{"grant_type": "client_credentials"}' />
          </el-form-item>
          <el-form-item label="Token提取路径" prop="tokenExtractPath">
            <el-input v-model="tokenForm.tokenExtractPath" placeholder="例如: $.access_token" />
          </el-form-item>
          <el-form-item label="过期时间(秒)" prop="defaultExpiresSeconds">
            <el-input-number v-model="tokenForm.defaultExpiresSeconds" :min="1" />
          </el-form-item>
        </template>
        <el-form-item label="备注">
          <el-input v-model="tokenForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveToken">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { tokenApi } from '@/api'

const tokenList = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新建Token配置')
const formRef = ref(null)

const tokenForm = reactive({
  id: null,
  name: '',
  tokenType: 'DYNAMIC',
  fixedToken: '',
  tokenUrl: '',
  tokenMethod: 'POST',
  tokenParams: '',
  tokenExtractPath: '',
  defaultExpiresSeconds: 7200,
  remark: ''
})

const formRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  tokenType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const loadTokens = async () => {
  try {
    const res = await tokenApi.getList()
    tokenList.value = res.data
  } catch (error) {
    ElMessage.error('Failed to load tokens')
  }
}

const openDialog = (token) => {
  dialogVisible.value = true
  if (token) {
    dialogTitle.value = '编辑Token配置'
    Object.assign(tokenForm, token)
  } else {
    dialogTitle.value = '新建Token配置'
    tokenForm.id = null
  }
}

const saveToken = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await tokenApi.save(tokenForm)
        ElMessage.success('Token saved successfully')
        dialogVisible.value = false
        loadTokens()
      } catch (error) {
        ElMessage.error('Failed to save token')
      }
    }
  })
}

const deleteToken = async (token) => {
  try {
    await tokenApi.delete(token.id)
    ElMessage.success('Token deleted successfully')
    loadTokens()
  } catch (error) {
    ElMessage.error('Failed to delete token')
  }
}

const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

const getStatusType = (status) => {
  const typeMap = {
    'ACTIVE': 'success',
    'INACTIVE': 'info',
    'EXPIRED': 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status) => {
  const textMap = {
    'ACTIVE': '有效',
    'INACTIVE': '无效',
    'EXPIRED': '已过期'
  }
  return textMap[status] || status
}

const formatDateTime = (datetime) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString()
}

onMounted(() => {
  loadTokens()
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
