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

/** 把后端绝对地址改成走 Vite 代理，便于页面预览 */
export function toLocalMediaUrl(url) {
  if (!url) return ''
  return url
    .replace('http://127.0.0.1:9003', '')
    .replace('http://localhost:9003', '')
}
