/** 将正文中的 #话题 / @用户 转为可点击 HTML（已转义）。 */

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/**
 * @param {string} text
 * @returns {string} HTML
 */
export function formatNoteContent(text) {
  const escaped = escapeHtml(text)
  return escaped
    .replace(/#([\u4e00-\u9fa5\w]{1,30})/g, '<button type="button" class="rt-tag" data-topic="$1">#$1</button>')
    .replace(/@([\u4e00-\u9fa5\w.]{1,32})/g, '<button type="button" class="rt-mention" data-mention="$1">@$1</button>')
    .replace(/\n/g, '<br>')
}

/** 提取正文话题名（不含 #） */
export function extractTopics(text) {
  const out = []
  const re = /#([\u4e00-\u9fa5\w]{1,30})/g
  let m
  while ((m = re.exec(String(text || '')))) {
    if (!out.includes(m[1])) out.push(m[1])
  }
  return out
}

export function isVideoUrl(url) {
  if (!url) return false
  const u = String(url).toLowerCase().split('?')[0]
  return u.endsWith('.mp4') || u.endsWith('.webm') || u.endsWith('.mov')
}
