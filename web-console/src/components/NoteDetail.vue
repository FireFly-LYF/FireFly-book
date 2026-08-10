<script setup>
import { computed, ref, watch } from 'vue'
import { noteApi, socialApi, toLocalMediaUrl, userApi } from '../api'
import TodoBadge from './TodoBadge.vue'

const props = defineProps({
  noteId: { type: [Number, String], default: null },
  open: Boolean,
  loggedIn: Boolean,
})
const emit = defineEmits(['close', 'need-login', 'toast'])

const detail = ref(null)
const author = ref(null)
const likeCount = ref(0)
const liked = ref(false)
const collected = ref(false)
const comments = ref([])
const commentText = ref('')
const loading = ref(false)
const nicknames = ref({})

const cover = computed(() => toLocalMediaUrl(detail.value?.coverUrl))

watch(
  () => [props.open, props.noteId],
  async ([open, id]) => {
    if (!open || !id) return
    await load(id)
  },
)

async function load(id) {
  loading.value = true
  try {
    const res = await noteApi().detail(id)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '笔记加载失败')
      return
    }
    const data = res.body.data
    // NoteDetailResponse: { note, mediaUrls }
    detail.value = data?.note
      ? { ...data.note, mediaUrls: data.mediaUrls || [] }
      : data
    const uid = detail.value?.userId
    if (uid) {
      const u = await userApi().getById(uid)
      if (u.body?.code === 0) author.value = u.body.data
    }
    await refreshSocial(id)
  } finally {
    loading.value = false
  }
}

async function refreshSocial(id) {
  const api = socialApi()
  const [c, me, list] = await Promise.all([
    api.likeCount(id),
    props.loggedIn ? api.likedByMe(id) : Promise.resolve({ body: null }),
    api.comments(id),
  ])
  if (c.body?.code === 0) likeCount.value = c.body.data ?? 0
  if (me.body?.code === 0) liked.value = !!me.body.data
  if (list.body?.code === 0) {
    comments.value = list.body.data || []
    const ids = comments.value.map((x) => x.userId)
    await ensureNicks(ids)
  }
}

async function ensureNicks(ids) {
  const missing = [...new Set(ids.filter(Boolean))].filter((id) => !nicknames.value[id])
  await Promise.all(
    missing.map(async (id) => {
      const res = await userApi().getById(id)
      if (res.body?.code === 0) {
        const u = res.body.data
        nicknames.value = {
          ...nicknames.value,
          [id]: u.nickname || u.username || `用户${id}`,
        }
      }
    }),
  )
}

function nick(uid) {
  return nicknames.value[uid] || `用户${uid}`
}

async function toggleLike() {
  if (!props.loggedIn) return emit('need-login')
  const api = socialApi()
  const res = liked.value
    ? await api.unlike(props.noteId)
    : await api.like(props.noteId)
  if (res.body?.code === 0) {
    liked.value = !liked.value
    const c = await api.likeCount(props.noteId)
    if (c.body?.code === 0) likeCount.value = c.body.data ?? 0
  } else emit('toast', res.body?.message || '操作失败')
}

async function toggleCollect() {
  if (!props.loggedIn) return emit('need-login')
  const api = socialApi()
  const res = collected.value
    ? await api.uncollect(props.noteId)
    : await api.collect(props.noteId)
  if (res.body?.code === 0) collected.value = !collected.value
  else emit('toast', res.body?.message || '操作失败')
}

async function sendComment() {
  if (!props.loggedIn) return emit('need-login')
  const text = commentText.value.trim()
  if (!text) return
  const res = await socialApi().comment({
    noteId: Number(props.noteId),
    content: text,
    parentId: null,
  })
  if (res.body?.code === 0) {
    commentText.value = ''
    await refreshSocial(props.noteId)
  } else emit('toast', res.body?.message || '评论失败')
}
</script>

<template>
  <div v-if="open" class="mask" @click.self="$emit('close')">
    <div class="panel">
      <div class="cover" :class="{ empty: !cover }">
        <img v-if="cover" :src="cover" alt="" />
        <div v-else class="cover-ph">{{ (detail?.title || '笔记').slice(0, 1) }}</div>
        <button type="button" class="close-float" @click="$emit('close')">×</button>
      </div>

      <div class="side">
        <header>
          <button type="button" class="back" @click="$emit('close')">←</button>
          <strong>笔记详情</strong>
          <span />
        </header>

        <div v-if="loading" class="muted pad">加载中…</div>
        <div v-else-if="detail" class="scroll">
          <div class="content">
            <h2>{{ detail.title || '无标题' }}</h2>
            <p class="text">{{ detail.content }}</p>
            <div class="author-row">
              <span class="av">{{ (author?.nickname || 'U').slice(0, 1) }}</span>
              <div>
                <strong>{{ author?.nickname || author?.username || `用户${detail.userId}` }}</strong>
                <div class="muted">id={{ detail.userId }} · 笔记 #{{ detail.id }}</div>
              </div>
              <button type="button" class="follow-btn" disabled>
                关注 <TodoBadge text="列表内可用" />
              </button>
            </div>
          </div>

          <section class="comments">
            <h3>评论 {{ comments.length }}</h3>
            <ul v-if="comments.length">
              <li v-for="c in comments" :key="c.id">
                <strong>{{ nick(c.userId) }}</strong>
                <span>{{ c.content }}</span>
              </li>
            </ul>
            <p v-else class="muted">还没有评论，来抢沙发</p>
          </section>
        </div>

        <footer>
          <input v-model="commentText" placeholder="说点什么…" @keyup.enter="sendComment" />
          <button type="button" class="act" :class="{ on: liked }" @click="toggleLike">
            {{ liked ? '♥' : '♡' }} {{ likeCount }}
          </button>
          <button type="button" class="act" :class="{ on: collected }" @click="toggleCollect">
            {{ collected ? '★' : '☆' }}
          </button>
          <button type="button" class="send" @click="sendComment">发送</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: stretch;
  justify-content: center;
}

.panel {
  width: 100%;
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  animation: rise 0.2s ease;
  overflow: hidden;
}

@keyframes rise {
  from { transform: translateY(12px); opacity: 0.7; }
  to { transform: none; opacity: 1; }
}

.cover {
  position: relative;
  background: #111;
  flex: 0 0 auto;
}

.cover img {
  width: 100%;
  max-height: 42vh;
  object-fit: contain;
  margin: 0 auto;
  background: #111;
}

.cover-ph {
  min-height: 180px;
  display: grid;
  place-items: center;
  font-size: 2.4rem;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(145deg, #ff6b81, #ff2442);
}

.close-float { display: none; }

.side {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

header {
  display: grid;
  grid-template-columns: 40px 1fr 40px;
  align-items: center;
  padding: 0.65rem 0.75rem;
  border-bottom: 1px solid var(--line);
}

header strong { text-align: center; }

.scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.content { padding: 0.9rem 1rem; }

h2 {
  margin: 0 0 0.5rem;
  font-size: 1.1rem;
}

.text {
  margin: 0;
  color: var(--ink-2);
  white-space: pre-wrap;
  font-size: 0.92rem;
}

.author-row {
  margin-top: 1rem;
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.av {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--xhs-red-soft);
  color: var(--xhs-red);
  display: grid;
  place-items: center;
  font-weight: 700;
}

.follow-btn {
  margin-left: auto;
  padding: 0.35rem 0.7rem;
  border-radius: 14px;
  background: #f5f5f5;
  font-size: 0.78rem;
  color: var(--ink-2);
}

.comments {
  padding: 0 1rem 1.2rem;
  border-top: 1px solid var(--line);
}

.comments h3 {
  margin: 0.8rem 0;
  font-size: 0.92rem;
}

.comments li {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.45rem 0;
  border-bottom: 1px solid #f3f3f3;
  font-size: 0.86rem;
}

.muted { color: var(--ink-3); font-size: 0.78rem; }
.pad { padding: 2rem 1rem; }

footer {
  display: flex;
  gap: 0.4rem;
  align-items: center;
  padding: 0.55rem 0.7rem calc(0.55rem + var(--safe-bottom));
  background: #fff;
  border-top: 1px solid var(--line);
}

footer input {
  flex: 1;
  border-radius: 18px;
  background: #f5f5f5;
  border: none;
  padding: 0.55rem 0.85rem;
}

.act {
  min-width: 44px;
  color: var(--ink-2);
  font-size: 0.85rem;
}
.act.on { color: var(--xhs-red); }

.send {
  padding: 0.45rem 0.75rem;
  border-radius: 14px;
  background: var(--xhs-red);
  color: #fff;
  font-size: 0.82rem;
  font-weight: 600;
}

@media (min-width: 900px) {
  .mask {
    align-items: center;
    padding: 2rem;
  }

  .panel {
    width: min(1100px, 100%);
    height: min(820px, 90vh);
    max-height: 90vh;
    border-radius: 16px;
    flex-direction: row;
  }

  .cover {
    flex: 1.15;
    height: 100%;
    display: grid;
    place-items: center;
  }

  .cover img {
    max-height: 100%;
    width: 100%;
    height: 100%;
    object-fit: contain;
  }

  .cover-ph { width: 100%; height: 100%; min-height: 100%; }

  .close-float {
    display: grid;
    place-items: center;
    position: absolute;
    top: 12px;
    left: 12px;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.45);
    color: #fff;
    font-size: 1.2rem;
  }

  .side {
    flex: 0 0 420px;
    width: 420px;
    border-left: 1px solid var(--line);
  }

  .back { visibility: hidden; }
}
</style>
