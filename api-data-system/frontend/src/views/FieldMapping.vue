<template>
  <div class="page-container">
    <el-alert
      title="字段映射说明"
      type="info"
      :closable="false"
      style="margin-bottom: 20px;"
    >
      在此配置API响应字段与目标表字段的映射关系。请至少选择一个字段作为业务唯一键用于去重。
    </el-alert>

    <el-form :inline="true" style="margin-bottom: 20px;">
      <el-form-item label="选择API配置">
        <el-select v-model="selectedApiId" placeholder="请选择API配置" style="width: 300px" @change="loadFieldMappings">
          <el-option v-for="api in apiList" :key="api.id" :label="api.name" :value="api.id" />
        </el-select>
      </el-form-item>
    </el-form>

    <el-table v-if="selectedApiId" :data="fieldMappings" border stripe>
      <el-table-column label="序号" width="60">
        <template #default="scope">
          {{ scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column label="源字段" min-width="150">
        <template #default="scope">
          <el-input v-model="scope.row.sourceField" placeholder="源字段名" />
        </template>
      </el-table-column>
      <el-table-column label="JSONPath" min-width="150">
        <template #default="scope">
          <el-input v-model="scope.row.sourceJsonPath" placeholder="$.field" />
        </template>
      </el-table-column>
      <el-table-column label="目标字段" min-width="150">
        <template #default="scope">
          <el-input v-model="scope.row.targetField" placeholder="目标表字段" />
        </template>
      </el-table-column>
      <el-table-column label="字段类型" width="150">
        <template #default="scope">
          <el-select v-model="scope.row.targetType" style="width: 100%">
            <el-option label="VARCHAR(255)" value="VARCHAR(255)" />
            <el-option label="TEXT" value="TEXT" />
            <el-option label="INT" value="INT" />
            <el-option label="BIGINT" value="BIGINT" />
            <el-option label="DATETIME" value="DATETIME" />
            <el-option label="DECIMAL" value="DECIMAL" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="唯一键" width="100" align="center">
        <template #default="scope">
          <el-checkbox v-model="scope.row.isUniqueKey" :true-value="1" :false-value="0" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="removeField(scope.$index)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="selectedApiId" style="margin-top: 20px; text-align: right;">
      <el-button icon="Plus" @click="addField">添加字段</el-button>
      <el-button type="primary" icon="Check" @click="saveFieldMappings">保存配置</el-button>
    </div>

    <el-empty v-if="!selectedApiId" description="请先选择API配置" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { apiConfigApi, fieldMappingApi } from '@/api'

const apiList = ref([])
const selectedApiId = ref(null)
const fieldMappings = ref([])

const loadApis = async () => {
  try {
    const res = await apiConfigApi.getList({ pageNum: 1, pageSize: 100 })
    apiList.value = res.data.records
  } catch (error) {
    ElMessage.error('Failed to load APIs')
  }
}

const loadFieldMappings = async () => {
  if (!selectedApiId.value) return

  try {
    const res = await fieldMappingApi.getList(selectedApiId.value)
    fieldMappings.value = res.data
  } catch (error) {
    fieldMappings.value = []
  }
}

const addField = () => {
  fieldMappings.value.push({
    sourceField: '',
    sourceJsonPath: '',
    targetField: '',
    targetType: 'VARCHAR(255)',
    isUniqueKey: 0,
    fieldOrder: fieldMappings.value.length + 1
  })
}

const removeField = (index) => {
  fieldMappings.value.splice(index, 1)
}

const saveFieldMappings = async () => {
  if (!selectedApiId.value) return

  try {
    const hasUniqueKey = fieldMappings.value.some(f => f.isUniqueKey === 1)
    if (!hasUniqueKey) {
      ElMessage.warning('请至少选择一个字段作为业务唯一键')
      return
    }

    await fieldMappingApi.save(selectedApiId.value, fieldMappings.value)
    ElMessage.success('Field mappings saved successfully')
  } catch (error) {
    ElMessage.error('Failed to save field mappings')
  }
}

onMounted(() => {
  loadApis()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
</style>
