import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import TaskScheduler from '../views/TaskScheduler.vue'
import TokenManagement from '../views/TokenManagement.vue'
import ApiManagement from '../views/ApiManagement.vue'
import FieldMapping from '../views/FieldMapping.vue'
import Logs from '../views/Logs.vue'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: Dashboard
  },
  {
    path: '/task-scheduler',
    name: 'TaskScheduler',
    component: TaskScheduler
  },
  {
    path: '/token-management',
    name: 'TokenManagement',
    component: TokenManagement
  },
  {
    path: '/api-management',
    name: 'ApiManagement',
    component: ApiManagement
  },
  {
    path: '/field-mapping',
    name: 'FieldMapping',
    component: FieldMapping
  },
  {
    path: '/logs',
    name: 'Logs',
    component: Logs
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
