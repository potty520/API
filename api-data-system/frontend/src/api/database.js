import request from './request'

// 数据库连接管理
export function getConnections(params) {
  return request({
    url: '/api/database/connections',
    method: 'get',
    params
  })
}

export function getConnection(id) {
  return request({
    url: `/api/database/connections/${id}`,
    method: 'get'
  })
}

export function saveConnection(data) {
  return request({
    url: '/api/database/connections/save',
    method: 'post',
    data
  })
}

export function deleteConnection(id) {
  return request({
    url: `/api/database/connections/${id}`,
    method: 'delete'
  })
}

export function testConnection(data) {
  return request({
    url: '/api/database/connections/test',
    method: 'post',
    data
  })
}

// 表管理
export function getTables(params) {
  return request({
    url: '/api/database/tables',
    method: 'get',
    params
  })
}

export function getColumns(tableName) {
  return request({
    url: `/api/database/tables/${tableName}/columns`,
    method: 'get'
  })
}

export function previewTableData(tableName, params) {
  return request({
    url: `/api/database/tables/${tableName}/preview`,
    method: 'get',
    params
  })
}

export function getCreateTableSql(tableName) {
  return request({
    url: `/api/database/tables/${tableName}/create-sql`,
    method: 'get'
  })
}

export function dropTable(tableName) {
  return request({
    url: `/api/database/tables/${tableName}`,
    method: 'delete'
  })
}

export function truncateTable(tableName) {
  return request({
    url: `/api/database/tables/${tableName}/truncate`,
    method: 'delete'
  })
}

export function createTable(data) {
  return request({
    url: '/api/database/tables/create',
    method: 'post',
    data
  })
}

// SQL执行
export function executeSql(data) {
  return request({
    url: '/api/database/execute',
    method: 'post',
    data
  })
}

// 数据库信息
export function getDatabaseInfo() {
  return request({
    url: '/api/database/info',
    method: 'get'
  })
}
