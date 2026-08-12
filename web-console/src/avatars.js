/** FireFly 默认头像（public/avatars） */
export const DEFAULT_AVATARS = [
  { id: 'ff-01', url: '/avatars/ff-01.svg', label: '蜜桃粉' },
  { id: 'ff-02', url: '/avatars/ff-02.svg', label: '晴空蓝' },
  { id: 'ff-03', url: '/avatars/ff-03.svg', label: '薄荷青' },
  { id: 'ff-04', url: '/avatars/ff-04.svg', label: '雾紫' },
  { id: 'ff-05', url: '/avatars/ff-05.svg', label: '暖阳黄' },
  { id: 'ff-06', url: '/avatars/ff-06.svg', label: '海水青' },
  { id: 'ff-07', url: '/avatars/ff-07.svg', label: '杏橘' },
  { id: 'ff-08', url: '/avatars/ff-08.svg', label: '玫粉' },
  { id: 'ff-09', url: '/avatars/ff-09.svg', label: '抹茶' },
  { id: 'ff-10', url: '/avatars/ff-10.svg', label: '靛蓝' },
  { id: 'ff-11', url: '/avatars/ff-11.svg', label: '落日橙' },
  { id: 'ff-12', url: '/avatars/ff-12.svg', label: '烟雾灰' },
]

export function isDefaultAvatarUrl(url) {
  if (!url) return false
  return String(url).includes('/avatars/ff-')
}

/** 按 userId 稳定分配一个默认头像 */
export function defaultAvatarById(userId) {
  const n = Math.abs(Number(userId) || 0)
  const item = DEFAULT_AVATARS[n % DEFAULT_AVATARS.length]
  return item.url
}

/**
 * 解析展示用头像：
 * - 有自定义 avatarUrl（含默认库路径或上传图）优先
 * - 否则按 userId 分配默认头像
 * - 再不行用名字首字占位（返回空，由组件画字）
 */
export function resolveAvatarUrl(userOrUrl, userId) {
  if (typeof userOrUrl === 'string') {
    const url = userOrUrl.trim()
    if (url) return url
    return defaultAvatarById(userId)
  }
  const u = userOrUrl || {}
  const url = (u.avatarUrl || '').trim()
  if (url) return url
  const id = u.id ?? userId
  if (id != null) return defaultAvatarById(id)
  return DEFAULT_AVATARS[0].url
}
