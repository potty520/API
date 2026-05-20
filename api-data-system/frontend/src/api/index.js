import request from '@/utils/request'

export const dashboardApi = {
  getStats() {
    return request.get('/dashboard/stats')
  }
}

export const taskApi = {
  getList(params) {
    return request.get('/task/list', { params })
  },
  getDetail(id) {
    return request.get(`/task/detail/${id}`)
  },
  save(data) {
    return request.post('/task/save', data)
  },
  delete(id) {
    return request.delete(`/task/delete/${id}`)
  },
  toggle(id) {
    return request.post(`/task/toggle/${id}`)
  },
  execute(id) {
    return request.post(`/task/execute/${id}`)
  },
  getCronDescription(cronExpression) {
    return request.get('/task/cron-description', { params: { cronExpression } })
  },
  getCronNextTime(cronExpression) {
    return request.get('/task/cron-next-time', { params: { cronExpression } })
  },
  getEnabledTasks() {
    return request.get('/task/enabled')
  }
}

export const tokenApi = {
  getList() {
    return request.get('/token/list')
  },
  getDetail(id) {
    return request.get(`/token/detail/${id}`)
  },
  save(data) {
    return request.post('/token/save', data)
  },
  delete(id) {
    return request.delete(`/token/delete/${id}`)
  }
}

export const apiConfigApi = {
  getList(params) {
    return request.get('/api-config/list', { params })
  },
  getDetail(id) {
    return request.get(`/api-config/detail/${id}`)
  },
  save(data) {
    return request.post('/api-config/save', data)
  },
  delete(id) {
    return request.delete(`/api-config/delete/${id}`)
  }
}

export const fieldMappingApi = {
  getList(apiConfigId) {
    return request.get(`/field-mapping/list/${apiConfigId}`)
  },
  save(apiConfigId, data) {
    return request.post('/field-mapping/save', data, { params: { apiConfigId } })
  }
}

export const logApi = {
  getList(params) {
    return request.get('/log/list', { params })
  },
  getDetail(id) {
    return request.get(`/log/detail/${id}`)
  }
}
