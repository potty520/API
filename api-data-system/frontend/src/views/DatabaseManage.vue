<template>
  <div class="database-manage">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 数据库连接管理 -->
      <el-tab-pane label="连接管理" name="connections">
        <div class="toolbar">
          <el-button type="primary" @click="showConnectionDialog = true">新建连接</el-button>
          <el-input v-model="connectionKeyword" placeholder="搜索连接..." style="width: 200px; margin-left: 10px;" clearable />
        </div>

        <el-table :data="filteredConnections" border style="margin-top: 15px;">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="name" label="连接名称" />
          <el-table-column prop="host" label="主机" />
          <el-table-column prop="port" label="端口" width="80" />
          <el-table-column prop="databaseName" label="数据库" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
                {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="editConnection(row)">编辑</el-button>
              <el-button size="small" type="success" link @click="testConn(row)">测试</el-button>
              <el-button size="small" type="danger" link @click="delConnection(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 表管理 -->
      <el-tab-pane label="表管理" name="tables">
        <div class="toolbar">
          <el-input v-model="tableKeyword" placeholder="搜索表名..." style="width: 200px;" clearable @change="loadTables" />
          <el-button type="primary" style="margin-left: 10px;" @click="showCreateTableDialog = true">新建表</el-button>
          <el-button @click="loadTables">刷新</el-button>
        </div>

        <el-table :data="tables" border style="margin-top: 15px;" v-loading="loading">
          <el-table-column prop="tableName" label="表名" />
          <el-table-column prop="tableComment" label="说明" />
          <el-table-column prop="engine" label="引擎" width="80" />
          <el-table-column prop="rowCount" label="行数" width="100" />
          <el-table-column label="大小" width="120">
            <template #default="{ row }">
              {{ formatSize(row.dataLength + row.indexLength) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="primary" link @click="viewColumns(row.tableName)">字段</el-button>
              <el-button size="small" type="info" link @click="previewData(row.tableName)">预览</el-button>
              <el-button size="small" type="warning" link @click="viewCreateSql(row.tableName)">DDL</el-button>
              <el-button size="small" type="danger" link @click="dropTable(row.tableName)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- SQL执行 -->
      <el-tab-pane label="SQL执行" name="sql">
        <div class="sql-editor">
          <el-input v-model="sqlContent" type="textarea" :rows="8" placeholder="输入SQL语句..." />
          <div class="sql-toolbar">
            <el-button type="primary" @click="executeSql" :loading="sqlLoading">执行</el-button>
            <el-button @click="sqlContent = ''">清空</el-button>
          </div>
        </div>

        <div v-if="sqlResult" class="sql-result">
          <div class="result-summary">
            <span v-if="sqlResult.hasResultSet">返回 {{ sqlResult.rowCount }} 行</span>
            <span v-else>影响 {{ sqlResult.affectedRows }} 行</span>
          </div>
          <el-table v-if="sqlResult.hasResultSet && sqlResult.rows" :data="sqlResult.rows" border max-height="400">
            <el-table-column v-for="header in sqlResult.headers" :key="header" :prop="header" :label="header" />
          </el-table>
          <pre v-else-if="sqlResult.message" class="error-message">{{ sqlResult.message }}</pre>
        </div>
      </el-tab-pane>

      <!-- 数据库信息 -->
      <el-tab-pane label="数据库信息" name="info">
        <el-descriptions :column="2" border v-if="dbInfo">
          <el-descriptions-item label="数据库产品">{{ dbInfo.productName }}</el-descriptions-item>
          <el-descriptions-item label="数据库版本">{{ dbInfo.productVersion }}</el-descriptions-item>
          <el-descriptions-item label="驱动名称">{{ dbInfo.driverName }}</el-descriptions-item>
          <el-descriptions-item label="驱动版本">{{ dbInfo.driverVersion }}</el-descriptions-item>
          <el-descriptions-item label="当前数据库">{{ dbInfo.catalog }}</el-descriptions-item>
          <el-descriptions-item label="表数量">{{ dbInfo.tableCount }}</el-descriptions-item>
          <el-descriptions-item label="JDBC URL" :span="2">{{ dbInfo.url }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>

    <!-- 连接编辑对话框 -->
    <el-dialog v-model="showConnectionDialog" :title="editingConnection.id ? '编辑连接' : '新建连接'" width="500px">
      <el-form :model="editingConnection" label-width="100px">
        <el-form-item label="连接名称">
          <el-input v-model="editingConnection.name" />
        </el-form-item>
        <el-form-item label="主机">
          <el-input v-model="editingConnection.host" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="editingConnection.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="数据库名">
          <el-input v-model="editingConnection.databaseName" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="editingConnection.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="editingConnection.password" show-password />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editingConnection.remark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showConnectionDialog = false">取消</el-button>
        <el-button type="primary" @click="saveConn">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字段查看对话框 -->
    <el-dialog v-model="showColumnsDialog" title="表字段" width="800px">
      <el-table :data="columns" border>
        <el-table-column prop="columnName" label="字段名" />
        <el-table-column prop="columnType" label="类型" />
        <el-table-column prop="columnComment" label="说明" />
        <el-table-column prop="isNullable" label="可空" width="60" />
        <el-table-column prop="columnKey" label="键" width="60">
          <template #default="{ row }">
            <el-tag v-if="row.columnKey === 'PRI'" type="danger" size="small">PRI</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="columnDefault" label="默认值" />
        <el-table-column prop="extra" label="额外" />
      </el-table>
    </el-dialog>

    <!-- 数据预览对话框 -->
    <el-dialog v-model="showPreviewDialog" :title="'预览: ' + previewTable" width="90%">
      <el-table :data="previewDataList" border height="400">
        <el-table-column v-for="col in previewColumns" :key="col" :prop="col" :label="col" />
      </el-table>
      <div style="margin-top: 15px; text-align: right;">
        <el-pagination
          v-model:current-page="previewPage"
          :page-size="previewPageSize"
          :total="previewTotal"
          layout="prev, pager, next"
          @current-change="loadPreviewData"
        />
      </div>
    </el-dialog>

    <!-- DDL查看对话框 -->
    <el-dialog v-model="showDDLDialog" title="建表语句" width="800px">
      <pre class="ddl-content">{{ createTableSql }}</pre>
    </el-dialog>

    <!-- 新建表对话框 -->
    <el-dialog v-model="showCreateTableDialog" title="新建表" width="600px">
      <el-input v-model="newTableSql" type="textarea" :rows="10" placeholder="输入建表SQL..." />
      <template #footer>
        <el-button @click="showCreateTableDialog = false">取消</el-button>
        <el-button type="primary" @click="doCreateTable">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '@/api/database'

const activeTab = ref('connections')
const loading = ref(false)

// 连接管理
const connections = ref([])
const connectionKeyword = ref('')
const showConnectionDialog = ref(false)
const editingConnection = ref({ port: 3306, dbType: 'MYSQL', charset: 'utf8mb4', status: 'ACTIVE' })

const filteredConnections = computed(() => {
  if (!connectionKeyword.value) return connections.value
  return connections.value.filter(c =>
    c.name.includes(connectionKeyword.value) || c.host.includes(connectionKeyword.value)
  )
})

// 表管理
const tables = ref([])
const tableKeyword = ref('')
const columns = ref([])
const showColumnsDialog = ref(false)

// 数据预览
const previewTable = ref('')
const previewDataList = ref([])
const previewColumns = ref([])
const previewTotal = ref(0)
const previewPage = ref(1)
const previewPageSize = ref(20)
const showPreviewDialog = ref(false)

// DDL
const createTableSql = ref('')
const showDDLDialog = ref(false)

// 新建表
const newTableSql = ref('')
const showCreateTableDialog = ref(false)

// SQL执行
const sqlContent = ref('')
const sqlResult = ref(null)
const sqlLoading = ref(false)

// 数据库信息
const dbInfo = ref(null)

function handleTabChange(tab) {
  if (tab === 'tables') loadTables()
  if (tab === 'info') loadDbInfo()
}

async function loadConnections() {
  try {
    const res = await api.getConnections({ pageNum: 1, pageSize: 100 })
    connections.value = res.data.records || []
  } catch (e) {
    ElMessage.error('加载连接列表失败')
  }
}

function editConnection(conn) {
  editingConnection.value = { ...conn }
  showConnectionDialog.value = true
}

async function saveConn() {
  try {
    await api.saveConnection(editingConnection.value)
    ElMessage.success('保存成功')
    showConnectionDialog.value = false
    loadConnections()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function testConn(conn) {
  try {
    await api.testConnection(conn)
    ElMessage.success('连接成功')
  } catch (e) {
    ElMessage.error('连接失败: ' + e.message)
  }
}

async function delConnection(id) {
  try {
    await ElMessageBox.confirm('确认删除该连接?', '提示')
    await api.deleteConnection(id)
    ElMessage.success('删除成功')
    loadConnections()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function loadTables() {
  loading.value = true
  try {
    const res = await api.getTables({ keyword: tableKeyword.value })
    tables.value = res.data || []
  } catch (e) {
    ElMessage.error('加载表列表失败')
  } finally {
    loading.value = false
  }
}

async function viewColumns(tableName) {
  try {
    const res = await api.getColumns(tableName)
    columns.value = res.data || []
    showColumnsDialog.value = true
  } catch (e) {
    ElMessage.error('加载字段失败')
  }
}

async function previewData(tableName) {
  previewTable.value = tableName
  previewPage.value = 1
  await loadPreviewData()
  showPreviewDialog.value = true
}

async function loadPreviewData() {
  try {
    const res = await api.previewTableData(previewTable.value, {
      page: previewPage.value,
      pageSize: previewPageSize.value
    })
    const data = res.data
    previewDataList.value = data.rows || []
    previewTotal.value = data.total || 0
    if (previewDataList.value.length > 0) {
      previewColumns.value = Object.keys(previewDataList.value[0])
    }
  } catch (e) {
    ElMessage.error('加载数据失败')
  }
}

async function viewCreateSql(tableName) {
  try {
    const res = await api.getCreateTableSql(tableName)
    createTableSql.value = res.data || ''
    showDDLDialog.value = true
  } catch (e) {
    ElMessage.error('获取DDL失败')
  }
}

async function dropTable(tableName) {
  try {
    await ElMessageBox.confirm(`确认删除表 ${tableName}? 此操作不可恢复!`, '警告', { type: 'warning' })
    await api.dropTable(tableName)
    ElMessage.success('删除成功')
    loadTables()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function executeSql() {
  if (!sqlContent.value.trim()) {
    ElMessage.warning('请输入SQL语句')
    return
  }
  sqlLoading.value = true
  sqlResult.value = null
  try {
    const res = await api.executeSql({ sql: sqlContent.value })
    sqlResult.value = res.data
    if (res.code !== 200) {
      ElMessage.error(res.message)
    }
  } catch (e) {
    ElMessage.error('执行失败')
  } finally {
    sqlLoading.value = false
  }
}

async function doCreateTable() {
  if (!newTableSql.value.trim()) {
    ElMessage.warning('请输入建表SQL')
    return
  }
  try {
    await api.createTable({ sql: newTableSql.value })
    ElMessage.success('创建成功')
    showCreateTableDialog.value = false
    newTableSql.value = ''
    loadTables()
  } catch (e) {
    ElMessage.error('创建失败: ' + e.message)
  }
}

async function loadDbInfo() {
  try {
    const res = await api.getDatabaseInfo()
    dbInfo.value = res.data
  } catch (e) {
    ElMessage.error('加载数据库信息失败')
  }
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

onMounted(() => {
  loadConnections()
})
</script>

<style scoped>
.database-manage {
  padding: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
}

.sql-editor {
  margin-bottom: 20px;
}

.sql-toolbar {
  margin-top: 10px;
}

.sql-result {
  margin-top: 20px;
}

.result-summary {
  margin-bottom: 10px;
  color: #67c23a;
  font-weight: bold;
}

.error-message {
  background: #fef0f0;
  color: #f56c6c;
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
}

.ddl-content {
  background: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
