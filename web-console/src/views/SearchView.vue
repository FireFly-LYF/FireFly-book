<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { searchApi, toLocalMediaUrl } from '../api'
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
  searched.value = true
  loading.value = true
  notes.value = []
  users.value = []
  try {
    const [nRes, uRes] = await Promise.all([
      searchApi().notes(keyword, 1, 20),
      searchApi().users(keyword),
    ])
    if (nRes.status === 401 || uRes.status === 401) {
      emit('need-login')
      return
    }
    if (nRes.body?.code !== 0) {
      emit('toast', nRes.body?.message || '笔记搜索失败（请确认 search-service / ES）')
    } else {
      notes.value = nRes.body.data || []
    }
    if (uRes.body?.code !== 0) {
      emit('toast', uRes.body?.message || '用户搜索失败')
    } else {
      users.value = uRes.body.data || []
    }
  } catch (err) {
    emit('toast', err?.message || '搜索请求失败')
  } finally {
    loading.value = false
  }
}

function clearQuery() {
  q.value = ''
  searched.value = false
  notes.value = []
  users.value = []
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
