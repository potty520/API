<template>
  <div v-if="!user" class="login-shell">
    <section class="login-brand">
      <div>
        <div class="brand-mark"><span class="brand-square">J</span><strong>JSON 数据接入中心</strong></div>
        <h1>接口数据持续同步工作台</h1>
        <p>Spring Boot · MyBatis-Plus · Quartz · MySQL</p>
      </div>
      <div class="brand-stats">
        <div><strong>4</strong><span>关系型数据库</span></div>
        <div><strong>6 位</strong><span>Quartz Cron</span></div>
        <div><strong>0</strong><span>手工建表</span></div>
      </div>
    </section>
    <section class="login-panel">
      <el-form class="login-form" :model="loginForm" @submit.prevent="login">
        <h2>数据接入工作台</h2>
        <p>使用系统账号登录</p>
        <el-form-item label="账号"><el-input v-model="loginForm.username" autocomplete="username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" @keyup.enter="login" /></el-form-item>
        <el-button type="primary" :loading="loginLoading" @click="login">登录</el-button>
        <p class="login-hint">初始账号密码由部署时的环境变量指定，或在首次启动日志中一次性输出。</p>
      </el-form>
    </section>
  </div>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-square">J</span>
        <div><strong>JSON 接入中心</strong><small>DATA PIPELINE</small></div>
      </div>
      <div class="nav-scroll">
        <template v-for="group in navGroups" :key="group.name">
          <div class="nav-group-title">{{ group.name }}</div>
          <button v-for="item in group.items" :key="item.key" class="nav-item" :class="{ active: page === item.key }" :title="item.title" @click="selectPage(item.key)">
            <el-icon><component :is="item.icon" /></el-icon><span>{{ item.title }}</span>
          </button>
        </template>
      </div>
      <div class="sidebar-user">
        <span class="avatar">{{ user.displayName.slice(0, 1) }}</span>
        <div><strong>{{ user.displayName }}</strong><span>{{ user.role }}</span></div>
        <div class="sidebar-actions">
          <el-button text circle title="修改密码" @click="openPasswordDialog"><el-icon><Key /></el-icon></el-button>
          <el-button text circle title="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></el-button>
        </div>
      </div>
    </aside>

    <main class="workspace">
      <header class="topbar">
        <div class="page-heading"><h1>{{ currentPage.title }}</h1><p>{{ currentPage.subtitle }}</p></div>
        <div class="top-actions"><span class="clock">{{ clock }}</span><el-button circle title="刷新" @click="refreshPage"><el-icon><Refresh /></el-icon></el-button></div>
      </header>
      <section class="content" v-loading="loading">
        <template v-if="page === 'dashboard'">
          <div class="kpi-grid">
            <div class="kpi"><div><span>接口任务</span><strong>{{ number(dashboard.stats.total) }}</strong><small>启用 {{ number(dashboard.stats.enabled) }}</small></div><div class="kpi-icon blue"><el-icon><Tickets /></el-icon></div></div>
            <div class="kpi"><div><span>今日执行</span><strong>{{ number(dashboard.today.runs) }}</strong><small>成功率 {{ successRate }}%</small></div><div class="kpi-icon green"><el-icon><VideoPlay /></el-icon></div></div>
            <div class="kpi"><div><span>今日写入</span><strong>{{ number((dashboard.today.inserted || 0) + (dashboard.today.updated || 0)) }}</strong><small>新增 {{ number(dashboard.today.inserted) }} / 更新 {{ number(dashboard.today.updated) }}</small></div><div class="kpi-icon amber"><el-icon><DataLine /></el-icon></div></div>
            <div class="kpi"><div><span>待处理异常</span><strong>{{ number(dashboard.alerts.length) }}</strong><small>待配主键 {{ number(dashboard.stats.pendingKey) }}</small></div><div class="kpi-icon red"><el-icon><Warning /></el-icon></div></div>
          </div>
          <div class="dashboard-grid">
            <section class="panel">
              <div class="panel-header"><div><h2>最近执行记录</h2><p>手动与调度任务</p></div><el-tag effect="plain">{{ dashboard.recent.length }} 条</el-tag></div>
              <div class="panel-body">
                <div v-for="run in dashboard.recent" :key="run.id" class="run-row"><span class="signal" :class="run.status === '成功' ? '' : run.status === '失败' ? 'fail' : 'warn'"></span><div><strong>{{ run.taskName }} · {{ run.syncMode }}</strong><p>拉取 {{ number(run.totalCount) }}，新增 {{ number(run.insertedCount) }}，更新 {{ number(run.updatedCount) }}，跳过 {{ number(run.skippedCount) }} · {{ number(run.durationMs) }}ms</p></div><time>{{ dateTime(run.startedAt) }}</time></div>
                <el-empty v-if="!dashboard.recent.length" description="尚无执行记录" :image-size="64" />
              </div>
            </section>
            <section class="panel">
              <div class="panel-header"><div><h2>异常告警</h2><p>失败与配置风险</p></div></div>
              <div class="panel-body">
                <div v-for="alert in dashboard.alerts" :key="alert.id" class="run-row"><span class="signal" :class="alert.levelName === '错误' ? 'fail' : 'warn'"></span><div><strong>{{ alert.title }}</strong><p>{{ alert.taskName }} · {{ alert.message }}</p></div><time>{{ dateTime(alert.createdAt) }}</time></div>
                <el-empty v-if="!dashboard.alerts.length" description="当前没有未处理告警" :image-size="64" />
              </div>
            </section>
          </div>
        </template>

        <template v-else-if="page === 'tasks'">
          <div class="toolbar">
            <div class="filters">
              <el-input v-model="taskFilters.search" class="wide-filter" clearable placeholder="任务名称、编码或 URL" @keyup.enter="loadTasks" />
              <el-select v-model="taskFilters.status" clearable placeholder="全部状态"><el-option v-for="status in taskStatuses" :key="status" :label="status" :value="status" /></el-select>
              <el-button @click="loadTasks">查询</el-button>
            </div>
            <el-button v-if="can('tasks')" type="primary" @click="openTaskDialog()"><el-icon><Plus /></el-icon>新增接口任务</el-button>
          </div>
          <el-table :data="tasks" stripe>
            <el-table-column label="任务" min-width="210"><template #default="{ row }"><div class="table-primary">{{ row.name }}</div><div class="table-secondary mono">{{ row.code }} · {{ row.groupName }}</div></template></el-table-column>
            <el-table-column label="接口" min-width="310"><template #default="{ row }"><el-tag class="method-tag" size="small" effect="plain">{{ row.method }}</el-tag><span class="table-url"> {{ row.url }}</span></template></el-table-column>
            <el-table-column label="目标" min-width="190"><template #default="{ row }">{{ row.datasourceName }}<div class="table-secondary mono">{{ row.tableName }}</div></template></el-table-column>
            <el-table-column prop="uniqueKey" label="唯一 Key" width="120"><template #default="{ row }"><span class="mono">{{ row.uniqueKey || '未配置' }}</span></template></el-table-column>
            <el-table-column label="Cron / 下次执行" min-width="190"><template #default="{ row }"><span class="mono">{{ row.cronExpr }}</span><div class="table-secondary">{{ dateTime(row.nextRunAt) }}</div></template></el-table-column>
            <el-table-column label="调度" width="76"><template #default="{ row }"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag></template></el-table-column>
            <el-table-column label="状态" width="112"><template #default="{ row }"><el-tag size="small" :type="statusType(runningTaskIds.includes(row.id) ? '执行中' : row.status)">{{ runningTaskIds.includes(row.id) ? '执行中' : row.status }}</el-tag></template></el-table-column>
            <el-table-column prop="runCount" label="执行次数" width="82" />
            <el-table-column label="操作" fixed="right" width="250"><template #default="{ row }"><div class="row-actions"><el-button v-if="can('execute')" size="small" type="primary" plain :disabled="runningTaskIds.includes(row.id)" @click="executeTask(row)">执行</el-button><el-button size="small" @click="showTaskDetail(row.id)">详情</el-button><el-button v-if="can('tasks')" size="small" @click="openTaskDialog(row)">编辑</el-button><el-button v-if="can('tasks')" size="small" @click="toggleTask(row)">{{ row.enabled ? '停用' : '启用' }}</el-button></div></template></el-table-column>
          </el-table>
        </template>

        <template v-else-if="page === 'datasources'">
          <div class="toolbar"><div></div><el-button type="primary" @click="openSourceDialog()"><el-icon><Plus /></el-icon>新增数据源</el-button></div>
          <el-table :data="sources" stripe>
            <el-table-column label="数据源" min-width="190"><template #default="{ row }"><div class="table-primary">{{ row.name }}</div><div class="table-secondary mono">{{ row.code }}</div></template></el-table-column>
            <el-table-column label="类型" width="120"><template #default="{ row }"><el-tag effect="plain">{{ dbLabel(row.dbType) }}</el-tag></template></el-table-column>
            <el-table-column label="连接目标" min-width="280"><template #default="{ row }"><span class="mono">{{ sourceTarget(row) }}</span></template></el-table-column>
            <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '禁用' }}</el-tag></template></el-table-column>
            <el-table-column label="最近测试" min-width="150"><template #default="{ row }"><el-tag size="small" :type="row.lastTestStatus === '成功' ? 'success' : row.lastTestStatus === '失败' ? 'danger' : 'info'">{{ row.lastTestStatus }}</el-tag><span class="muted"> {{ row.lastTestMessage }}</span></template></el-table-column>
            <el-table-column label="操作" fixed="right" width="150"><template #default="{ row }"><el-button size="small" type="primary" plain @click="testSource(row)">测试</el-button><el-button size="small" @click="openSourceDialog(row)">编辑</el-button></template></el-table-column>
          </el-table>
        </template>

        <template v-else-if="page === 'runs'">
          <div class="toolbar">
            <div class="filters"><el-select v-model="runFilters.taskId" clearable placeholder="全部任务"><el-option v-for="task in tasks" :key="task.id" :label="task.name" :value="task.id" /></el-select><el-select v-model="runFilters.status" clearable placeholder="全部状态"><el-option v-for="status in ['成功','失败','部分成功','执行中']" :key="status" :label="status" :value="status" /></el-select><el-button @click="loadRuns">查询</el-button></div>
            <el-button @click="exportRuns"><el-icon><Download /></el-icon>导出 CSV</el-button>
          </div>
          <el-table :data="runs" stripe>
            <el-table-column prop="runNo" label="执行编号" min-width="190"><template #default="{ row }"><span class="mono strong">{{ row.runNo }}</span></template></el-table-column>
            <el-table-column prop="taskName" label="任务" min-width="150" />
            <el-table-column prop="triggerType" label="触发方式" width="110" />
            <el-table-column label="同步模式" width="110"><template #default="{ row }"><el-tag size="small" effect="plain">{{ row.syncMode }}</el-tag></template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="拉取 / 新增 / 更新" min-width="150"><template #default="{ row }">{{ number(row.totalCount) }} / {{ number(row.insertedCount) }} / {{ number(row.updatedCount) }}</template></el-table-column>
            <el-table-column label="跳过 / 失败" width="120"><template #default="{ row }">{{ number(row.skippedCount) }} / {{ number(row.failedCount) }}</template></el-table-column>
            <el-table-column label="耗时" width="90"><template #default="{ row }">{{ number(row.durationMs) }}ms</template></el-table-column>
            <el-table-column label="开始时间" min-width="165"><template #default="{ row }">{{ dateTime(row.startedAt) }}</template></el-table-column>
            <el-table-column label="详情" fixed="right" width="75"><template #default="{ row }"><el-button size="small" @click="showRunDetail(row.id)">查看</el-button></template></el-table-column>
          </el-table>
        </template>

        <template v-else-if="page === 'data'">
          <div class="toolbar"><div class="filters"><el-select v-model="dataTaskId" class="wide-filter" placeholder="选择任务" @change="loadData"><el-option v-for="task in tasks" :key="task.id" :label="`${task.name} · ${task.tableName}`" :value="task.id" /></el-select></div><span class="muted">目标表：<span class="mono">{{ dataPreview.table || '-' }}</span></span></div>
          <div class="data-table-block"><div class="section-title"><div><h2>主表数据</h2><p class="mono">{{ dataPreview.table }}</p></div><el-tag effect="plain">{{ dataPreview.items.length }} 条</el-tag></div><dynamic-table :rows="dataPreview.items" /></div>
          <div v-for="child in dataPreview.childTables" :key="child.table" class="data-table-block"><div class="section-title"><div><h2>子表数据</h2><p class="mono">{{ child.table }}</p></div><el-tag effect="plain">{{ child.items.length }} 条</el-tag></div><dynamic-table :rows="child.items" /></div>
          <div class="section-title"><div><h2>结构版本</h2><p>主表字段历史</p></div></div>
          <el-table :data="schemaVersions" stripe><el-table-column prop="versionNo" label="版本" width="80"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column><el-table-column prop="tableName" label="表名" min-width="150" /><el-table-column label="字段数" width="90"><template #default="{ row }">{{ Object.keys(row.structure || {}).length }}</template></el-table-column><el-table-column prop="batchId" label="批次" min-width="190" /><el-table-column label="创建时间" min-width="165"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column></el-table>
        </template>

        <template v-else-if="page === 'alerts'">
          <el-table :data="alerts" stripe>
            <el-table-column label="等级" width="90"><template #default="{ row }"><el-tag :type="row.levelName === '错误' ? 'danger' : 'warning'">{{ row.levelName }}</el-tag></template></el-table-column>
            <el-table-column prop="title" label="告警标题" min-width="190" />
            <el-table-column prop="taskName" label="任务" min-width="150" />
            <el-table-column prop="message" label="详情" min-width="360" />
            <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === '已处理' ? 'success' : 'warning'">{{ row.status }}</el-tag></template></el-table-column>
            <el-table-column label="发生时间" min-width="165"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column>
            <el-table-column label="操作" fixed="right" width="85"><template #default="{ row }"><el-button v-if="row.status === '未处理'" size="small" @click="resolveAlert(row)">处理</el-button><span v-else>-</span></template></el-table-column>
          </el-table>
        </template>

        <template v-else-if="page === 'settings'">
          <div class="settings-grid">
            <section class="panel"><div class="panel-header"><div><h2>系统参数</h2><p>调度与入库策略</p></div></div><el-form class="settings-form" label-width="150px"><el-form-item v-for="setting in settingsData.items" :key="setting.settingKey" :label="setting.description"><el-input v-model="settingsForm[setting.settingKey]" :disabled="!canEditSetting(setting.settingKey)" /><span v-if="!canEditSetting(setting.settingKey)" class="field-note">涉及外发地址或建表语句，仅管理员可修改</span></el-form-item><el-form-item><el-button type="primary" @click="saveSettings">保存参数</el-button></el-form-item></el-form></section>
            <section class="panel"><div class="panel-header"><div><h2>角色账号</h2><p>权限隔离</p></div></div><el-table :data="settingsData.users"><el-table-column prop="username" label="账号" /><el-table-column prop="displayName" label="姓名" /><el-table-column prop="roleName" label="角色" /><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '禁用' }}</el-tag><el-tag v-if="row.mustChangePassword" type="warning" size="small">待改密</el-tag></template></el-table-column><el-table-column v-if="can('*')" label="操作" width="110"><template #default="{ row }"><el-button size="small" text type="primary" @click="resetUserPassword(row)">重置密码</el-button></template></el-table-column></el-table></section>
          </div>
          <div class="section-title"><div><h2>审计日志</h2><p>配置与执行操作</p></div></div>
          <el-table :data="settingsData.audits" stripe><el-table-column prop="username" label="用户" width="100" /><el-table-column prop="actionName" label="操作" min-width="150" /><el-table-column prop="moduleName" label="模块" width="110" /><el-table-column prop="detailText" label="详情" min-width="220" /><el-table-column label="时间" min-width="165"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column></el-table>
        </template>
      </section>
    </main>

    <el-dialog v-model="taskDialogVisible" :title="taskForm.id ? '编辑接口任务' : '新增接口任务'" width="860px" destroy-on-close>
      <el-tabs v-model="taskTab">
        <el-tab-pane label="接口" name="base"><el-form label-position="top"><div class="dialog-grid"><el-form-item label="任务名称"><el-input v-model="taskForm.name" /></el-form-item><el-form-item label="唯一编码"><el-input v-model="taskForm.code" /></el-form-item><el-form-item label="任务分组"><el-select v-model="taskForm.groupId" clearable><el-option v-for="group in groups" :key="group.id" :label="group.name" :value="group.id" /></el-select></el-form-item><el-form-item label="请求方式"><el-select v-model="taskForm.method"><el-option label="GET" value="GET" /><el-option label="POST" value="POST" /></el-select></el-form-item><el-form-item label="接口 URL" class="wide"><el-input v-model="taskForm.url" /></el-form-item><el-form-item label="认证方式"><el-select v-model="taskForm.authType"><el-option label="无认证" value="none" /><el-option label="Basic" value="basic" /><el-option label="表单账号密码" value="form" /><el-option label="Token" value="token" /></el-select></el-form-item><el-form-item label="定时调度"><el-switch v-model="taskForm.enabled" /></el-form-item><el-form-item label="描述" class="wide"><el-input v-model="taskForm.description" type="textarea" :rows="2" /></el-form-item></div></el-form></el-tab-pane>
        <el-tab-pane label="请求参数" name="request"><el-form label-position="top"><el-form-item label="Headers JSON" class="json-editor"><el-input v-model="taskForm.headersText" type="textarea" :rows="5" /></el-form-item><el-form-item label="Query JSON" class="json-editor"><el-input v-model="taskForm.queryText" type="textarea" :rows="5" /></el-form-item><el-form-item label="Body JSON" class="json-editor"><el-input v-model="taskForm.bodyText" type="textarea" :rows="5" /></el-form-item><el-form-item label="认证配置 JSON" class="json-editor"><el-input v-model="taskForm.authText" type="textarea" :rows="6" /></el-form-item></el-form></el-tab-pane>
        <el-tab-pane label="入库与调度" name="target"><el-form label-position="top"><div class="dialog-grid"><el-form-item label="目标数据源"><el-select v-model="taskForm.datasourceId"><el-option v-for="source in sources" :key="source.id" :label="`${source.name} · ${dbLabel(source.dbType)}`" :value="source.id" /></el-select></el-form-item><el-form-item label="目标表名"><el-input v-model="taskForm.tableName" /></el-form-item><el-form-item label="数据根节点"><el-input v-model="taskForm.rootPath" /></el-form-item><el-form-item label="业务唯一 Key"><el-input v-model="taskForm.uniqueKey" /></el-form-item><el-form-item label="Cron 表达式" class="wide"><el-input v-model="taskForm.cronExpr" /><div class="cron-presets"><el-button v-for="preset in cronPresets" :key="preset.value" size="small" @click="taskForm.cronExpr = preset.value">{{ preset.label }}</el-button></div></el-form-item><el-form-item label="重试次数"><el-input-number v-model="taskForm.retryCount" :min="0" :max="10" /></el-form-item><el-form-item label="重试间隔（秒）"><el-input-number v-model="taskForm.retryIntervalSec" :min="0" :max="3600" /></el-form-item><el-form-item label="超时时间（秒）"><el-input-number v-model="taskForm.timeoutSec" :min="1" :max="600" /></el-form-item><el-form-item label="数据来源标识"><el-input v-model="taskForm.sourceLabel" /></el-form-item></div></el-form></el-tab-pane>
      </el-tabs>
      <template #footer><el-button @click="taskDialogVisible = false">取消</el-button><el-button type="primary" :loading="dialogSaving" @click="saveTask">保存配置</el-button></template>
    </el-dialog>

    <el-dialog v-model="sourceDialogVisible" :title="sourceForm.id ? '编辑数据源' : '新增数据源'" width="760px">
      <el-form label-position="top"><div class="dialog-grid"><el-form-item label="数据源名称"><el-input v-model="sourceForm.name" /></el-form-item><el-form-item label="数据源编码"><el-input v-model="sourceForm.code" /></el-form-item><el-form-item label="数据库类型"><el-select v-model="sourceForm.dbType"><el-option v-for="db in databaseTypes" :key="db.value" :label="db.label" :value="db.value" /></el-select></el-form-item><el-form-item label="状态"><el-switch v-model="sourceForm.active" /></el-form-item><el-form-item label="主机地址"><el-input v-model="sourceForm.host" /></el-form-item><el-form-item label="端口"><el-input-number v-model="sourceForm.port" :min="1" :max="65535" /></el-form-item><el-form-item label="数据库名"><el-input v-model="sourceForm.databaseName" /></el-form-item><el-form-item label="Schema"><el-input v-model="sourceForm.schemaName" /></el-form-item><el-form-item label="Oracle 服务名"><el-input v-model="sourceForm.serviceName" /></el-form-item><el-form-item label="用户名"><el-input v-model="sourceForm.username" /></el-form-item><el-form-item label="密码"><el-input v-model="sourceForm.password" type="password" show-password :placeholder="sourceForm.id ? '留空保持原密码' : ''" /></el-form-item><el-form-item label="完整 JDBC URL"><el-input v-model="sourceForm.jdbcUrl" /></el-form-item><el-form-item label="连接选项 JSON" class="wide json-editor"><el-input v-model="sourceForm.optionsText" type="textarea" :rows="5" /></el-form-item></div></el-form>
      <template #footer><el-button @click="sourceDialogVisible = false">取消</el-button><el-button type="primary" :loading="dialogSaving" @click="saveSource">保存数据源</el-button></template>
    </el-dialog>

    <el-dialog v-model="taskDetailVisible" title="接口任务详情" width="900px">
      <template v-if="detailTask">
        <div class="summary-grid"><div class="summary-item"><span>任务编码</span><strong class="mono">{{ detailTask.code }}</strong></div><div class="summary-item"><span>目标表</span><strong class="mono">{{ detailTask.tableName }}</strong></div><div class="summary-item"><span>同步状态</span><strong><el-tag :type="statusType(detailTask.status)">{{ detailTask.status }}</el-tag></strong></div><div class="summary-item"><span>下次执行</span><strong>{{ dateTime(detailTask.nextRunAt) }}</strong></div></div>
        <div class="dialog-actions"><el-button type="primary" plain :loading="testingTask" @click="testTask(detailTask.id)">测试接口与解析</el-button><el-button v-if="can('execute')" type="primary" @click="executeTask(detailTask)">立即执行</el-button><el-button v-if="can('full')" type="danger" plain @click="fullRefresh(detailTask)">强制全量重刷</el-button><el-button v-if="can('data')" @click="viewTaskData(detailTask.id)">查看目标数据</el-button></div>
        <template v-if="testResult"><div class="summary-grid"><div class="summary-item"><span>HTTP 状态</span><strong>{{ testResult.httpStatus }}</strong></div><div class="summary-item"><span>响应耗时</span><strong>{{ testResult.responseMs }}ms</strong></div><div class="summary-item"><span>数据根节点</span><strong class="mono">{{ testResult.detectedRoot }}</strong></div><div class="summary-item"><span>记录数</span><strong>{{ testResult.totalCount }}</strong></div></div><div class="section-title"><h2>主表识别字段</h2></div><field-table :columns="testResult.columns" /><div v-for="(child, name) in testResult.childTables" :key="name"><div class="section-title"><div><h2>子表 {{ name }}</h2><p>{{ child.count }} 条明细</p></div></div><field-table :columns="child.columns" /></div><div class="section-title"><h2>响应预览</h2></div><pre class="code-block">{{ testResult.rawPreview }}</pre></template>
        <div class="section-title"><div><h2>结构版本</h2><p>只新增字段</p></div></div><el-table :data="detailSchema"><el-table-column prop="versionNo" label="版本" width="80"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column><el-table-column prop="tableName" label="表名" /><el-table-column prop="batchId" label="批次" /><el-table-column label="创建时间"><template #default="{ row }">{{ dateTime(row.createdAt) }}</template></el-table-column></el-table>
      </template>
    </el-dialog>

    <el-dialog v-model="runDetailVisible" title="执行日志详情" width="760px">
      <template v-if="runDetail"><div class="summary-grid"><div class="summary-item"><span>状态</span><strong><el-tag :type="statusType(runDetail.status)">{{ runDetail.status }}</el-tag></strong></div><div class="summary-item"><span>同步模式</span><strong>{{ runDetail.syncMode }}</strong></div><div class="summary-item"><span>尝试次数</span><strong>{{ runDetail.attemptCount }}</strong></div><div class="summary-item"><span>HTTP / 响应</span><strong>{{ runDetail.httpStatus || '-' }} / {{ runDetail.responseMs }}ms</strong></div></div><div class="section-title"><h2>数据统计</h2></div><div class="summary-grid"><div class="summary-item"><span>拉取</span><strong>{{ runDetail.totalCount }}</strong></div><div class="summary-item"><span>新增</span><strong>{{ runDetail.insertedCount }}</strong></div><div class="summary-item"><span>更新</span><strong>{{ runDetail.updatedCount }}</strong></div><div class="summary-item"><span>跳过</span><strong>{{ runDetail.skippedCount }}</strong></div></div><div class="section-title"><h2>结构变更</h2></div><pre class="code-block">{{ pretty(runDetail.schemaChanges) }}</pre><template v-if="runDetail.errorMessage"><div class="section-title"><h2>异常详情</h2></div><el-alert :title="runDetail.errorMessage" type="error" :closable="false" /><pre v-if="runDetail.errorStack" class="code-block">{{ runDetail.errorStack }}</pre></template></template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" :title="mustChangePassword ? '首次登录必须修改密码' : '修改密码'" width="460px"
               :close-on-click-modal="false" :close-on-press-escape="!mustChangePassword" :show-close="!mustChangePassword">
      <el-form label-position="top" @submit.prevent="submitPasswordChange">
        <el-form-item label="原密码"><el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="确认新密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.enter="submitPasswordChange" /></el-form-item>
        <p class="dialog-hint">至少 8 位，且必须同时包含字母和数字。修改成功后当前浏览器自动续用新会话，其他设备需重新登录。</p>
      </el-form>
      <template #footer>
        <el-button v-if="!mustChangePassword" @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="submitPasswordChange">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElButton, ElEmpty, ElMessage, ElMessageBox, ElTable, ElTableColumn } from 'element-plus'
import { api, download, getToken, setToken } from './api'

const DynamicTable = defineComponent({
  props: { rows: { type: Array, default: () => [] } },
  setup(props) { return () => props.rows.length ? h(ElTable, { data: props.rows, stripe: true }, () => Object.keys(props.rows[0]).map(column => h(ElTableColumn, { prop: column, label: column, minWidth: 130, showOverflowTooltip: true }))) : h(ElEmpty, { description: '当前没有数据', imageSize: 64 }) }
})
const FieldTable = defineComponent({
  props: { columns: { type: Object, default: () => ({}) } },
  setup(props) { return () => h(ElTable, { data: Object.entries(props.columns).map(([name, value]) => ({ name, ...value })) }, () => [h(ElTableColumn, { prop: 'name', label: '数据库字段', minWidth: 180 }), h(ElTableColumn, { prop: 'type', label: '逻辑类型', width: 130 }), h(ElTableColumn, { prop: 'length', label: '最大长度', width: 120 })]) }
})

const loginForm = reactive({ username: '', password: '' })
const loginLoading = ref(false)
const user = ref(null)
const mustChangePassword = ref(false)
const passwordDialogVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const page = ref('dashboard')
const loading = ref(false)
const clock = ref('')
let clockTimer

const pages = [
  { key: 'dashboard', title: '运行总览', subtitle: '任务、调度与今日入库', group: '监控中心', icon: 'DataAnalysis', permission: 'dashboard' },
  { key: 'runs', title: '执行日志', subtitle: '同步批次与异常详情', group: '监控中心', icon: 'Document', permission: 'logs' },
  { key: 'alerts', title: '异常告警', subtitle: '同步失败与配置风险', group: '监控中心', icon: 'Bell', permission: 'logs' },
  { key: 'tasks', title: '接口任务', subtitle: '请求、解析、入库与调度', group: '接入配置', icon: 'Connection', permission: 'tasks' },
  { key: 'datasources', title: '数据源管理', subtitle: '目标数据库连接', group: '接入配置', icon: 'Coin', permission: 'datasources' },
  { key: 'data', title: '目标数据', subtitle: '主表、子表与结构版本', group: '数据管理', icon: 'Grid', permission: 'data' },
  { key: 'settings', title: '系统管理', subtitle: '参数、账号与审计', group: '系统', icon: 'Setting', permission: 'settings' }
]
const navGroups = computed(() => [...new Set(pages.map(item => item.group))].map(name => ({ name, items: pages.filter(item => item.group === name && can(item.permission)) })).filter(group => group.items.length))
const currentPage = computed(() => pages.find(item => item.key === page.value) || pages[0])
function can(permission) { return !permission || user.value?.permissions?.includes('*') || user.value?.permissions?.includes(permission) }

// 与后端 ManagementController.ADMIN_ONLY_SETTINGS 保持一致
const ADMIN_ONLY_SETTINGS = ['alert_webhook_url', 'ollama_url', 'ollama_model', 'comment_translate_enabled', 'field_comment_map']
function canEditSetting(key) { return can('*') || !ADMIN_ONLY_SETTINGS.includes(key) }
async function resetUserPassword(row) {
  try {
    const { value } = await ElMessageBox.prompt(`为 ${row.username} 设置新密码，该账号下次登录必须再次修改`, '重置密码', {
      confirmButtonText: '确认重置', cancelButtonText: '取消', inputType: 'password',
      inputValidator: input => (input && input.length >= 8 && /[A-Za-z]/.test(input) && /\d/.test(input)) || '至少 8 位且同时包含字母和数字'
    })
    await api('/api/password/reset', { method: 'POST', body: JSON.stringify({ username: row.username, newPassword: value }) })
    ElMessage.success('密码已重置')
    await loadSettings()
  } catch (error) { if (error !== 'cancel' && error !== 'close') handleApiError(error) }
}

const dashboard = reactive({ stats: {}, today: {}, recent: [], alerts: [] })
const successRate = computed(() => dashboard.today.runs ? ((dashboard.today.success || 0) * 100 / dashboard.today.runs).toFixed(1) : '100.0')
const tasks = ref([]), runningTaskIds = ref([]), groups = ref([]), sources = ref([]), runs = ref([]), alerts = ref([])
const taskFilters = reactive({ search: '', status: '' }), runFilters = reactive({ taskId: null, status: '' })
const taskStatuses = ['未执行', '成功', '失败', '待配置主键', '执行中', 'Cron无效']
const dataTaskId = ref(null), dataPreview = reactive({ table: '', items: [], childTables: [] }), schemaVersions = ref([])
const settingsData = reactive({ items: [], users: [], audits: [] }), settingsForm = reactive({})

const taskDialogVisible = ref(false), taskTab = ref('base'), dialogSaving = ref(false)
const blankTask = () => ({ id: null, name: '', code: '', groupId: null, url: 'http://127.0.0.1:3100/demo-api/orders?scenario=v1', method: 'GET', description: '', enabled: false, authType: 'none', headersText: '{}', queryText: '{}', bodyText: '{}', authText: '{}', datasourceId: null, tableName: '', rootPath: 'data.list', uniqueKey: 'id', cronExpr: '0 0/5 * * * ?', retryCount: 2, retryIntervalSec: 5, timeoutSec: 30, sourceLabel: '' })
const taskForm = reactive(blankTask())
const cronPresets = [{ label: '每分钟', value: '0 0/1 * * * ?' }, { label: '每5分钟', value: '0 0/5 * * * ?' }, { label: '每小时', value: '0 0 0/1 * * ?' }, { label: '每日2点', value: '0 0 2 * * ?' }, { label: '每周一3点', value: '0 0 3 ? * 2' }, { label: '每月1日4点', value: '0 0 4 1 * ?' }]
const sourceDialogVisible = ref(false)
const blankSource = () => ({ id: null, name: '', code: '', dbType: 'mysql', host: '127.0.0.1', port: 3306, databaseName: '', schemaName: '', serviceName: '', jdbcUrl: '', username: '', password: '', active: true, optionsText: '{\n  "maxConnections": 10,\n  "connectTimeoutMs": 10000\n}' })
const sourceForm = reactive(blankSource())
const databaseTypes = [{ value: 'mysql', label: 'MySQL' }, { value: 'postgres', label: 'PostgreSQL' }, { value: 'sqlserver', label: 'SQL Server' }, { value: 'oracle', label: 'Oracle' }]
const taskDetailVisible = ref(false), detailTask = ref(null), detailSchema = ref([]), testResult = ref(null), testingTask = ref(false)
const runDetailVisible = ref(false), runDetail = ref(null)

function openPasswordDialog() { Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' }); passwordDialogVisible.value = true }
async function submitPasswordChange() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) { ElMessage.warning('请填写原密码与新密码'); return }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) { ElMessage.warning('两次输入的新密码不一致'); return }
  passwordSaving.value = true
  try {
    const result = await api('/api/password/change', { method: 'POST', body: JSON.stringify({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword }) })
    if (result.token) setToken(result.token)
    if (result.user) user.value = result.user
    mustChangePassword.value = false
    passwordDialogVisible.value = false
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
    ElMessage.success('密码已修改')
    await selectPage('dashboard')
  } catch (error) { ElMessage.error(error.message) } finally { passwordSaving.value = false }
}
function handleApiError(error) {
  if (error.status === 401) { logout(); return }
  if (error.code === 'MUST_CHANGE_PASSWORD') { mustChangePassword.value = true; openPasswordDialog(); return }
  ElMessage.error(error.message)
}
async function login() { loginLoading.value = true; try { const result = await api('/api/login', { method: 'POST', body: JSON.stringify(loginForm) }); setToken(result.token); user.value = result.user; mustChangePassword.value = !!result.mustChangePassword; loginForm.password = ''; if (mustChangePassword.value) { ElMessage.warning('首次登录请先修改初始密码'); openPasswordDialog() } else { await selectPage('dashboard') } } catch (error) { ElMessage.error(error.message) } finally { loginLoading.value = false } }
async function logout() { try { await api('/api/logout', { method: 'POST' }) } catch {} setToken(''); user.value = null; mustChangePassword.value = false; passwordDialogVisible.value = false }
async function restore() { if (!getToken()) return; try { const session = (await api('/api/session')).user; user.value = session; mustChangePassword.value = !!session.mustChangePassword; if (mustChangePassword.value) openPasswordDialog(); else await selectPage('dashboard') } catch { setToken('') } }
async function selectPage(target) { const meta = pages.find(item => item.key === target); if (!meta || !can(meta.permission)) target = 'dashboard'; page.value = target; await refreshPage() }
async function refreshPage() { loading.value = true; try { if (page.value === 'dashboard') await loadDashboard(); else if (page.value === 'tasks') await loadTasks(); else if (page.value === 'datasources') await loadSources(); else if (page.value === 'runs') { await loadTaskOptions(); await loadRuns() } else if (page.value === 'data') { await loadTaskOptions(); if (!dataTaskId.value) dataTaskId.value = tasks.value[0]?.id; await loadData() } else if (page.value === 'alerts') await loadAlerts(); else if (page.value === 'settings') await loadSettings() } catch (error) { handleApiError(error) } finally { loading.value = false } }
async function loadDashboard() { Object.assign(dashboard, await api('/api/dashboard')) }
async function loadTaskOptions() { const result = await api('/api/tasks'); tasks.value = result.items; runningTaskIds.value = result.runningTaskIds || [] }
async function loadTasks() { const query = new URLSearchParams(taskFilters); const [taskResult, sourceResult, groupResult] = await Promise.all([api(`/api/tasks?${query}`), api('/api/datasources'), api('/api/groups')]); tasks.value = taskResult.items; runningTaskIds.value = taskResult.runningTaskIds || []; sources.value = sourceResult.items; groups.value = groupResult.items }
async function loadSources() { sources.value = (await api('/api/datasources')).items }
async function loadRuns() { const query = new URLSearchParams(); if (runFilters.taskId) query.set('taskId', runFilters.taskId); if (runFilters.status) query.set('status', runFilters.status); runs.value = (await api(`/api/runs?${query}`)).items }
async function loadData() { if (!dataTaskId.value) { Object.assign(dataPreview, { table: '', items: [], childTables: [] }); schemaVersions.value = []; return } const [data, schema] = await Promise.all([api(`/api/tasks/${dataTaskId.value}/data?limit=100`), api(`/api/tasks/${dataTaskId.value}/schema`)]); Object.assign(dataPreview, data); schemaVersions.value = schema.items }
async function loadAlerts() { alerts.value = (await api('/api/alerts')).items }
async function loadSettings() { const result = await api('/api/settings'); Object.assign(settingsData, result); result.items.forEach(item => { settingsForm[item.settingKey] = item.settingValue }) }

async function openTaskDialog(row) { if (!sources.value.length) await loadSources(); if (!groups.value.length) groups.value = (await api('/api/groups')).items; const detail = row ? (await api(`/api/tasks/${row.id}`)).item : null; Object.assign(taskForm, blankTask(), detail || {}, { id: detail?.id || null, headersText: pretty(detail?.headers || {}), queryText: pretty(detail?.query || {}), bodyText: pretty(detail?.body || {}), authText: pretty(detail?.auth || {}), datasourceId: detail?.datasourceId || sources.value[0]?.id || null }); taskTab.value = 'base'; taskDialogVisible.value = true }
async function saveTask() { dialogSaving.value = true; try { const payload = { ...taskForm, headers: JSON.parse(taskForm.headersText || '{}'), query: JSON.parse(taskForm.queryText || '{}'), body: JSON.parse(taskForm.bodyText || '{}'), auth: JSON.parse(taskForm.authText || '{}') }; const path = taskForm.id ? `/api/tasks/${taskForm.id}` : '/api/tasks'; await api(path, { method: taskForm.id ? 'PUT' : 'POST', body: JSON.stringify(payload) }); ElMessage.success('任务配置已保存'); taskDialogVisible.value = false; await loadTasks() } catch (error) { ElMessage.error(error.message) } finally { dialogSaving.value = false } }
async function toggleTask(row) { try { await api(`/api/tasks/${row.id}/toggle`, { method: 'PATCH', body: JSON.stringify({ enabled: !row.enabled }) }); ElMessage.success('调度状态已更新'); await loadTasks() } catch (error) { ElMessage.error(error.message) } }
async function executeTask(row) { try { ElMessage.info('任务已开始执行'); const result = await api(`/api/tasks/${row.id}/run`, { method: 'POST', body: '{}' }); ElMessage.success(`执行${result.item.status}：新增 ${result.item.insertedCount}，更新 ${result.item.updatedCount}`); taskDetailVisible.value = false; await selectPage('runs') } catch (error) { ElMessage.error(error.message); await refreshPage() } }
async function showTaskDetail(id) { try { const [detail, schema] = await Promise.all([api(`/api/tasks/${id}`), api(`/api/tasks/${id}/schema`)]); detailTask.value = detail.item; detailSchema.value = schema.items; testResult.value = null; taskDetailVisible.value = true } catch (error) { ElMessage.error(error.message) } }
async function testTask(id) { testingTask.value = true; try { testResult.value = await api(`/api/tasks/${id}/test`, { method: 'POST', body: '{}' }) } catch (error) { ElMessage.error(error.message) } finally { testingTask.value = false } }
async function fullRefresh(task) { try { await ElMessageBox.prompt('输入 FULL REFRESH 确认', '强制全量重刷', { confirmButtonText: '确认重刷', cancelButtonText: '取消', inputValidator: value => value === 'FULL REFRESH' || '确认文本不正确' }).then(async ({ value }) => api(`/api/tasks/${task.id}/full-refresh`, { method: 'POST', body: JSON.stringify({ confirmText: value }) })); ElMessage.success('全量重刷已完成'); taskDetailVisible.value = false; await selectPage('runs') } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || error) } }
async function viewTaskData(id) { taskDetailVisible.value = false; dataTaskId.value = id; await selectPage('data') }

function openSourceDialog(row) { Object.assign(sourceForm, blankSource(), row || {}, { id: row?.id || null, password: '', optionsText: pretty(row?.options || { maxConnections: 10, connectTimeoutMs: 10000 }) }); sourceDialogVisible.value = true }
async function saveSource() { dialogSaving.value = true; try { const payload = { ...sourceForm, options: JSON.parse(sourceForm.optionsText || '{}') }; await api(sourceForm.id ? `/api/datasources/${sourceForm.id}` : '/api/datasources', { method: sourceForm.id ? 'PUT' : 'POST', body: JSON.stringify(payload) }); ElMessage.success('数据源配置已保存'); sourceDialogVisible.value = false; await loadSources() } catch (error) { ElMessage.error(error.message) } finally { dialogSaving.value = false } }
async function testSource(row) { try { const result = await api(`/api/datasources/${row.id}/test`, { method: 'POST', body: '{}' }); ElMessage.success(`连接成功，${result.durationMs}ms`); await loadSources() } catch (error) { ElMessage.error(error.message); await loadSources() } }
async function showRunDetail(id) { try { runDetail.value = (await api(`/api/runs/${id}`)).item; runDetailVisible.value = true } catch (error) { ElMessage.error(error.message) } }
async function resolveAlert(row) { try { await api(`/api/alerts/${row.id}/resolve`, { method: 'PATCH', body: '{}' }); ElMessage.success('告警已处理'); await loadAlerts() } catch (error) { ElMessage.error(error.message) } }
async function saveSettings() { try { await api('/api/settings', { method: 'PUT', body: JSON.stringify({ settings: settingsForm }) }); ElMessage.success('系统参数已保存'); await loadSettings() } catch (error) { ElMessage.error(error.message) } }
async function exportRuns() { try { await download('/api/export/runs', 'execution-runs.csv'); ElMessage.success('执行日志已导出') } catch (error) { ElMessage.error(error.message) } }

function number(value) { return Number(value || 0).toLocaleString('zh-CN') }
function dateTime(value) { if (!value) return '-'; const date = new Date(value); if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 19); return date.toLocaleString('zh-CN', { hour12: false }).replaceAll('/', '-') }
function statusType(status) { if (status === '成功') return 'success'; if (['失败', 'Cron无效'].includes(status)) return 'danger'; if (['待配置主键', '部分成功'].includes(status)) return 'warning'; if (status === '执行中') return 'primary'; return 'info' }
function pretty(value) { return JSON.stringify(value ?? {}, null, 2) }
function dbLabel(type) { return databaseTypes.find(item => item.value === type)?.label || type }
function sourceTarget(row) { return row.jdbcUrl || `${row.host}:${row.port || ''} / ${row.databaseName || row.serviceName || ''}` }

onMounted(() => { clockTimer = setInterval(() => { clock.value = new Date().toLocaleString('zh-CN', { hour12: false }) }, 1000); clock.value = new Date().toLocaleString('zh-CN', { hour12: false }); restore() })
onBeforeUnmount(() => clearInterval(clockTimer))
</script>
