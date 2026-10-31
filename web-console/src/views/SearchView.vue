<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { assistantApi, searchApi, toLocalMediaUrl } from '../api'
import NoteCard from '../components/NoteCard.vue'
import SignedImg from '../components/SignedImg.vue'
import UserAvatar from '../components/UserAvatar.vue'

const props = defineProps({
  loggedIn: Boolean,
})
const emit = defineEmits(['back', 'open-note', 'open-profile', 'need-login', 'toast'])

const HISTORY_KEY = 'ff-search-history'
const hot = ['穿搭', '火锅', '咖啡', '通勤', '周末', '南京']

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
const aiActiveCite = ref(null) // number | null，角标/卡片联动
let aiAbort = null
let aiHighlightTimer = null

const showIdle = computed(() => !searched.value && !loading.value)

/** 已登录且 AI 有内容/加载中；或未登录搜索后展示引导 */
const showAiGuest = computed(
  () => searched.value && !props.loggedIn && !loading.value,
)

/** AI 不可用（503 / 无 upstream 等）时整块隐藏，不展示错误态板块 */
const showAiBlock = computed(() => {
  if (!props.loggedIn) return false
  if (aiError.value) return false
  return (
    aiLoading.value ||
    aiStreaming.value ||
    !!aiAnswer.value ||
    aiNoteSources.value.length > 0 ||
    aiWebSources.value.length > 0
  )
})

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

function toNoteCandidates(list) {
  return (list || []).slice(0, 8).map((n) => ({
    id: n.id,
    title: n.title || '',
    content: n.content || n.snippet || '',
    coverUrl: n.coverUrl || '',
  }))
}

async function runAiStream(keyword, noteCandidates, signal) {
  if (!props.loggedIn) {
    aiLoading.value = false
    return
  }
  aiLoading.value = true
  const res = await assistantApi().searchStream(
    keyword,
    false,
    {
      onMeta: (data) => {
        aiLoading.value = false
        aiStreaming.value = true
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
        const byId = new Map((notes.value || []).map((n) => [n.id, n]))
        aiNoteSources.value = (data?.noteSources || []).map((s) => {
          const hit = byId.get(s.id)
          return {
            ...s,
            title: s.title || hit?.title || '',
            coverUrl: s.coverUrl || hit?.coverUrl || '',
            snippet: s.snippet || hit?.content || '',
          }
        })
        aiWebSources.value = data?.webSources || []
        aiUngrounded.value = !!data?.ungrounded
      },
      onError: (msg) => {
        aiStreaming.value = false
        aiAnswer.value = ''
        aiNoteSources.value = []
        aiWebSources.value = []
        aiError.value = msg || 'AI 回答暂不可用'
      },
    },
    signal,
    noteCandidates,
  )
  if (res?.status === 401) {
    aiLoading.value = false
    aiError.value = 'need-login'
  }
}

async function doSearch() {
  const keyword = q.value.trim()
  if (!keyword) {
    emit('toast', '请输入关键词')
    return
  }

  pushHistory(keyword)
  aiAbort?.abort()
  aiAbort = new AbortController()
  const { signal } = aiAbort

  searched.value = true
  loading.value = true
  aiLoading.value = props.loggedIn
  aiStreaming.value = false
  notes.value = []
  users.value = []
  aiAnswer.value = ''
  aiNoteSources.value = []
  aiWebSources.value = []
  aiUngrounded.value = false
  aiError.value = ''
  aiActiveCite.value = null
  try {
    const notesPromise = searchApi().notes(keyword, 1, 20)
    const usersPromise = searchApi().users(keyword)

    const nRes = await notesPromise
    if (nRes.status === 401) {
      emit('need-login')
      return
    }
    if (nRes.body?.code !== 0) {
      emit('toast', nRes.body?.message || '搜索失败，请稍后重试')
      notes.value = []
    } else {
      notes.value = nRes.body.data || []
    }

    loading.value = false

    // 先有笔记结果再问 AI，保证引用与下方列表同源
    const [uRes] = await Promise.all([
      usersPromise,
      runAiStream(keyword, toNoteCandidates(notes.value), signal),
    ])

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
  aiActiveCite.value = null
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
  let t = String(text || '')
    .replace(/^\s*usedSources\s*:\s*\[[^\]]*\]\s*$/gim, '')
    // 旧回退/模型偶发泄露的工程师痕迹
    .replace(/（站内笔记\s*id\s*=\s*\d+）/g, '')
    .replace(/（未配置\s*LLM[_A-Z]*[^\n）]*）/g, '')
    .replace(/^\s*站内有相关笔记\s*[:：]?\s*$/gim, '')
    .replace(/^\s*网络参考\s*[:：]?\s*$/gim, '')
    .replace(/^\s*链接\s*[:：]\s*\S+\s*$/gim, '')
  if (stripPrefix) {
    t = t.replace(new RegExp(`^${UNGROUNDED_PREFIX}\\s*`), '')
  }
  return t.trim()
}

/** 轻量 Markdown → HTML；[笔记N]/[网络M] 渲染为可点角标（id 不外露） */
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
      .replace(/\[笔记(\d+)\]/g, (_, n) => {
        return `<button type="button" class="ai-cite" data-note-cite="${n}" title="查看引用笔记">${n}</button>`
      })
      .replace(/\[网络(\d+)\]/g, (_, n) => {
        return `<button type="button" class="ai-cite ai-cite-web" data-web-cite="${n}" title="查看引用网页">${n}</button>`
      })

  for (const raw of lines) {
    const line = raw.trimEnd()
    const bullet = line.match(/^[-*•·]\s+(.+)/)
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

function noteSourceByCite(idx) {
  const n = Number(idx)
  if (!n) return null
  return (
    aiNoteSources.value.find((s) => Number(s.citeIndex) === n) ||
    aiNoteSources.value[n - 1] ||
    null
  )
}

function webSourceByCite(idx) {
  const n = Number(idx)
  if (!n) return null
  return (
    aiWebSources.value.find((s) => Number(s.citeIndex) === n) ||
    aiWebSources.value[n - 1] ||
    null
  )
}

function flashCite(idx) {
  const n = Number(idx)
  if (!n) return
  aiActiveCite.value = n
  clearTimeout(aiHighlightTimer)
  aiHighlightTimer = setTimeout(() => {
    if (aiActiveCite.value === n) aiActiveCite.value = null
  }, 1600)
  nextTick(() => {
    document
      .querySelector(`.ai-ref-row[data-cite="${n}"]`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

function openCitedNote(src, citeIdx) {
  if (citeIdx != null) flashCite(citeIdx)
  if (src?.id) emit('open-note', src.id)
}

function onAiAnswerClick(e) {
  const noteBtn = e.target?.closest?.('[data-note-cite]')
  if (noteBtn) {
    const idx = noteBtn.getAttribute('data-note-cite')
    const src = noteSourceByCite(idx)
    flashCite(idx)
    if (src?.id) emit('open-note', src.id)
    return
  }
  const webBtn = e.target?.closest?.('[data-web-cite]')
  if (webBtn) {
    const idx = webBtn.getAttribute('data-web-cite')
    flashCite(idx)
    const src = webSourceByCite(idx)
    if (src?.url) window.open(src.url, '_blank', 'noopener,noreferrer')
  }
}

function citeSnippet(n, max = 36) {
  const t = String(n.snippet || '').replace(/\s+/g, ' ').trim()
  if (!t) return '查看原文'
  return t.length > max ? `${t.slice(0, max)}…` : t
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
          type="text"
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
      <section v-if="showAiGuest" class="ai-panel ai-guest">
        <div class="ai-panel-hd">
          <div class="ai-panel-title">
            <span class="ai-mark" aria-hidden="true" />
            <div>
              <h3>智能回答</h3>
              <p class="ai-sub">登录后根据站内笔记提炼要点</p>
            </div>
          </div>
        </div>
        <div class="ai-panel-bd">
          <p class="ai-guest-copy">把相关笔记读完，整理成一段能直接用的回答；点引用还能回到原文。</p>
          <button type="button" class="ai-guest-btn" @click="$emit('need-login')">登录解锁</button>
        </div>
      </section>

      <section v-if="showAiBlock" class="ai-panel">
        <div class="ai-panel-hd">
          <div class="ai-panel-title">
            <span class="ai-mark" aria-hidden="true" />
            <div>
              <h3>智能回答</h3>
              <p class="ai-sub">{{ aiUngrounded ? '补充说明' : '依据站内笔记' }}</p>
            </div>
          </div>
        </div>

        <div v-if="aiLoading" class="ai-panel-bd">
          <div class="ai-skel" aria-hidden="true">
            <span class="ai-skel-line w92" />
            <span class="ai-skel-line w78" />
            <span class="ai-skel-line w86" />
            <span class="ai-skel-line w54" />
          </div>
          <p class="ai-loading-hint">正在阅读相关笔记…</p>
        </div>

        <div v-else-if="aiAnswer || aiStreaming" class="ai-panel-bd">
          <p v-if="aiUngrounded" class="ai-ungrounded">未直接命中笔记，以下为补充说明</p>
          <div v-if="aiStreaming" class="ai-answer ai-streaming">
            {{ aiAnswerDisplay }}<span class="ai-cursor" aria-hidden="true" />
          </div>
          <div v-else class="ai-answer" v-html="aiAnswerHtml" @click="onAiAnswerClick" />

          <div v-if="!aiStreaming && aiNoteSources.length" class="ai-refs">
            <div class="ai-refs-hd">
              <span>来源</span>
              <em>{{ aiNoteSources.length }} 篇笔记</em>
            </div>
            <div class="ai-ref-list">
              <button
                v-for="n in aiNoteSources"
                :key="n.id"
                type="button"
                class="ai-ref-row"
                :class="{ on: aiActiveCite != null && Number(n.citeIndex) === aiActiveCite }"
                :data-cite="n.citeIndex"
                @click="openCitedNote(n, n.citeIndex)"
              >
                <span class="ai-ref-thumb">
                  <SignedImg v-if="n.coverUrl" :src="toLocalMediaUrl(n.coverUrl)" alt="" />
                  <span v-else class="ai-ref-thumb-ph" />
                </span>
                <span class="ai-ref-body">
                  <span class="ai-ref-top">
                    <span v-if="n.citeIndex != null" class="ai-ref-no">{{ n.citeIndex }}</span>
                    <strong>{{ n.title || '未命名笔记' }}</strong>
                  </span>
                  <em>{{ citeSnippet(n, 42) }}</em>
                </span>
              </button>
            </div>
          </div>

          <div v-if="!aiStreaming && aiWebSources.length" class="ai-webs">
            <div class="ai-refs-hd">
              <span>网页</span>
              <em>{{ aiWebSources.length }} 条</em>
            </div>
            <ul>
              <li v-for="(w, i) in aiWebSources" :key="i">
                <span v-if="w.citeIndex != null" class="ai-ref-no muted-no">{{ w.citeIndex }}</span>
                <a :href="w.url" target="_blank" rel="noopener noreferrer">{{ w.title }}</a>
              </li>
            </ul>
          </div>
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
        <p v-if="!notes.length" class="muted pad">没有相关笔记，换个词试试</p>
        <div v-else class="xhs-waterfall pad-notes">
          <NoteCard
            v-for="n in notes"
            :key="n.id"
            :note="n"
            :author-name="n.nickname || n.username || ''"
            :author-avatar="n.avatarUrl || ''"
            :like-count="n.likeCount || 0"
            @open="(note) => $emit('open-note', note.id)"
            @open-profile="(uid) => $emit('open-profile', uid)"
          />
        </div>
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

.search > header {
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
section.ai-panel { padding: 0; }

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

/* —— AI 智能回答：纸感摘要卡 —— */
.ai-panel {
  margin: 0.75rem 0.85rem 0.35rem;
  padding: 0;
  border: 1px solid rgba(30, 41, 59, 0.08);
  border-radius: 16px;
  background:
    radial-gradient(120% 80% at 100% 0%, rgba(255, 36, 66, 0.06), transparent 55%),
    linear-gradient(180deg, #fffdfb 0%, #f7f4f0 100%);
  overflow: hidden;
}

.ai-panel-hd {
  padding: 0.95rem 1rem 0.55rem;
}

.ai-panel-title {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
}

.ai-mark {
  flex: 0 0 auto;
  width: 2rem;
  height: 2rem;
  margin-top: 0.1rem;
  border-radius: 10px;
  background: linear-gradient(145deg, #ff5a6e 0%, var(--brand) 55%, #c9182e 100%);
  position: relative;
  box-shadow: 0 6px 14px rgba(255, 36, 66, 0.22);
}

.ai-mark::after {
  content: "AI";
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.ai-panel-title h3 {
  margin: 0;
  font-family: "ZCOOL XiaoWei", "Noto Sans SC", serif;
  font-size: 1.22rem;
  font-weight: 400;
  letter-spacing: 0.04em;
  color: #1a1520;
  line-height: 1.25;
}

.ai-sub {
  margin: 0.18rem 0 0;
  font-size: 0.72rem;
  color: var(--ink-3);
  letter-spacing: 0.06em;
}

.ai-panel-bd {
  padding: 0.15rem 1rem 1.05rem;
}

.ai-answer {
  font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 0.98rem;
  font-weight: 400;
  line-height: 1.85;
  letter-spacing: 0.02em;
  color: #2a2430;
  text-wrap: pretty;
}

.ai-answer :deep(p) {
  margin: 0 0 0.85rem;
}

.ai-answer :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-answer :deep(ul) {
  margin: 0 0 0.85rem;
  padding-left: 1.05rem;
}

.ai-answer :deep(li) {
  margin-bottom: 0.45rem;
  padding-left: 0.15rem;
}

.ai-answer :deep(li::marker) {
  color: rgba(255, 36, 66, 0.55);
}

.ai-answer :deep(strong) {
  font-weight: 600;
  color: #1a1520;
}

.ai-streaming {
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-cursor {
  display: inline-block;
  width: 2px;
  height: 1.05em;
  margin-left: 2px;
  vertical-align: text-bottom;
  background: var(--brand);
  animation: ai-blink 0.9s step-end infinite;
}

@keyframes ai-blink {
  50% { opacity: 0; }
}

.ai-ungrounded {
  margin: 0 0 0.75rem;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--ink-2);
  font-size: 0.78rem;
  font-weight: 500;
  letter-spacing: 0.02em;
}

.ai-answer :deep(.ai-cite) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.05rem;
  height: 1.05rem;
  margin: 0 0.08rem 0 0.18rem;
  padding: 0 0.22rem;
  border: none;
  border-radius: 4px;
  background: rgba(255, 36, 66, 0.1);
  color: var(--brand-deep);
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  vertical-align: 0.18em;
  transition: background 0.15s ease, transform 0.15s ease;
}

.ai-answer :deep(.ai-cite:hover),
.ai-answer :deep(.ai-cite:active) {
  background: rgba(255, 36, 66, 0.2);
  transform: translateY(-1px);
}

.ai-answer :deep(.ai-cite-web) {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.ai-refs,
.ai-webs {
  margin-top: 1.05rem;
  padding-top: 0.85rem;
  border-top: 1px solid rgba(30, 41, 59, 0.07);
}

.ai-refs-hd {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 0.55rem;
}

.ai-refs-hd span {
  font-family: "ZCOOL XiaoWei", serif;
  font-size: 0.92rem;
  letter-spacing: 0.06em;
  color: #1a1520;
}

.ai-refs-hd em {
  font-style: normal;
  font-size: 0.7rem;
  color: var(--ink-3);
}

.ai-ref-list {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.ai-ref-row {
  display: grid;
  grid-template-columns: 3.1rem 1fr;
  gap: 0.7rem;
  align-items: center;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 12px;
  padding: 0.4rem 0.45rem 0.4rem 0.4rem;
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  cursor: pointer;
  color: inherit;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.ai-ref-row:hover {
  background: #fff;
  border-color: rgba(255, 36, 66, 0.18);
}

.ai-ref-row.on {
  background: #fff;
  border-color: rgba(255, 36, 66, 0.45);
  box-shadow: 0 0 0 1px rgba(255, 36, 66, 0.12);
}

.ai-ref-thumb {
  width: 3.1rem;
  height: 3.1rem;
  border-radius: 9px;
  overflow: hidden;
  background: #efe8e2;
}

.ai-ref-thumb :deep(img),
.ai-ref-thumb-ph {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.ai-ref-thumb-ph {
  background: linear-gradient(145deg, #ffd0d8 0%, #f0ebe6 70%);
}

.ai-ref-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
}

.ai-ref-top {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
}

.ai-ref-no {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1rem;
  height: 1rem;
  padding: 0 0.22rem;
  border-radius: 4px;
  background: rgba(255, 36, 66, 0.12);
  color: var(--brand-deep);
  font-size: 0.66rem;
  font-weight: 700;
}

.ai-ref-no.muted-no {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.ai-ref-body strong {
  flex: 1;
  min-width: 0;
  font-size: 0.86rem;
  font-weight: 600;
  color: #1a1520;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-ref-body em {
  font-style: normal;
  font-size: 0.74rem;
  line-height: 1.4;
  color: var(--ink-2);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ai-webs ul {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.ai-webs li {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
}

.ai-webs a {
  flex: 1;
  min-width: 0;
  font-size: 0.82rem;
  color: #1a1520;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-bottom: 1px solid transparent;
}

.ai-webs a:hover {
  color: var(--brand);
  border-bottom-color: rgba(255, 36, 66, 0.35);
}

.ai-guest-copy {
  margin: 0 0 0.85rem;
  font-size: 0.92rem;
  line-height: 1.7;
  letter-spacing: 0.02em;
  color: #3a3340;
}

.ai-guest-btn {
  border: none;
  border-radius: 999px;
  padding: 0.48rem 1.15rem;
  background: var(--brand);
  color: #fff;
  font-size: 0.84rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  cursor: pointer;
  box-shadow: 0 8px 18px rgba(255, 36, 66, 0.2);
}

.ai-guest-btn:active {
  transform: scale(0.98);
}

.ai-skel {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.2rem 0 0.55rem;
}

.ai-skel-line {
  display: block;
  height: 0.72rem;
  border-radius: 999px;
  background: linear-gradient(
    90deg,
    rgba(30, 41, 59, 0.06) 0%,
    rgba(255, 36, 66, 0.1) 45%,
    rgba(30, 41, 59, 0.06) 100%
  );
  background-size: 200% 100%;
  animation: ai-shimmer 1.35s ease-in-out infinite;
}

.ai-skel-line.w92 { width: 92%; }
.ai-skel-line.w78 { width: 78%; }
.ai-skel-line.w86 { width: 86%; }
.ai-skel-line.w54 { width: 54%; }

.ai-loading-hint {
  margin: 0;
  font-size: 0.76rem;
  color: var(--ink-3);
  letter-spacing: 0.04em;
}

@keyframes ai-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}

.ai-error {
  color: #c62828;
  font-size: 0.82rem;
}

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

.user-list {
  list-style: none;
  margin: 0;
  padding: 0.4rem 0 1rem;
}

.pad-notes {
  padding: 0.75rem 0.7rem 1.2rem;
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
