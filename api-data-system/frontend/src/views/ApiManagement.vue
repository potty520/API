<template>
  <div class="page-container">
    <div class="page-header">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索API名称或描述"
        style="width: 300px"
        clearable
        @clear="loadApis"
        @keyup.enter="loadApis"
      >
        <template #append>
          <el-button icon="Search" @click="loadApis" />
        </template>
      </el-input>
      <el-button type="primary" icon="Plus" @click="openDialog()">
        新建API配置
      </el-button>
    </div>

    <el-table :data="apiList" border stripe>
      <el-table-column prop="name" label="API名称" width="150" />
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column prop="apiUrl" label="API URL" min-width="200" />
      <el-table-column prop="apiMethod" label="方法" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.apiMethod === 'GET' ? 'success' : 'warning'">
            {{ scope.row.apiMethod }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="dataExtractPath" label="数据路径" width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ scope.row.status === 'ACTIVE' ? '有效' : '无效' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="200">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="openDialog(scope.row)">
            编辑
          </el-button>
          <el-button link type="danger" icon="Delete" @click="deleteApi(scope.row)">
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
      <el-form ref="formRef" :model="apiForm" :rules="formRules" label-width="140px">
        <el-form-item label="API名称" prop="name">
          <el-input v-model="apiForm.name" placeholder="请输入API名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="apiForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="关联Token配置" prop="tokenConfigId">
          <el-select v-model="apiForm.tokenConfigId" placeholder="请选择Token配置" style="width: 100%">
            <el-option v-for="token in tokenList" :key="token.id" :label="token.name" :value="token.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="API URL" prop="apiUrl">
          <el-input v-model="apiForm.apiUrl" placeholder="请输入API接口URL" />
        </el-form-item>
        <el-form-item label="请求方式" prop="apiMethod">
          <el-radio-group v-model="apiForm.apiMethod">
            <el-radio label="GET">GET</el-radio>
            <el-radio label="POST">POST</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="API参数">
          <el-input v-model="apiForm.apiParams" type="textarea" :rows="3" placeholder='{"date": "{date}"}' />
        </el-form-item>
        <el-form-item label="数据提取路径" prop="dataExtractPath">
          <el-input v-model="apiForm.dataExtractPath" placeholder="例如: $.data.list" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveApi">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { apiConfigApi, tokenApi } from '@/api'

const searchKeyword = ref('')
const apiList = ref([])
const tokenList = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新建API配置')
const formRef = ref(null)

const apiForm = reactive({
  id: null,
  name: '',
  description: '',
  tokenConfigId: null,
  apiUrl: '',
  apiMethod: 'GET',
  apiParams: '',
  dataExtractPath: ''
})

const formRules = {
  name: [{ required: true, message: '请输入API名称', trigger: 'blur' }],
  apiUrl: [{ required: true, message: '请输入API URL', trigger: 'blur' }]
}

const loadApis = async () => {
  try {
    const res = await apiConfigApi.getList({
      pageNum: 1,
      pageSize: 100,
      keyword: searchKeyword.value
    })
    apiList.value = res.data.records
  } catch (error) {
    ElMessage.error('Failed to load APIs')
  }
}

const loadTokens = async () => {
  try {
    const res = await tokenApi.getList()
    tokenList.value = res.data
  } catch (error) {
    console.error('Failed to load tokens', error)
  }
}

const openDialog = (api) => {
  dialogVisible.value = true
  if (api) {
    dialogTitle.value = '编辑API配置'
    Object.assign(apiForm, api)
  } else {
    dialogTitle.value = '新建API配置'
    apiForm.id = null
  }
}

const saveApi = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await apiConfigApi.save(apiForm)
        ElMessage.success('API saved successfully')
        dialogVisible.value = false
        loadApis()
      } catch (error) {
        ElMessage.error('Failed to save API')
      }
    }
  })
}

const deleteApi = async (api) => {
  try {
    await apiConfigApi.delete(api.id)
    ElMessage.success('API deleted successfully')
    loadApis()
  } catch (error) {
    ElMessage.error('Failed to delete API')
  }
}

const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

onMounted(() => {
  loadApis()
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
