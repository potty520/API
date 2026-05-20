import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

// Mock echarts completely before any imports
vi.mock('echarts', () => ({
  default: {
    init: vi.fn(() => ({ setOption: vi.fn(), dispose: vi.fn(), resize: vi.fn() }))
  },
  init: vi.fn(() => ({ setOption: vi.fn(), dispose: vi.fn(), resize: vi.fn() }))
}))

// Mock vue-router
vi.mock('vue-router', () => ({
  useRoute: vi.fn(() => ({ path: '/dashboard' }))
}))

// Mock element-plus
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() }
}))

// Mock element-plus icons
const { MockIcon } = vi.hoisted(() => ({
  MockIcon: { template: '<span class="mock-icon"></span>', props: ['size', 'color'] }
}))

vi.mock('@element-plus/icons-vue', () => ({
  Timer: MockIcon,
  CircleCheck: MockIcon,
  TrendCharts: MockIcon,
  DataLine: MockIcon,
  SuccessFilled: MockIcon,
  CircleCloseFilled: MockIcon,
  DocumentAdd: MockIcon
}))

// Mock API
vi.mock('@/api', () => ({
  dashboardApi: {
    getStats: vi.fn(() => Promise.resolve({
      code: 200,
      data: {
        totalTasks: 10,
        runningTasks: 5,
        successRate: 90,
        todayDataCount: 100,
        todaySuccessCount: 8,
        todayFailedCount: 2,
        recentExecutions: [],
        taskStatusDistribution: [{ name: '运行中', value: 5 }, { name: '已停止', value: 3 }]
      }
    }))
  }
}))

import Dashboard from '../Dashboard.vue'

describe('Dashboard.vue', () => {
  const stubs = {
    'el-row': true,
    'el-col': true,
    'el-card': true,
    'el-icon': true,
    'el-table': true,
    'el-table-column': true,
    'el-tag': true,
    'el-pagination': true,
    'el-button': true,
    'el-dialog': true,
    'el-form': true,
    'el-form-item': true,
    'el-input': true,
    'el-select': true,
    'el-option': true,
    'el-space': true,
    'el-alert': true,
    'el-descriptions': true,
    'el-descriptions-item': true,
    'el-divider': true,
    'el-empty': true,
    'el-checkbox': true,
    'el-radio-group': true,
    'el-radio': true,
    'el-input-number': true,
    'router-view': true,
    'router-link': true
  }

  it('should mount successfully', () => {
    const wrapper = mount(Dashboard, { global: { stubs } })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.find('.page-container').exists()).toBe(true)
  })

  it('should render page structure with all three rows', () => {
    const wrapper = mount(Dashboard, { global: { stubs } })
    const rows = wrapper.findAll('.dashboard-row')
    expect(rows.length).toBe(3)
  })
})
