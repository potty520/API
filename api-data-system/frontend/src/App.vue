<template>
  <el-container style="height: 100vh">
    <el-aside width="200px" style="background-color: #545c64">
      <div style="padding: 20px; text-align: center; color: white; font-size: 20px; font-weight: 600; border-bottom: 1px solid #6f6f6f;">
        API数据系统
      </div>
      <el-menu
        :default-active="$route.path"
        router
        background-color="#545c64"
        text-color="#fff"
        active-text-color="#ffd04b"
        style="border-right: none"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/task-scheduler">
          <el-icon><Timer /></el-icon>
          <span>任务调度</span>
        </el-menu-item>
        <el-menu-item index="/token-management">
          <el-icon><Key /></el-icon>
          <span>Token管理</span>
        </el-menu-item>
        <el-menu-item index="/api-management">
          <el-icon><Connection /></el-icon>
          <span>接口管理</span>
        </el-menu-item>
        <el-menu-item index="/field-mapping">
          <el-icon><Document /></el-icon>
          <span>映射配置</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Tickets /></el-icon>
          <span>数据与日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background-color: white; box-shadow: 0 2px 4px rgba(0,0,0,0.1); display: flex; justify-content: space-between; align-items: center;">
        <div style="font-size: 20px; font-weight: 600; color: #303133;">
          {{ pageTitle }}
        </div>
        <div style="display: flex; align-items: center;">
          <el-icon style="margin-right: 10px; font-size: 24px;"><User /></el-icon>
          <span style="font-size: 14px; color: #606266;">管理员</span>
        </div>
      </el-header>

      <el-main style="background-color: #f0f2f5;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  DataAnalysis,
  Timer,
  Key,
  Connection,
  Document,
  Tickets,
  User
} from '@element-plus/icons-vue'

const route = useRoute()

const pageTitle = computed(() => {
  const titleMap = {
    '/dashboard': '仪表盘',
    '/task-scheduler': '任务调度',
    '/token-management': 'Token管理',
    '/api-management': '接口管理',
    '/field-mapping': '映射配置',
    '/logs': '数据与日志'
  }
  return titleMap[route.path] || '首页'
})
</script>

<style scoped>
.el-header {
  padding: 0 20px;
}
</style>
