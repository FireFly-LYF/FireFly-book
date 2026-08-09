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
    me: (userId) =>
      request('/api/user/me', {
        headers: { 'X-User-Id': String(userId) },
      }),
    getById: (id) => request(`/api/user/${id}`),
    follow: (userId, followeeId) =>
      request(`/api/user/follow/${followeeId}`, {
        method: 'POST',
        headers: { 'X-User-Id': String(userId) },
      }),
    unfollow: (userId, followeeId) =>
      request(`/api/user/follow/${followeeId}`, {
        method: 'DELETE',
        headers: { 'X-User-Id': String(userId) },
      }),
    followers: (id) => request(`/api/user/${id}/followers`),
    following: (id) => request(`/api/user/${id}/following`),
  }
}

export function mediaApi() {
  return {
    upload: (userId, file) => {
      const fd = new FormData()
      fd.append('file', file)
      return request('/api/media/upload', {
        method: 'POST',
        headers: { 'X-User-Id': String(userId) },
        body: fd,
      })
    },
    get: (id) => request(`/api/media/${id}`),
  }
}

export function noteApi() {
  return {
    create: (userId, data) =>
      request('/api/note', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': String(userId),
        },
        body: JSON.stringify(data),
      }),
    detail: (id) => request(`/api/note/${id}`),
    listByUser: (userId, page = 1, size = 10) =>
      request(`/api/note/user/${userId}?page=${page}&size=${size}`),
    remove: (userId, id) =>
      request(`/api/note/${id}`, {
        method: 'DELETE',
        headers: { 'X-User-Id': String(userId) },
      }),
  }
}

export function socialApi() {
  const headers = (userId) => ({
    'Content-Type': 'application/json',
    ...(userId != null ? { 'X-User-Id': String(userId) } : {}),
  })
  return {
    like: (userId, noteId) =>
      request(`/api/social/like/${noteId}`, {
        method: 'POST',
        headers: headers(userId),
      }),
    unlike: (userId, noteId) =>
      request(`/api/social/like/${noteId}`, {
        method: 'DELETE',
        headers: headers(userId),
      }),
    likeCount: (noteId) => request(`/api/social/like/${noteId}/count`),
    likedByMe: (userId, noteId) =>
      request(`/api/social/like/${noteId}/me`, {
        headers: headers(userId),
      }),
    collect: (userId, noteId) =>
      request(`/api/social/collect/${noteId}`, {
        method: 'POST',
        headers: headers(userId),
      }),
    uncollect: (userId, noteId) =>
      request(`/api/social/collect/${noteId}`, {
        method: 'DELETE',
        headers: headers(userId),
      }),
    comment: (userId, data) =>
      request('/api/social/comment', {
        method: 'POST',
        headers: headers(userId),
        body: JSON.stringify(data),
      }),
    comments: (noteId) => request(`/api/social/comment/${noteId}`),
  }
}

export function notifyApi() {
  const headers = (userId) => ({
    'Content-Type': 'application/json',
    ...(userId != null ? { 'X-User-Id': String(userId) } : {}),
  })
  return {
    list: (userId, page = 1, size = 20) =>
      request(`/api/notify/list?page=${page}&size=${size}`, {
        headers: headers(userId),
      }),
    read: (userId, data) =>
      request('/api/notify/read', {
        method: 'POST',
        headers: headers(userId),
        body: JSON.stringify(data),
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
