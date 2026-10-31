/**
 * 后端多为 MySQL DATETIME / LocalDateTime，无时区后缀。
 * 库与业务按 Asia/Shanghai，无后缀时按东八区解析，避免被当成本机/UTC 算偏。
 */
export function parseServerTime(input) {
  if (!input) return null
  if (input instanceof Date) {
    return Number.isNaN(input.getTime()) ? null : input
  }
  let s = String(input).trim()
  if (!s) return null
  s = s.replace(' ', 'T')
  if (/[zZ]$|[+-]\d{2}:?\d{2}$/.test(s)) {
    const d = new Date(s)
    return Number.isNaN(d.getTime()) ? null : d
  }
  // 截掉毫秒再补偏移，兼容 2026-10-31T05:05:36.123
  const base = s.replace(/\.\d+$/, '')
  const d = new Date(`${base}+08:00`)
  return Number.isNaN(d.getTime()) ? null : d
}

/** 相对时间；无效输入返回空串 */
export function formatRelativeTime(input) {
  const d = parseServerTime(input)
  if (!d) return input ? String(input) : ''
  const diff = Date.now() - d.getTime()
  if (diff < 0) return '刚刚'
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day} 天前`
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dayNum = String(d.getDate()).padStart(2, '0')
  if (d.getFullYear() === new Date().getFullYear()) return `${m}-${dayNum}`
  return `${d.getFullYear()}-${m}-${dayNum}`
}
