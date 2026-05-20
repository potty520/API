import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the request module
vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn()
  }
}))

import request from '@/utils/request'
import {
  dashboardApi,
  taskApi,
  tokenApi,
  apiConfigApi,
  fieldMappingApi,
  logApi
} from '../index'

describe('dashboardApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call GET /dashboard/stats', async () => {
    request.get.mockResolvedValue({ code: 200, data: { totalTasks: 5 } })
    const result = await dashboardApi.getStats()
    expect(request.get).toHaveBeenCalledWith('/dashboard/stats')
    expect(result.data.totalTasks).toBe(5)
  })
})

describe('taskApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call GET /task/list with params', async () => {
    request.get.mockResolvedValue({ code: 200, data: { records: [] } })
    await taskApi.getList({ pageNum: 1, pageSize: 10 })
    expect(request.get).toHaveBeenCalledWith('/task/list', { params: { pageNum: 1, pageSize: 10 } })
  })

  it('should call POST /task/save with data', async () => {
    request.post.mockResolvedValue({ code: 200, data: true })
    await taskApi.save({ taskName: 'Test' })
    expect(request.post).toHaveBeenCalledWith('/task/save', { taskName: 'Test' })
  })

  it('should call DELETE /task/delete/{id}', async () => {
    request.delete.mockResolvedValue({ code: 200, data: true })
    await taskApi.delete(1)
    expect(request.delete).toHaveBeenCalledWith('/task/delete/1')
  })

  it('should call POST /task/toggle/{id}', async () => {
    request.post.mockResolvedValue({ code: 200, data: true })
    await taskApi.toggle(1)
    expect(request.post).toHaveBeenCalledWith('/task/toggle/1')
  })

  it('should call POST /task/execute/{id}', async () => {
    request.post.mockResolvedValue({ code: 200, data: 'Task execution started' })
    await taskApi.execute(1)
    expect(request.post).toHaveBeenCalledWith('/task/execute/1')
  })

  it('should call GET /task/cron-description with params', async () => {
    request.get.mockResolvedValue({ code: 200, data: '每5分' })
    await taskApi.getCronDescription('0 */5 * * *')
    expect(request.get).toHaveBeenCalledWith('/task/cron-description', { params: { cronExpression: '0 */5 * * *' } })
  })

  it('should call GET /task/enabled', async () => {
    request.get.mockResolvedValue({ code: 200, data: [] })
    await taskApi.getEnabledTasks()
    expect(request.get).toHaveBeenCalledWith('/task/enabled')
  })
})

describe('tokenApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call GET /token/list', async () => {
    request.get.mockResolvedValue({ code: 200, data: [] })
    await tokenApi.getList()
    expect(request.get).toHaveBeenCalledWith('/token/list')
  })

  it('should call POST /token/save', async () => {
    request.post.mockResolvedValue({ code: 200, data: true })
    await tokenApi.save({ name: 'Token1' })
    expect(request.post).toHaveBeenCalledWith('/token/save', { name: 'Token1' })
  })

  it('should call DELETE /token/delete/{id}', async () => {
    request.delete.mockResolvedValue({ code: 200, data: true })
    await tokenApi.delete(1)
    expect(request.delete).toHaveBeenCalledWith('/token/delete/1')
  })
})

describe('apiConfigApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call GET /api-config/list with params', async () => {
    request.get.mockResolvedValue({ code: 200, data: { records: [] } })
    await apiConfigApi.getList({ pageNum: 1, pageSize: 10 })
    expect(request.get).toHaveBeenCalledWith('/api-config/list', { params: { pageNum: 1, pageSize: 10 } })
  })

  it('should call POST /api-config/save', async () => {
    request.post.mockResolvedValue({ code: 200, data: true })
    await apiConfigApi.save({ name: 'API1' })
    expect(request.post).toHaveBeenCalledWith('/api-config/save', { name: 'API1' })
  })

  it('should call DELETE /api-config/delete/{id}', async () => {
    request.delete.mockResolvedValue({ code: 200, data: true })
    await apiConfigApi.delete(1)
    expect(request.delete).toHaveBeenCalledWith('/api-config/delete/1')
  })
})

describe('fieldMappingApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call GET /field-mapping/list/{apiConfigId}', async () => {
    request.get.mockResolvedValue({ code: 200, data: [] })
    await fieldMappingApi.getList(1)
    expect(request.get).toHaveBeenCalledWith('/field-mapping/list/1')
  })

  it('should call POST /field-mapping/save with apiConfigId as param', async () => {
    request.post.mockResolvedValue({ code: 200, data: true })
    await fieldMappingApi.save(1, [{ sourceField: 'f1' }])
    expect(request.post).toHaveBeenCalledWith('/field-mapping/save', [{ sourceField: 'f1' }], { params: { apiConfigId: 1 } })
  })
})

describe('logApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should call GET /log/list with params', async () => {
    request.get.mockResolvedValue({ code: 200, data: { records: [] } })
    await logApi.getList({ pageNum: 1, pageSize: 10 })
    expect(request.get).toHaveBeenCalledWith('/log/list', { params: { pageNum: 1, pageSize: 10 } })
  })

  it('should call GET /log/detail/{id}', async () => {
    request.get.mockResolvedValue({ code: 200, data: { id: 1 } })
    await logApi.getDetail(1)
    expect(request.get).toHaveBeenCalledWith('/log/detail/1')
  })
})
