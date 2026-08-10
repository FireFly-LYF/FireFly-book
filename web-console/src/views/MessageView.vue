<script setup>
import { onMounted, ref, watch } from 'vue'
import TodoBadge from '../components/TodoBadge.vue'
import { notifyApi, userApi } from '../api'

const props = defineProps({
  loggedIn: Boolean,
  userId: { type: [Number, null], default: null },
})
const emit = defineEmits(['need-login', 'toast', 'unread'])

const tab = ref('notify')
const list = ref([])
const names = ref({})
const loading = ref(false)

const typeLabel = { LIKE: '赞了你', COMMENT: '评论了你', FOLLOW: '关注了你' }

watch(
  () => props.loggedIn,
  (v) => {
    if (v) load()
    else {
      list.value = []
      emit('unread', 0)
    }
  },
)

onMounted(() => {
  if (props.loggedIn) load()
})

async function load() {
  if (!props.loggedIn) return emit('need-login')
  loading.value = true
  try {
    const res = await notifyApi().list(1, 50)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '加载失败')
      return
    }
    list.value = res.body.data || []
    const unread = list.value.filter((n) => !n.isRead).length
    emit('unread', unread)
    await ensureNicks(list.value.map((n) => n.fromUserId))
  } finally {
    loading.value = false
  }
}

async function ensureNicks(ids) {
  const missing = [...new Set(ids.filter(Boolean))].filter((id) => !names.value[id])
  await Promise.all(
    missing.map(async (id) => {
      const res = await userApi().getById(id)
      if (res.body?.code === 0) {
        const u = res.body.data
        names.value = { ...names.value, [id]: u.nickname || u.username || `用户${id}` }
      }
    }),
  )
}

function nick(uid) {
  return names.value[uid] || `用户${uid}`
}

async function markOne(id) {
  const res = await notifyApi().read({ ids: [id] })
  if (res.body?.code === 0) await load()
}

async function markAll() {
  const res = await notifyApi().read({ all: true })
  if (res.body?.code === 0) {
    emit('toast', '已全部已读')
    await load()
  }
}
</script>

<template>
  <div class="msg">
    <header>
      <div class="tabs">
        <button type="button" :class="{ on: tab === 'notify' }" @click="tab = 'notify'">通知</button>
        <button type="button" :class="{ on: tab === 'chat' }" @click="tab = 'chat'">
          私信 <TodoBadge />
        </button>
      </div>
      <button v-if="tab === 'notify'" type="button" class="all" @click="markAll">全部已读</button>
    </header>

    <div v-if="tab === 'chat'" class="empty">
      <p>私信会话列表</p>
      <TodoBadge text="IM / 私信待实现" />
    </div>

    <template v-else>
      <p v-if="!loggedIn" class="empty">登录后查看点赞、评论、关注通知</p>
      <p v-else-if="loading" class="tip">加载中…</p>
      <ul v-else-if="list.length" class="list">
        <li v-for="n in list" :key="n.id" :class="{ unread: !n.isRead }">
          <span class="av">{{ nick(n.fromUserId).slice(0, 1) }}</span>
          <div class="body">
            <div>
              <strong>{{ nick(n.fromUserId) }}</strong>
              {{ typeLabel[n.type] || n.type }}
              <span class="muted">{{ n.content || '' }}</span>
            </div>
            <div class="muted">#{{ n.id }} · {{ n.createdAt || '' }}</div>
          </div>
          <button v-if="!n.isRead" type="button" class="read" @click="markOne(n.id)">已读</button>
        </li>
      </ul>
      <p v-else class="empty">暂无通知</p>
      <button v-if="loggedIn" type="button" class="refresh" @click="load">刷新</button>
    </template>
  </div>
</template>

<style scoped>
.msg {
  min-height: 100%;
  background: #fff;
}

@media (min-width: 769px) {
  .msg {
    max-width: 800px;
    margin: 16px auto 0;
    border: 1px solid var(--line);
    border-radius: 12px;
    min-height: 70vh;
  }
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.7rem 1rem;
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 2;
}

.tabs { display: flex; gap: 1rem; }
.tabs button {
  font-size: 0.95rem;
  color: var(--ink-3);
  font-weight: 500;
}
.tabs button.on {
  color: var(--ink);
  font-weight: 700;
}

.all {
  font-size: 0.78rem;
  color: var(--ink-2);
}

.list { list-style: none; margin: 0; padding: 0; }

.list li {
  display: flex;
  gap: 0.7rem;
  align-items: flex-start;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid #f3f3f3;
}

.list li.unread { background: #fff8f9; }

.av {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--xhs-red-soft);
  color: var(--xhs-red);
  display: grid;
  place-items: center;
  font-weight: 700;
  flex-shrink: 0;
}

.body { flex: 1; min-width: 0; font-size: 0.88rem; }
.muted { color: var(--ink-3); font-size: 0.75rem; margin-top: 0.2rem; }

.read {
  font-size: 0.75rem;
  color: var(--xhs-red);
  padding: 0.25rem 0.4rem;
}

.empty, .tip {
  text-align: center;
  color: var(--ink-3);
  padding: 3rem 1rem;
  margin: 0;
}

.refresh {
  display: block;
  margin: 0 auto 1rem;
  padding: 0.45rem 1rem;
  border-radius: 14px;
  background: #f5f5f5;
  font-size: 0.82rem;
}
</style>
