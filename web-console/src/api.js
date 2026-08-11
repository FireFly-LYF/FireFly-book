const SESSION_KEY = 'ff-session'

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

function getToken() {
  return loadAuthSession()?.token || null
}

/** Bearer + 可选 Content-Type；网关验 JWT 后注入 X-User-Id */
export function authHeaders(extra = {}) {
  const headers = { ...extra }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

async function request(url, options = {}) {
  const res = await fetch(url, options)
  const text = await res.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = { raw: text }
  }
  return { ok: res.ok, status: res.status, body }
}

export function userApi() {
  return {
    register: (data) =>
      request('/api/user/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      }),
    login: (data) =>
      request('/api/user/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      }),
    me: () =>
      request('/api/user/me', {
        headers: authHeaders(),
      }),
    getById: (id) =>
      request(`/api/user/${id}`, {
        headers: authHeaders(),
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
  }
}

export function noteApi() {
  return {
    create: (data) =>
      request('/api/note', {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
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
    comment: (data) =>
      request('/api/social/comment', {
        method: 'POST',
        headers: authHeaders({ 'Content-Type': 'application/json' }),
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

/** 关注流：feed-service 读扩散 */
export function feedApi() {
  return {
    following: (size = 20) =>
      request(`/api/feed/following?size=${size}`, {
        headers: authHeaders(),
      }),
  }
}

/** 把后端绝对地址改成走 Vite 代理，便于页面预览 */
export function toLocalMediaUrl(url) {
  if (!url) return ''
  return url
    .replace('http://127.0.0.1:9003', '')
    .replace('http://localhost:9003', '')
}
