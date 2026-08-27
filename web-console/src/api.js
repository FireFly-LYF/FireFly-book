const SESSION_KEY = 'ff-session'
const DEVICE_KEY = 'ff-device-id'

export function loadAuthSession() {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null')
  } catch {
    return null
  }
}

export function saveAuthSession(session) {
  if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  else localStorage.removeItem(SESSION_KEY)
}

/**
 * 本浏览器档案级设备指纹：localStorage 持久 UUID。
 * 换浏览器 / 清站点数据 = 新设备，Refresh 会失败，需重新登录。
 */
export function getDeviceFingerprint() {
  let id = localStorage.getItem(DEVICE_KEY)
  if (!id) {
    id =
      typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `ff-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
    localStorage.setItem(DEVICE_KEY, id)
  }
  return id
}

/** 一次点击一张号码牌；超时重试必须复用，成功后再换新的。 */
export function newIdempotencyKey() {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `idem-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

function withDevice(data = {}) {
  return { ...data, deviceFingerprint: getDeviceFingerprint() }
}

/** 规范化登录/刷新返回：兼容 token / accessToken */
export function normalizeAuthPayload(data) {
  if (!data?.user) return null
  const access = data.accessToken || data.token
  if (!access) return null
  return {
    token: access,
    accessToken: access,
    refreshToken: data.refreshToken || loadAuthSession()?.refreshToken || null,
    user: data.user,
  }
}

function getAccessToken() {
  const s = loadAuthSession()
  return s?.accessToken || s?.token || null
}

/** Bearer + 可选 Content-Type；网关验 Access JWT 后注入 X-User-Id */
export function authHeaders(extra = {}) {
  const headers = { ...extra }
  const token = getAccessToken()
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

let refreshPromise = null

function isAuthPublicPath(url) {
  return (
    url.includes('/api/user/login') ||
    url.includes('/api/user/register') ||
    url.includes('/api/user/refresh')
  )
}

/** 单飞刷新：并发 401 只打一次 /refresh（须带同设备指纹） */
async function refreshAccessToken() {
  if (refreshPromise) return refreshPromise
  refreshPromise = (async () => {
    const session = loadAuthSession()
    if (!session?.refreshToken) return false
    const res = await fetch('/api/user/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(withDevice({ refreshToken: session.refreshToken })),
    })
    const text = await res.text()
    let body
    try {
      body = text ? JSON.parse(text) : null
    } catch {
      return false
    }
    if (!res.ok || body?.code !== 0 || !body?.data) return false
    const next = normalizeAuthPayload({
      ...body.data,
      user: body.data.user || session.user,
    })
    if (!next) return false
    saveAuthSession(next)
    return true
  })().finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

async function request(url, options = {}, allowRefresh = true) {
  const res = await fetch(url, options)
  const text = await res.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = { raw: text }
  }

  // 网关 HTTP 401：尝试 refresh 后重试一次
  if (res.status === 401 && allowRefresh && !isAuthPublicPath(url)) {
    const ok = await refreshAccessToken()
    if (ok) {
      const headers = { ...(options.headers || {}) }
      const auth = authHeaders()
      if (auth.Authorization) headers.Authorization = auth.Authorization
      return request(url, { ...options, headers }, false)
    }
    saveAuthSession(null)
  }

  return { ok: res.ok, status: res.status, body }
}

export function userApi() {
  return {
    register: (data) =>
      request('/api/user/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(withDevice(data)),
      }),
    login: (data) =>
      request('/api/user/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(withDevice(data)),
      }),
    refresh: (refreshToken) =>
      request('/api/user/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(withDevice({ refreshToken })),
      }),
    logout: () =>
      request('/api/user/logout', {
        method: 'POST',
        headers: authHeaders(),
      }),
    me: () =>
      request('/api/user/me', {
        headers: authHeaders(),
      }),
    getById: (id) =>
      request(`/api/user/${id}`, {
        headers: authHeaders(),
      }),
    updateMe: (data) =>
      request('/api/user/me', {
        method: 'PUT',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(data),
      }),
    follow: (followeeId) =>
      request(`/api/user/follow/${followeeId}`, {
        method: 'POST',
        headers: authHeaders(),
      }),
    unfollow: (followeeId) =>
      request(`/api/user/follow/${followeeId}`, {
        method: 'DELETE',
        headers: authHeaders(),
      }),
    followers: (id) =>
      request(`/api/user/${id}/followers`, {
        headers: authHeaders(),
      }),
    following: (id) =>
      request(`/api/user/${id}/following`, {
        headers: authHeaders(),
      }),
  }
}

export function mediaApi() {
  return {
    upload: (file) => {
      const fd = new FormData()
      fd.append('file', file)
      return request('/api/media/upload', {
        method: 'POST',
        headers: authHeaders(),
        body: fd,
      })
    },
    get: (id) =>
      request(`/api/media/${id}`, {
        headers: authHeaders(),
      }),
    /** 为 /files/... 规范路径批量签发短期 accessUrl */
    sign: (paths) =>
      request('/api/media/sign', {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          paths: Array.isArray(paths) ? paths : [paths],
        }),
      }),
  }
}

export function noteApi() {
  return {
    create: (data, idempotencyKey) =>
      request('/api/note', {
        method: 'POST',
        headers: authHeaders({
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey || newIdempotencyKey(),
        }),
        body: JSON.stringify(data),
      }),
    detail: (id) =>
      request(`/api/note/${id}`, {
        headers: authHeaders(),
      }),
    listByUser: (userId, page = 1, size = 10) =>
      request(`/api/note/user/${userId}?page=${page}&size=${size}`, {
        headers: authHeaders(),
      }),
    remove: (id) =>
      request(`/api/note/${id}`, {
        method: 'DELETE',
        headers: authHeaders(),
      }),
  }
}

export function socialApi() {
  return {
    like: (noteId) =>
      request(`/api/social/like/${noteId}`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
      }),
    unlike: (noteId) =>
      request(`/api/social/like/${noteId}`, {
        method: 'DELETE',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
      }),
    likeCount: (noteId) =>
      request(`/api/social/like/${noteId}/count`, {
        headers: authHeaders(),
      }),
    likedByMe: (noteId) =>
      request(`/api/social/like/${noteId}/me`, {
        headers: authHeaders(),
      }),
    likedOf: (userId, page = 1, size = 50) =>
      request(`/api/social/like/of/${userId}?page=${page}&size=${size}`, {
        headers: authHeaders(),
      }),
    collect: (noteId) =>
      request(`/api/social/collect/${noteId}`, {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
      }),
    uncollect: (noteId) =>
      request(`/api/social/collect/${noteId}`, {
        method: 'DELETE',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
      }),
    collectedByMe: (noteId) =>
      request(`/api/social/collect/${noteId}/me`, {
        headers: authHeaders(),
      }),
    collectedOf: (userId, page = 1, size = 50) =>
      request(`/api/social/collect/of/${userId}?page=${page}&size=${size}`, {
        headers: authHeaders(),
      }),
    comment: (data, idempotencyKey) =>
      request('/api/social/comment', {
        method: 'POST',
        headers: authHeaders({
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey || newIdempotencyKey(),
        }),
        body: JSON.stringify(data),
      }),
    comments: (noteId) =>
      request(`/api/social/comment/${noteId}`, {
        headers: authHeaders(),
      }),
  }
}

export function notifyApi() {
  return {
    list: (page = 1, size = 20) =>
      request(`/api/notify/list?page=${page}&size=${size}`, {
        headers: authHeaders(),
      }),
    read: (data) =>
      request('/api/notify/read', {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(data),
      }),
  }
}

export function feedApi() {
  return {
    following: (size = 20) =>
      request(`/api/feed/following?size=${size}`, {
        headers: authHeaders(),
      }),
  }
}

export function searchApi() {
  return {
    notes: (q, page = 1, size = 10) =>
      request(`/api/search/note?q=${encodeURIComponent(q)}&page=${page}&size=${size}`, {
        headers: authHeaders(),
      }),
    users: (q) =>
      request(`/api/search/user?q=${encodeURIComponent(q)}`, {
        headers: authHeaders(),
      }),
  }
}

/** 解析 SSE 文本块 */
function parseSseBlock(block, handlers) {
  if (!block.trim()) return
  let event = 'message'
  let data = ''
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data += line.slice(5).trim()
  }
  if (!data) return
  let json
  try {
    json = JSON.parse(data)
  } catch {
    return
  }
  if (event === 'meta') handlers.onMeta?.(json)
  else if (event === 'delta') handlers.onDelta?.(json.text ?? '')
  else if (event === 'done') handlers.onDone?.(json)
  else if (event === 'error') handlers.onError?.(json.message || 'AI 流式失败')
}

/** AI 搜索助手（SSE 流式） */
async function searchStreamRequest(url, body, handlers, signal, allowRefresh = true) {
  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders({
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    }),
    body: JSON.stringify(body),
    signal,
  })

  if (res.status === 401 && allowRefresh && !isAuthPublicPath(url)) {
    const ok = await refreshAccessToken()
    if (ok) return searchStreamRequest(url, body, handlers, signal, false)
    saveAuthSession(null)
    handlers.onError?.('未登录')
    return { status: 401 }
  }

  if (!res.ok) {
    const text = await res.text()
    let msg = text
    try {
      const j = JSON.parse(text)
      msg = j.message || j.detail || text
    } catch {
      /* keep raw */
    }
    handlers.onError?.(msg || `HTTP ${res.status}`)
    return { status: res.status }
  }

  const reader = res.body?.getReader()
  if (!reader) {
    handlers.onError?.('浏览器不支持流式响应')
    return { status: 0 }
  }

  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() ?? ''
    for (const block of parts) parseSseBlock(block, handlers)
  }
  if (buffer.trim()) parseSseBlock(buffer, handlers)
  return { status: res.status }
}

/** AI 搜索助手：站内笔记 + 网络 + LLM（noteCandidates 由前端 search 结果传入，避免重复检索） */
export function assistantApi() {
  return {
    search: (query, includeWeb = true, noteCandidates = null) =>
      request('/api/ai/search', {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          query,
          includeWeb,
          ...(noteCandidates?.length ? { noteCandidates } : {}),
        }),
      }),
    searchStream: (query, includeWeb = false, handlers = {}, signal = null) =>
      searchStreamRequest(
        '/api/ai/search/stream',
        { query, includeWeb },
        handlers,
        signal,
      ),
  }
}

export function toLocalMediaUrl(url) {
  if (!url) return ''
  let u = String(url)
    .replace('http://127.0.0.1:9003', '')
    .replace('http://localhost:9003', '')
    .replace('http://127.0.0.1:8080', '')
    .replace('http://localhost:8080', '')
  const q = u.indexOf('?')
  if (q >= 0) u = u.substring(0, q)
  return u
}

/** 是否为需签名的媒体路径 */
export function isSignedMediaPath(url) {
  const p = toLocalMediaUrl(url)
  return p.startsWith('/files/')
}

const signCache = new Map()

/**
 * 将规范 /files/... 换成带 exp+sig 的短期 URL（需登录）。
 * 静态 /avatars 等原样返回。
 */
export async function resolveSignedMediaUrl(url) {
  const path = toLocalMediaUrl(url)
  if (!path) return ''
  if (!path.startsWith('/files/')) return path

  const now = Math.floor(Date.now() / 1000)
  const hit = signCache.get(path)
  if (hit && hit.exp > now + 60) {
    return hit.accessUrl
  }

  const res = await mediaApi().sign([path])
  if (res.body?.code !== 0) {
    throw new Error(res.body?.message || '媒体签名失败')
  }
  const accessUrl = res.body.data?.urls?.[path] || res.body.data?.accessUrl || ''
  if (!accessUrl) throw new Error('空 accessUrl')

  const expMatch = /[?&]exp=(\d+)/.exec(accessUrl)
  const exp = expMatch ? Number(expMatch[1]) : now + 3000
  signCache.set(path, { accessUrl, exp })
  return accessUrl
}
