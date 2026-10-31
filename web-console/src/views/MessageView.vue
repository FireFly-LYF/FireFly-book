<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { imApi, notifyApi, userApi } from '../api'
import UserAvatar from '../components/UserAvatar.vue'
import { formatRelativeTime } from '../time'

const props = defineProps({
  loggedIn: Boolean,
  userId: { type: [Number, null], default: null },
  /** 从主页「私信」跳入时传入对方 uid */
  openPeerId: { type: [Number, null], default: null },
})
const emit = defineEmits(['need-login', 'toast', 'unread', 'open-profile', 'open-note', 'open-market', 'peer-opened'])

const tab = ref('notify')
const rawNotifies = ref([])
const names = ref({})
const avatars = ref({})
const loading = ref(false)

const conversations = ref([])
const chatLoading = ref(false)
const activeId = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
let pollTimer = null

const typeLabel = {
  LIKE: '赞了你',
  COMMENT: '评论了你',
  FOLLOW: '关注了你',
  ORDER_PAID: '订单已付款',
  ORDER_CANCELLED: '取消了订单',
  ORDER_REFUNDED: '退款已完成',
  IM: '发来私信',
}

/** 通知 Tab 不展示 IM：私信只走会话列表，避免与未读角标双重计数 */
const list = computed(() =>
  (rawNotifies.value || []).filter((n) => String(n.type || '').toUpperCase() !== 'IM'),
)

const activeConv = computed(() =>
  conversations.value.find((c) => Number(c.id) === Number(activeId.value)) || null,
)

watch(
  () => props.loggedIn,
  (v) => {
    if (v) {
      load()
      loadConversations()
      startPoll()
    } else {
      rawNotifies.value = []
      conversations.value = []
      messages.value = []
      activeId.value = null
      emit('unread', 0)
      stopPoll()
    }
  },
)

watch(
  () => props.openPeerId,
  async (pid) => {
    if (!pid || !props.loggedIn) return
    tab.value = 'chat'
    await openPeer(Number(pid))
    emit('peer-opened')
  },
)

onMounted(async () => {
  if (props.loggedIn) {
    await Promise.all([load(), loadConversations()])
    startPoll()
    if (props.openPeerId) {
      tab.value = 'chat'
      await openPeer(Number(props.openPeerId))
      emit('peer-opened')
    }
  }
})

onUnmounted(() => stopPoll())

function isUnreadNotify(n) {
  // 后端 isRead 是 0/1，不能用 !n.isRead（对字符串等也不稳）
  return !(n?.isRead === 1 || n?.isRead === true || n?.isRead === '1')
}

function startPoll() {
  stopPoll()
  pollTimer = setInterval(async () => {
    if (!props.loggedIn) return
    if (tab.value === 'chat') {
      if (activeId.value) await pullMessages(true)
      else await loadConversations(true)
    }
    await refreshUnreadBadge()
  }, 4000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function load() {
  if (!props.loggedIn) return emit('need-login')
  loading.value = true
  try {
    const res = await notifyApi().list(1, 50)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '加载失败')
      return
    }
    rawNotifies.value = res.body.data || []
    await ensureNicks(list.value.map((n) => n.fromUserId))
    await refreshUnreadBadge()
  } finally {
    loading.value = false
  }
}

async function loadConversations(silent = false) {
  if (!props.loggedIn) return
  if (!silent) chatLoading.value = true
  try {
    const res = await imApi().conversations(50)
    if (res.body?.code !== 0) {
      if (!silent) emit('toast', res.body?.message || '会话加载失败')
      return
    }
    conversations.value = res.body.data || []
    await ensureNicks(conversations.value.map((c) => c.peerUserId))
    await refreshUnreadBadge()
  } finally {
    if (!silent) chatLoading.value = false
  }
}

async function refreshUnreadBadge() {
  if (!props.loggedIn) {
    emit('unread', 0)
    return
  }
  try {
    const [nRes, iRes] = await Promise.all([
      notifyApi().list(1, 50),
      imApi().unreadCount(),
    ])
    if (nRes.body?.code === 0) {
      rawNotifies.value = nRes.body.data || []
    }
    const notifyUnread = (rawNotifies.value || [])
      .filter((n) => String(n.type || '').toUpperCase() !== 'IM')
      .filter(isUnreadNotify).length
    const imUnread = iRes.body?.code === 0 ? Number(iRes.body.data?.count || 0) : 0
    emit('unread', notifyUnread + imUnread)
  } catch {
    /* ignore */
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
        avatars.value = { ...avatars.value, [id]: u.avatarUrl || '' }
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
  const unreadIds = list.value.filter(isUnreadNotify).map((n) => n.id)
  if (!unreadIds.length) {
    emit('toast', '没有未读通知')
    return
  }
  const res = await notifyApi().read({ ids: unreadIds })
  if (res.body?.code === 0) {
    emit('toast', '已全部已读')
    await load()
  } else {
    emit('toast', res.body?.message || '操作失败')
  }
}

async function onNotifyClick(n) {
  if (isUnreadNotify(n)) {
    await notifyApi().read({ ids: [n.id] })
  }
  const t = String(n.type || '').toUpperCase()
  if (t === 'IM' && n.fromUserId) {
    tab.value = 'chat'
    await openPeer(Number(n.fromUserId))
    return
  }
  if ((t === 'LIKE' || t === 'COMMENT') && n.refId) {
    emit('open-note', Number(n.refId))
    return
  }
  if (t.startsWith('ORDER_')) {
    emit('open-market', 'orders')
    return
  }
  if (n.fromUserId) emit('open-profile', n.fromUserId)
}

async function openPeer(peerUserId) {
  if (!props.loggedIn) return emit('need-login')
  const res = await imApi().open(peerUserId)
  if (res.body?.code !== 0) {
    emit('toast', res.body?.message || '打开会话失败')
    return
  }
  const conv = res.body.data
  await loadConversations(true)
  await selectConversation(conv.id)
}

async function selectConversation(id) {
  activeId.value = Number(id)
  messages.value = []
  await pullMessages(false)
  const last = messages.value[messages.value.length - 1]
  await imApi().read(id, last?.id)
  // 打开会话后顺手清掉该好友的 IM 通知，避免脏数据
  const peerId = conversations.value.find((c) => Number(c.id) === Number(id))?.peerUserId
  const imIds = (rawNotifies.value || [])
    .filter((n) => String(n.type || '').toUpperCase() === 'IM')
    .filter((n) => !peerId || Number(n.fromUserId) === Number(peerId))
    .filter(isUnreadNotify)
    .map((n) => n.id)
  if (imIds.length) {
    await notifyApi().read({ ids: imIds })
  }
  await loadConversations(true)
  await refreshUnreadBadge()
  await nextTick()
  scrollChatBottom()
}

async function pullMessages(silent) {
  if (!activeId.value) return
  const afterId = silent && messages.value.length
    ? messages.value[messages.value.length - 1].id
    : 0
  const res = await imApi().messages(activeId.value, afterId, 80)
  if (res.body?.code !== 0) {
    if (!silent) emit('toast', res.body?.message || '消息加载失败')
    return
  }
  const rows = res.body.data || []
  if (!silent || !afterId) {
    messages.value = rows
  } else if (rows.length) {
    const known = new Set(messages.value.map((m) => m.id))
    const fresh = rows.filter((m) => !known.has(m.id))
    if (fresh.length) {
      messages.value = [...messages.value, ...fresh]
      const last = fresh[fresh.length - 1]
      await imApi().read(activeId.value, last.id)
      await nextTick()
      scrollChatBottom()
    }
  }
}

function scrollChatBottom() {
  const el = document.getElementById('ff-chat-scroll')
  if (el) el.scrollTop = el.scrollHeight
}

async function sendMessage() {
  const text = draft.value.trim()
  if (!text || !activeId.value || sending.value) return
  if (!props.loggedIn) return emit('need-login')
  sending.value = true
  try {
    const res = await imApi().send(activeId.value, text)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '发送失败')
      return
    }
    draft.value = ''
    const msg = res.body.data
    if (msg && !messages.value.some((m) => m.id === msg.id)) {
      messages.value = [...messages.value, msg]
    } else {
      await pullMessages(false)
    }
    await loadConversations(true)
    await nextTick()
    scrollChatBottom()
  } finally {
    sending.value = false
  }
}

function backToList() {
  activeId.value = null
  messages.value = []
  loadConversations()
}

function onChatTab() {
  tab.value = 'chat'
  loadConversations()
}

function formatTime(t) {
  return formatRelativeTime(t) || ''
}
</script>

<template>
  <div class="msg">
    <header>
      <div class="tabs">
        <button type="button" :class="{ on: tab === 'notify' }" @click="tab = 'notify'; load()">通知</button>
        <button type="button" :class="{ on: tab === 'chat' }" @click="onChatTab">私信</button>
      </div>
      <button v-if="tab === 'notify'" type="button" class="all" @click="markAll">全部已读</button>
      <button
        v-else-if="activeId"
        type="button"
        class="all"
        @click="backToList"
      >
        ← 返回
      </button>
    </header>

    <template v-if="tab === 'chat'">
      <p v-if="!loggedIn" class="empty">登录后收发私信</p>

      <div v-else-if="activeId" class="chat">
        <div class="chat-head">
          <button type="button" class="peer" @click="$emit('open-profile', activeConv?.peerUserId)">
            <UserAvatar
              :user-id="activeConv?.peerUserId"
              :avatar-url="avatars[activeConv?.peerUserId] || ''"
              :name="nick(activeConv?.peerUserId)"
              :size="36"
            />
            <strong>{{ nick(activeConv?.peerUserId) }}</strong>
          </button>
        </div>
        <div id="ff-chat-scroll" class="chat-body">
          <div
            v-for="m in messages"
            :key="m.id"
            class="bubble"
            :class="{ me: Number(m.senderId) === Number(userId) }"
          >
            <p>{{ m.content }}</p>
            <span>{{ formatTime(m.createdAt) }}</span>
          </div>
          <p v-if="!messages.length" class="tip-inline">打个招呼吧</p>
        </div>
        <form class="composer" @submit.prevent="sendMessage">
          <input
            v-model="draft"
            type="text"
            maxlength="1000"
            placeholder="输入私信…"
            autocomplete="off"
          />
          <button type="submit" :disabled="sending || !draft.trim()">发送</button>
        </form>
      </div>

      <template v-else>
        <p v-if="chatLoading" class="tip">加载中…</p>
        <ul v-else-if="conversations.length" class="list">
          <li
            v-for="c in conversations"
            :key="c.id"
            :class="{ unread: Number(c.unreadCount) > 0 }"
          >
            <button type="button" class="hit" @click="selectConversation(c.id)">
              <UserAvatar
                :user-id="c.peerUserId"
                :avatar-url="avatars[c.peerUserId] || ''"
                :name="nick(c.peerUserId)"
                :size="40"
              />
              <div class="body">
                <div class="row-top">
                  <strong>{{ nick(c.peerUserId) }}</strong>
                  <em v-if="Number(c.unreadCount) > 0" class="badge">{{ c.unreadCount }}</em>
                </div>
                <div class="muted preview">{{ c.lastPreview || '暂无消息' }}</div>
                <div class="muted">{{ formatTime(c.lastMessageAt) }}</div>
              </div>
            </button>
          </li>
        </ul>
        <p v-else class="empty">还没有私信。去别人主页点「私信」开始聊天。</p>
      </template>
    </template>

    <template v-else>
      <p v-if="!loggedIn" class="empty">登录后查看点赞、评论、关注通知</p>
      <p v-else-if="loading" class="tip">加载中…</p>
      <ul v-else-if="list.length" class="list">
        <li v-for="n in list" :key="n.id" :class="{ unread: isUnreadNotify(n) }">
          <button type="button" class="hit" @click="onNotifyClick(n)">
            <UserAvatar
              :user-id="n.fromUserId"
              :avatar-url="avatars[n.fromUserId] || ''"
              :name="nick(n.fromUserId)"
              :size="40"
            />
            <div class="body">
              <div>
                <strong>{{ nick(n.fromUserId) }}</strong>
                {{ typeLabel[n.type] || n.type }}
                <span v-if="n.content" class="muted">「{{ n.content }}」</span>
              </div>
              <div class="muted">{{ formatTime(n.createdAt) }}</div>
            </div>
          </button>
          <button v-if="isUnreadNotify(n)" type="button" class="read" @click.stop="markOne(n.id)">已读</button>
        </li>
      </ul>
      <p v-else class="empty">暂无通知</p>
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
  transition: color var(--dur-fast) ease, transform var(--dur-fast) var(--ease-out);
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
  animation: ff-card-in 0.3s var(--ease-out) both;
  transition: background-color var(--dur-fast) ease;
}

.list li.unread { background: #fff8f9; }

.hit {
  flex: 1;
  display: flex;
  gap: 0.7rem;
  align-items: flex-start;
  border: none;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
  color: inherit;
  min-width: 0;
}

.body { flex: 1; min-width: 0; font-size: 0.88rem; }
.row-top {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.badge {
  font-style: normal;
  font-size: 0.7rem;
  background: var(--xhs-red);
  color: #fff;
  border-radius: 8px;
  padding: 0 0.35rem;
  line-height: 1.3;
}
.preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
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

.chat {
  display: flex;
  flex-direction: column;
  min-height: 60vh;
}

.chat-head {
  padding: 0.6rem 1rem;
  border-bottom: 1px solid #f3f3f3;
}

.peer {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  color: inherit;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  max-height: calc(70vh - 140px);
  min-height: 280px;
}

.bubble {
  max-width: 78%;
  align-self: flex-start;
  background: #f5f5f5;
  border-radius: 12px 12px 12px 4px;
  padding: 0.55rem 0.75rem;
}

.bubble.me {
  align-self: flex-end;
  background: #ffe8ec;
  border-radius: 12px 12px 4px 12px;
}

.bubble p {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.45;
  word-break: break-word;
}

.bubble span {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.68rem;
  color: var(--ink-3);
}

.tip-inline {
  text-align: center;
  color: var(--ink-3);
  font-size: 0.85rem;
  margin: 2rem 0;
}

.composer {
  display: flex;
  gap: 0.5rem;
  padding: 0.7rem 1rem 1rem;
  border-top: 1px solid var(--line);
}

.composer input {
  flex: 1;
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 0.55rem 0.9rem;
  font-size: 0.9rem;
}

.composer button {
  border: none;
  border-radius: 18px;
  padding: 0.55rem 1rem;
  background: var(--xhs-red);
  color: #fff;
  font-weight: 600;
  font-size: 0.88rem;
}

.composer button:disabled {
  opacity: 0.45;
}
</style>
