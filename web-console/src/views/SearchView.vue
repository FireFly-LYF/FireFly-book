<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { assistantApi, searchApi, toLocalMediaUrl } from '../api'
import SignedImg from '../components/SignedImg.vue'
import UserAvatar from '../components/UserAvatar.vue'

const props = defineProps({
  loggedIn: Boolean,
})
const emit = defineEmits(['back', 'open-note', 'open-profile', 'need-login', 'toast'])

const HISTORY_KEY = 'ff-search-history'
const hot = ['穿搭', '美食探店', '旅行攻略', '数码测评', '家居改造']

const q = ref('')
const tab = ref('note') // note | user
const loading = ref(false)
const searched = ref(false)
const notes = ref([])
const users = ref([])
const history = ref([])
const inputRef = ref(null)

const aiLoading = ref(false)
const aiStreaming = ref(false)
const aiAnswer = ref('')
const aiNoteSources = ref([])
const aiWebSources = ref([])
const aiUngrounded = ref(false)
const aiError = ref('')
let aiAbort = null

const showIdle = computed(() => !searched.value && !loading.value)

onMounted(() => {
  history.value = loadHistory()
  nextTick(() => inputRef.value?.focus())
})

function loadHistory() {
  try {
    const list = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]')
    return Array.isArray(list) ? list.filter((s) => typeof s === 'string' && s.trim()) : []
  } catch {
    return []
  }
}

function pushHistory(keyword) {
  const k = keyword.trim()
  if (!k) return
  const next = [k, ...history.value.filter((h) => h !== k)].slice(0, 12)
  history.value = next
  localStorage.setItem(HISTORY_KEY, JSON.stringify(next))
}

function clearHistory() {
  history.value = []
  localStorage.removeItem(HISTORY_KEY)
}

function useKeyword(keyword) {
  q.value = keyword
  doSearch()
}

function onSubmit(e) {
  e?.preventDefault?.()
  doSearch()
}

async function doSearch() {
  const keyword = q.value.trim()
  if (!keyword) {
    emit('toast', '请输入关键词')
    return
  }
  if (!props.loggedIn) {
    emit('need-login')
    return
  }

  pushHistory(keyword)
  aiAbort?.abort()
  aiAbort = new AbortController()
  const { signal } = aiAbort

  searched.value = true
  loading.value = true
  aiLoading.value = true
  aiStreaming.value = false
  notes.value = []
  users.value = []
  aiAnswer.value = ''
  aiNoteSources.value = []
  aiWebSources.value = []
  aiUngrounded.value = false
  aiError.value = ''
  try {
    const notesPromise = searchApi().notes(keyword, 1, 20)
    const usersPromise = searchApi().users(keyword)
    const runAiStream = async () => {
      const res = await assistantApi().searchStream(
        keyword,
        false,
        {
          onMeta: (data) => {
            aiLoading.value = false
            aiStreaming.value = true
            // 实引在 done 里下发；此处仅提前标记是否无检索材料
            if (typeof data?.ungrounded === 'boolean') {
              aiUngrounded.value = data.ungrounded
            }
          },
          onDelta: (text) => {
            if (!text) return
            aiLoading.value = false
            aiStreaming.value = true
            aiAnswer.value += text
          },
          onDone: (data) => {
            aiStreaming.value = false
            if (data?.answer) aiAnswer.value = data.answer
            aiNoteSources.value = data?.noteSources || []
            aiWebSources.value = data?.webSources || []
            aiUngrounded.value = !!data?.ungrounded
          },
          onError: (msg) => {
            aiStreaming.value = false
            aiError.value = msg || 'AI 回答暂不可用（请确认 assistant-service）'
          },
        },
        signal,
      )
      if (res?.status === 401) throw new Error('401')
      if (aiError.value) throw new Error(aiError.value)
    }
    const aiPromise = runAiStream()

    const nRes = await notesPromise
    if (nRes.status === 401) {
      emit('need-login')
      return
    }
    if (nRes.body?.code !== 0) {
      emit('toast', nRes.body?.message || '笔记搜索失败（请确认 search-service / ES）')
      notes.value = []
    } else {
      notes.value = nRes.body.data || []
    }

    loading.value = false

    const [uRes] = await Promise.all([usersPromise, aiPromise])

    if (uRes?.status === 401) {
      emit('need-login')
      return
    }
    if (uRes?.body?.code !== 0) {
      emit('toast', uRes.body?.message || '用户搜索失败')
    } else if (uRes?.body) {
      users.value = uRes.body.data || []
    }
  } catch (err) {
    if (err?.name === 'AbortError') return
    if (err?.message === '401') {
      emit('need-login')
      return
    }
    if (!aiError.value) emit('toast', err?.message || '搜索请求失败')
  } finally {
    loading.value = false
    aiLoading.value = false
  }
}

function clearQuery() {
  aiAbort?.abort()
  aiAbort = null
  q.value = ''
  searched.value = false
  notes.value = []
  users.value = []
  aiAnswer.value = ''
  aiNoteSources.value = []
  aiWebSources.value = []
  aiUngrounded.value = false
  aiError.value = ''
  aiStreaming.value = false
  nextTick(() => inputRef.value?.focus())
}

function snippet(text, max = 72) {
  if (!text) return ''
  const t = String(text).replace(/\s+/g, ' ').trim()
  return t.length > max ? `${t.slice(0, max)}…` : t
}

function coverOf(n) {
  return toLocalMediaUrl(n?.coverUrl)
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

const UNGROUNDED_PREFIX = '未检索到依据，以下是AI补充结果'

/** 流式过程中隐藏尚未结束的 usedSources 行；有独立横幅时去掉正文前缀避免重复 */
function prepareAiDisplayText(text, stripPrefix) {
  let t = String(text || '').replace(/^\s*usedSources\s*:\s*\[[^\]]*\]\s*$/gim, '')
  if (stripPrefix) {
    t = t.replace(new RegExp(`^${UNGROUNDED_PREFIX}\\s*`), '')
  }
  return t.trim()
}

/** 轻量 Markdown → HTML（加粗、列表、段落）；高亮 [笔记N]/[网络M] */
function formatAiAnswer(text) {
  if (!text) return ''
  const lines = text.split('\n')
  let html = ''
  let inList = false

  const flushList = () => {
    if (inList) {
      html += '</ul>'
      inList = false
    }
  }

  const inline = (s) =>
    escapeHtml(s)
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(
        /\[(笔记|网络)(\d+)\]/g,
        '<span class="ai-cite">[$1$2]</span>',
      )

  for (const raw of lines) {
    const line = raw.trimEnd()
    const bullet = line.match(/^[-*•]\s+(.+)/)
    if (bullet) {
      if (!inList) {
        html += '<ul>'
        inList = true
      }
      html += `<li>${inline(bullet[1])}</li>`
      continue
    }
    flushList()
    if (!line.trim()) continue
    html += `<p>${inline(line)}</p>`
  }
  flushList()
  return html
}

const aiAnswerDisplay = computed(() =>
  prepareAiDisplayText(aiAnswer.value, aiUngrounded.value),
)
const aiAnswerHtml = computed(() =>
  formatAiAnswer(prepareAiDisplayText(aiAnswer.value, aiUngrounded.value)),
)
</script>

<template>
  <div class="search">
    <header>
      <button type="button" class="back" @click="$emit('back')" aria-label="返回">←</button>
      <form class="field" @submit="onSubmit">
        <input
          ref="inputRef"
          v-model="q"
          type="search"
          enterkeyhint="search"
          placeholder="搜索笔记、用户"
          autocomplete="off"
        />
        <button v-if="q" type="button" class="clear" @click="clearQuery" aria-label="清空">×</button>
      </form>
      <button type="button" class="go" @click="doSearch">搜索</button>
    </header>

    <template v-if="showIdle">
      <section v-if="history.length">
        <div class="sec-hd">
          <h3>历史搜索</h3>
          <button type="button" class="link" @click="clearHistory">清空</button>
        </div>
        <div class="tags">
          <button v-for="t in history" :key="t" type="button" @click="useKeyword(t)">{{ t }}</button>
        </div>
      </section>
      <section>
        <h3>热门搜索</h3>
        <div class="tags">
          <button v-for="t in hot" :key="t" type="button" @click="useKeyword(t)">{{ t }}</button>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="ai-block">
        <div class="ai-hd">
          <span class="ai-badge">AI</span>
          <h3>智能回答</h3>
        </div>
        <div v-if="aiLoading" class="ai-loading pad-sm">
          <span class="ai-dot" /><span class="ai-dot" /><span class="ai-dot" />
          <span class="ai-loading-text">正在思考…</span>
        </div>
        <p v-else-if="aiError" class="ai-error pad-sm">{{ aiError }}</p>
        <div v-else-if="aiAnswer || aiStreaming" class="ai-body pad-sm">
          <p v-if="aiUngrounded" class="ai-ungrounded">未检索到依据，以下是AI补充结果</p>
          <div v-if="aiStreaming" class="ai-answer ai-streaming">
            {{ aiAnswerDisplay }}<span class="ai-cursor" aria-hidden="true" />
          </div>
          <div v-else class="ai-answer" v-html="aiAnswerHtml" />
          <div v-if="!aiStreaming && aiNoteSources.length" class="ai-ref-notes">
            <span class="ai-ref-label">引用笔记</span>
            <button
              v-for="n in aiNoteSources"
              :key="n.id"
              type="button"
              class="ai-ref-chip"
              @click="$emit('open-note', n.id)"
            >
              {{ n.title || `笔记 #${n.id}` }}
            </button>
          </div>
        </div>
        <div v-if="!aiLoading && !aiStreaming && aiWebSources.length" class="ai-sources pad-sm">
          <h4>引用网页</h4>
          <ul>
            <li v-for="(w, i) in aiWebSources" :key="i">
              <a :href="w.url" target="_blank" rel="noopener noreferrer">{{ w.title }}</a>
            </li>
          </ul>
        </div>
      </section>

      <div class="tabs">
        <button type="button" :class="{ on: tab === 'note' }" @click="tab = 'note'">
          笔记 {{ notes.length }}
        </button>
        <button type="button" :class="{ on: tab === 'user' }" @click="tab = 'user'">
          用户 {{ users.length }}
        </button>
      </div>

      <p v-if="loading" class="muted pad">搜索中…</p>

      <template v-else-if="tab === 'note'">
        <p v-if="!notes.length" class="muted pad">没有相关笔记</p>
        <ul v-else class="note-list">
          <li v-for="n in notes" :key="n.id">
            <button type="button" class="note-row" @click="$emit('open-note', n.id)">
              <div class="thumb" :class="{ empty: !coverOf(n) }">
                <SignedImg v-if="coverOf(n)" :src="coverOf(n)" alt="" loading="lazy" />
                <span v-else>{{ (n.title || '笔').slice(0, 1) }}</span>
              </div>
              <div class="info">
                <h4>{{ n.title || '无标题' }}</h4>
                <p>{{ snippet(n.content) }}</p>
              </div>
            </button>
          </li>
        </ul>
      </template>

      <template v-else>
        <p v-if="!users.length" class="muted pad">没有相关用户</p>
        <ul v-else class="user-list">
          <li v-for="u in users" :key="u.id">
            <button type="button" class="user-row" @click="$emit('open-profile', u.id)">
              <UserAvatar
                :user="u"
                :name="u.nickname || u.username || `用户${u.id}`"
                :size="44"
              />
              <div class="u-info">
                <strong>{{ u.nickname || u.username || `用户${u.id}` }}</strong>
                <span>@{{ u.username || u.id }}</span>
              </div>
            </button>
          </li>
        </ul>
      </template>
    </template>
  </div>
</template>

<style scoped>
.search {
  min-height: 100%;
  background: #fff;
}

@media (min-width: 769px) {
  .search {
    max-width: 720px;
    margin: 16px auto 0;
    border: 1px solid var(--line);
    border-radius: 12px;
    min-height: 60vh;
  }
}

header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.7rem 0.85rem;
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 2;
}

.back, .go, .clear, .link {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--ink);
}

.back {
  font-size: 1.1rem;
  padding: 0.2rem 0.35rem;
}

.go {
  color: var(--brand, #ff2442);
  font-weight: 600;
  font-size: 0.9rem;
  white-space: nowrap;
}

.field {
  flex: 1;
  display: flex;
  align-items: center;
  background: #f5f5f5;
  border-radius: 16px;
  padding: 0 0.55rem;
  min-width: 0;
}

.field input {
  flex: 1;
  border: none;
  background: transparent;
  padding: 0.55rem 0.25rem;
  min-width: 0;
  outline: none;
  font-size: 0.92rem;
}

.clear {
  font-size: 1.1rem;
  line-height: 1;
  color: var(--ink-3);
  padding: 0.2rem;
}

section { padding: 1rem; }

.sec-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.6rem;
}

.sec-hd h3 { margin: 0; }

h3 {
  margin: 0 0 0.6rem;
  font-size: 0.92rem;
}

.link {
  font-size: 0.78rem;
  color: var(--ink-3);
}

.muted { color: var(--ink-3); font-size: 0.82rem; }
.pad { padding: 1.2rem 1rem; }
.pad-sm { padding: 0.75rem 0.85rem; }

.ai-block {
  border-bottom: 1px solid var(--line);
  background: linear-gradient(180deg, #fff8f9 0%, #fff 100%);
}

.ai-hd {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.75rem 0.85rem 0;
}

.ai-hd h3 {
  margin: 0;
  font-size: 0.92rem;
}

.ai-badge {
  font-size: 0.68rem;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #ff2442, #ff6b81);
  padding: 0.15rem 0.45rem;
  border-radius: 6px;
}

.ai-answer {
  font-size: 0.9rem;
  line-height: 1.65;
  color: var(--ink);
}

.ai-answer :deep(p) {
  margin: 0 0 0.65rem;
}

.ai-answer :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-answer :deep(ul) {
  margin: 0 0 0.65rem;
  padding-left: 1.15rem;
}

.ai-answer :deep(li) {
  margin-bottom: 0.35rem;
}

.ai-answer :deep(strong) {
  font-weight: 600;
  color: var(--ink);
}

.ai-streaming {
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 1px;
  vertical-align: text-bottom;
  background: var(--brand, #ff2442);
  animation: ai-blink 0.9s step-end infinite;
}

@keyframes ai-blink {
  50% { opacity: 0; }
}

.ai-body {
  padding-bottom: 0.85rem;
}

.ai-ungrounded {
  margin: 0 0 0.55rem;
  padding: 0.4rem 0.55rem;
  border-radius: 8px;
  background: color-mix(in srgb, var(--brand, #ff2442) 10%, transparent);
  color: var(--brand, #ff2442);
  font-size: 0.82rem;
  font-weight: 600;
}

.ai-answer :deep(.ai-cite) {
  display: inline-block;
  margin: 0 0.1rem;
  padding: 0 0.25rem;
  border-radius: 4px;
  background: color-mix(in srgb, var(--brand, #ff2442) 12%, transparent);
  color: var(--brand, #ff2442);
  font-size: 0.85em;
  font-weight: 600;
}

.ai-ref-notes {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.75rem;
  padding-top: 0.65rem;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
}

.ai-ref-label {
  font-size: 0.72rem;
  color: var(--ink-3);
  margin-right: 0.15rem;
}

.ai-ref-chip {
  border: none;
  padding: 0.22rem 0.55rem;
  border-radius: 999px;
  background: #fff;
  box-shadow: inset 0 0 0 1px rgba(255, 36, 66, 0.22);
  color: var(--brand, #ff2442);
  font-size: 0.74rem;
  cursor: pointer;
  max-width: 10rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-ref-chip:active {
  background: #fff5f6;
}

.ai-loading {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.ai-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand, #ff2442);
  opacity: 0.35;
  animation: ai-pulse 1.2s ease-in-out infinite;
}

.ai-dot:nth-child(2) { animation-delay: 0.15s; }
.ai-dot:nth-child(3) { animation-delay: 0.3s; }

.ai-loading-text {
  font-size: 0.82rem;
  color: var(--ink-3);
  margin-left: 0.25rem;
}

@keyframes ai-pulse {
  0%, 80%, 100% { opacity: 0.35; transform: scale(1); }
  40% { opacity: 1; transform: scale(1.15); }
}

.ai-error {
  color: #c62828;
  font-size: 0.82rem;
}

.ai-sources h4 {
  margin: 0 0 0.35rem;
  font-size: 0.78rem;
  color: var(--ink-3);
}

.ai-sources ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ai-sources li {
  margin-bottom: 0.25rem;
}

.ai-sources a {
  font-size: 0.78rem;
  color: var(--brand, #ff2442);
  text-decoration: none;
}

.ai-sources a:hover { text-decoration: underline; }

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.tags button {
  border: none;
  padding: 0.35rem 0.7rem;
  background: #f5f5f5;
  border-radius: 14px;
  font-size: 0.8rem;
  color: var(--ink-2);
  cursor: pointer;
}

.tags button:active { background: #ebebeb; }

.tabs {
  display: flex;
  gap: 0.25rem;
  padding: 0.55rem 0.85rem 0;
  border-bottom: 1px solid var(--line);
}

.tabs button {
  border: none;
  background: transparent;
  padding: 0.55rem 0.85rem;
  font-size: 0.88rem;
  color: var(--ink-3);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tabs button.on {
  color: var(--ink);
  font-weight: 600;
  border-bottom-color: var(--brand, #ff2442);
}

.note-list, .user-list {
  list-style: none;
  margin: 0;
  padding: 0.4rem 0 1rem;
}

.note-row {
  width: 100%;
  display: flex;
  gap: 0.75rem;
  padding: 0.75rem 0.85rem;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.note-row:active { background: #fafafa; }

.thumb {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  flex-shrink: 0;
  background: #ececec;
  display: grid;
  place-items: center;
  color: var(--ink-3);
  font-weight: 600;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info {
  min-width: 0;
  flex: 1;
}

.info h4 {
  margin: 0 0 0.25rem;
  font-size: 0.92rem;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info p {
  margin: 0;
  font-size: 0.78rem;
  color: var(--ink-3);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.user-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 0.85rem;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  color: inherit;
}

.user-row:active { background: #fafafa; }

.u-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
  background: #ececec;
  display: grid;
  place-items: center;
  font-weight: 600;
  color: var(--ink-2);
  flex-shrink: 0;
}

.u-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.u-info {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.u-info strong {
  font-size: 0.9rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.u-info span {
  font-size: 0.75rem;
  color: var(--ink-3);
}
</style>
