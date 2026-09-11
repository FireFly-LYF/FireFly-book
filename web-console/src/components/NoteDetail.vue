<script setup>
import { computed, ref, watch } from 'vue'
import { noteApi, socialApi, toLocalMediaUrl, userApi, newIdempotencyKey } from '../api'
import UserAvatar from './UserAvatar.vue'
import SignedImg from './SignedImg.vue'

const props = defineProps({
  noteId: { type: [Number, String], default: null },
  open: Boolean,
  loggedIn: Boolean,
})
const emit = defineEmits(['close', 'need-login', 'toast', 'open-profile'])

const detail = ref(null)
const author = ref(null)
const likeCount = ref(0)
const liked = ref(false)
const collected = ref(false)
const comments = ref([])
const commentText = ref('')
const commentIdemKey = ref(null)
const loading = ref(false)
/** 正在回复的评论：{ id, nickname }；null=发一级评论 */
const replyTo = ref(null)

const cover = computed(() => toLocalMediaUrl(detail.value?.coverUrl))

/** 楼中楼：一级 + 挂在其下的二级 */
const commentThreads = computed(() => {
  const list = comments.value || []
  const byId = new Map(list.map((c) => [c.id, c]))
  const replies = new Map()
  const roots = []
  for (const c of list) {
    if (!c.parentId) {
      roots.push(c)
      continue
    }
    const rootId = byId.has(c.parentId) ? c.parentId : null
    if (rootId == null) {
      roots.push(c)
      continue
    }
    if (!replies.has(rootId)) replies.set(rootId, [])
    replies.get(rootId).push(c)
  }
  return roots.map((root) => ({
    root,
    replies: replies.get(root.id) || [],
  }))
})

const commentPlaceholder = computed(() =>
  replyTo.value ? `回复 ${replyTo.value.nickname}…` : '说点什么…',
)

watch(
  () => [props.open, props.noteId],
  async ([open, id]) => {
    if (!open || !id) return
    replyTo.value = null
    commentText.value = ''
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
  const [c, meLike, meCollect, list] = await Promise.all([
    api.likeCount(id),
    props.loggedIn ? api.likedByMe(id) : Promise.resolve({ body: null }),
    props.loggedIn ? api.collectedByMe(id) : Promise.resolve({ body: null }),
    api.comments(id),
  ])
  if (c.body?.code === 0) {
    const d = c.body.data
    likeCount.value = typeof d === 'number' ? d : Number(d?.count ?? 0)
  }
  if (meLike.body?.code === 0) {
    const d = meLike.body.data
    liked.value = typeof d === 'boolean' ? d : !!d?.liked
  }
  if (meCollect.body?.code === 0) {
    const d = meCollect.body.data
    collected.value = typeof d === 'boolean' ? d : !!d?.collected
  }
  if (list.body?.code === 0) {
    comments.value = list.body.data || []
  }
}

function commentNick(c) {
  return c?.nickname || `用户${c?.userId ?? ''}`
}

function startReply(c) {
  if (!props.loggedIn) return emit('need-login')
  replyTo.value = { id: c.id, nickname: commentNick(c) }
}

function cancelReply() {
  replyTo.value = null
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
    if (c.body?.code === 0) {
      const d = c.body.data
      likeCount.value = typeof d === 'number' ? d : Number(d?.count ?? 0)
    }
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
  if (!commentIdemKey.value) commentIdemKey.value = newIdempotencyKey()
  const res = await socialApi().comment({
    noteId: Number(props.noteId),
    content: text,
    parentId: replyTo.value?.id ?? null,
  }, commentIdemKey.value)
  if (res.body?.code === 0) {
    commentIdemKey.value = null
    commentText.value = ''
    replyTo.value = null
    await refreshSocial(props.noteId)
  } else emit('toast', res.body?.message || '评论失败')
}
function goAuthor() {
  const uid = detail.value?.userId || author.value?.id
  if (!uid) return
  emit('open-profile', Number(uid))
}
</script>

<template>
  <div v-if="open" class="mask" @click.self="$emit('close')">
    <div class="panel">
      <div class="cover" :class="{ empty: !cover }">
        <SignedImg v-if="cover" :src="cover" alt="" />
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
              <button type="button" class="author-hit" @click="goAuthor">
                <UserAvatar
                  :user="author"
                  :user-id="detail.userId"
                  :name="author?.nickname || author?.username || `用户${detail.userId}`"
                  :size="36"
                />
                <div>
                  <strong>{{ author?.nickname || author?.username || `用户${detail.userId}` }}</strong>
                  <div class="muted">id={{ detail.userId }} · 笔记 #{{ detail.id }}</div>
                </div>
              </button>
              <button type="button" class="follow-btn" @click="goAuthor">主页</button>
            </div>
          </div>

          <section class="comments">
            <h3>评论 {{ comments.length }}</h3>
            <div v-if="commentThreads.length" class="thread-list">
              <div v-for="t in commentThreads" :key="t.root.id" class="thread">
                <div class="c-row">
                  <UserAvatar
                    :user-id="t.root.userId"
                    :avatar-url="t.root.avatarUrl || ''"
                    :name="commentNick(t.root)"
                    :size="28"
                  />
                  <div class="c-body">
                    <strong>{{ commentNick(t.root) }}</strong>
                    <span>{{ t.root.content }}</span>
                    <button type="button" class="reply-btn" @click="startReply(t.root)">回复</button>
                  </div>
                </div>
                <div v-if="t.replies.length" class="replies">
                  <div v-for="r in t.replies" :key="r.id" class="c-row reply">
                    <UserAvatar
                      :user-id="r.userId"
                      :avatar-url="r.avatarUrl || ''"
                      :name="commentNick(r)"
                      :size="24"
                    />
                    <div class="c-body">
                      <strong>{{ commentNick(r) }}</strong>
                      <span>
                        <template v-if="r.replyToNickname">
                          回复 <em>@{{ r.replyToNickname }}</em>：
                        </template>{{ r.content }}
                      </span>
                      <button type="button" class="reply-btn" @click="startReply(r)">回复</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <p v-else class="muted">还没有评论，来抢沙发</p>
          </section>
        </div>

        <footer>
          <div v-if="replyTo" class="reply-bar">
            <span>回复 {{ replyTo.nickname }}</span>
            <button type="button" class="cancel-reply" @click="cancelReply">取消</button>
          </div>
          <div class="footer-row">
            <input
              v-model="commentText"
              :placeholder="commentPlaceholder"
              @keyup.enter="sendComment"
            />
            <button type="button" class="act" :class="{ on: liked }" @click="toggleLike">
              {{ liked ? '♥' : '♡' }} {{ likeCount }}
            </button>
            <button type="button" class="act" :class="{ on: collected }" @click="toggleCollect">
              {{ collected ? '★' : '☆' }}
            </button>
            <button type="button" class="send" @click="sendComment">发送</button>
          </div>
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

.author-hit {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  border: none;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
  color: inherit;
  min-width: 0;
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
  flex-shrink: 0;
}

.follow-btn {
  margin-left: auto;
  padding: 0.35rem 0.7rem;
  border-radius: 14px;
  border: none;
  background: var(--xhs-red);
  color: #fff;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
}

.comments {
  padding: 0 1rem 1.2rem;
  border-top: 1px solid var(--line);
}

.comments h3 {
  margin: 0.8rem 0;
  font-size: 0.92rem;
}

.thread-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.thread {
  padding: 0.35rem 0 0.55rem;
  border-bottom: 1px solid #f3f3f3;
}

.c-row {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: 0.55rem;
  font-size: 0.86rem;
}

.c-row.reply {
  margin-top: 0.45rem;
}

.replies {
  margin: 0.35rem 0 0 2.1rem;
  padding: 0.35rem 0.55rem;
  border-radius: 10px;
  background: #f7f7f8;
}

.comments .c-body {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
  flex: 1;
}

.c-body em {
  font-style: normal;
  color: var(--ink-2);
  font-weight: 600;
}

.reply-btn {
  align-self: flex-start;
  margin-top: 0.15rem;
  border: none;
  background: transparent;
  padding: 0;
  color: var(--ink-3);
  font-size: 0.75rem;
  cursor: pointer;
}

.reply-btn:hover {
  color: var(--xhs-red);
}

.muted { color: var(--ink-3); font-size: 0.78rem; }
.pad { padding: 2rem 1rem; }

footer {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.55rem 0.7rem calc(0.55rem + var(--safe-bottom));
  background: #fff;
  border-top: 1px solid var(--line);
}

.reply-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.25rem 0.35rem;
  border-radius: 10px;
  background: #f5f5f5;
  font-size: 0.78rem;
  color: var(--ink-2);
}

.cancel-reply {
  border: none;
  background: transparent;
  color: var(--ink-3);
  font-size: 0.75rem;
  cursor: pointer;
}

.footer-row {
  display: flex;
  gap: 0.4rem;
  align-items: center;
}

.footer-row input {
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
