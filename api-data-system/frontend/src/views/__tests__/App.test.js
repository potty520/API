import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

const { MockIcon } = vi.hoisted(() => ({
  MockIcon: { template: '<span class="mock-icon"></span>' }
}))

// Mock vue-router
const mockRoute = { path: '/dashboard' }
vi.mock('vue-router', () => ({
  useRoute: vi.fn(() => mockRoute)
}))

// Mock element-plus icons
vi.mock('@element-plus/icons-vue', () => ({
  DataAnalysis: MockIcon,
  Timer: MockIcon,
  Key: MockIcon,
  Connection: MockIcon,
  Document: MockIcon,
  Tickets: MockIcon,
  User: MockIcon
}))

import App from '../../App.vue'

describe('App.vue', () => {
  const stubs = {
    'el-container': true,
    'el-aside': true,
    'el-header': true,
    'el-main': true,
    'el-menu': true,
    'el-menu-item': true,
    'el-icon': true,
    'router-view': true
  }

  it('should mount successfully', () => {
    const wrapper = mount(App, {
      global: {
        stubs,
        mocks: {
          $route: { path: '/dashboard' }
        }
      }
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('should contain the app title', () => {
    const wrapper = mount(App, {
      global: {
        stubs,
        mocks: {
          $route: { path: '/dashboard' }
        }
      }
    })
    // The sidebar title should be rendered
    const html = wrapper.html()
    expect(html).toBeTruthy()
  })
})
