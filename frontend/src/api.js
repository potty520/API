const TOKEN_KEY = 'json_ingestion_java_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export async function api(path, options = {}) {
  const token = getToken()
  const response = await fetch(path, {
    ...options,
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    }
  })
  const type = response.headers.get('content-type') || ''
  const body = type.includes('application/json') ? await response.json() : await response.text()
  if (!response.ok) {
    const error = new Error(body?.error || body || '请求失败')
    error.status = response.status
    error.code = typeof body === 'object' && body !== null ? body.code || '' : ''
    throw error
  }
  return body
}

export async function download(path, filename) {
  const response = await fetch(path, { headers: { Authorization: `Bearer ${getToken()}` } })
  if (!response.ok) {
    // 失败响应可能不是 JSON(例如反代返回的 HTML 错误页), 不能直接 json()
    const text = await response.text()
    let message = ''
    try { message = JSON.parse(text)?.error || '' } catch { message = '' }
    const error = new Error(message || `导出失败 (HTTP ${response.status})`)
    error.status = response.status
    throw error
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
