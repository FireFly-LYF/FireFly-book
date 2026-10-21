<script setup>
import { computed, ref, watch } from 'vue'
import { noteApi, socialApi, toLocalMediaUrl, userApi, newIdempotencyKey } from '../api'
import UserAvatar from './UserAvatar.vue'
import SignedImg from './SignedImg.vue'

const props = defineProps({
  noteId: { type: [Number, String], default: null },
  open: Boolean,
  loggedIn: Boolean,
  currentUserId: { type: [Number, String, null], default: null },
})
const emit = defineEmits(['close', 'need-login', 'toast', 'open-profile'])

const detail = ref(null)
const author = ref(null)
const likeCount = ref(0)
const liked = ref(false)
const collected = ref(false)
const followedByMe = ref(false)
const comments = ref([])
const commentText = ref('')
const commentIdemKey = ref(null)
const loading = ref(false)
const likePop = ref(false)
const collectPop = ref(false)
/** 正在回复的评论：{ id, nickname }；null=发一级评论 */
const replyTo = ref(null)

function bumpPop(flag) {
  flag.value = false
  requestAnimationFrame(() => {
    flag.value = true
    setTimeout(() => {
      flag.value = false
    }, 380)
  })
}

const isOwnNote = computed(() => {
  const uid = detail.value?.userId ?? author.value?.id
  if (uid == null || props.currentUserId == null) return false
  return Number(uid) === Number(props.currentUserId)
})

const showFollow = computed(() => !isOwnNote.value)

const mediaList = computed(() => {
  const d = detail.value
  if (!d) return []
  const raw = Array.isArray(d.mediaUrls) && d.mediaUrls.length
    ? d.mediaUrls
    : d.coverUrl
      ? [d.coverUrl]
      : []
  return raw.map((u) => toLocalMediaUrl(u)).filter(Boolean)
})

const mediaIndex = ref(0)
const activeMedia = computed(
  () => mediaList.value[mediaIndex.value] || mediaList.value[0] || '',
)

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

const commentPlaceholder = '说点什么…'

watch(
  () => [props.open, props.noteId, props.loggedIn],
  async ([open, id]) => {
    if (!open || !id) return
    replyTo.value = null
    commentText.value = ''
    mediaIndex.value = 0
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
      await refreshFollow(uid)
    } else {
      followedByMe.value = false
    }
    await refreshSocial(id)
  } finally {
    loading.value = false
  }
}

async function refreshFollow(authorId) {
  followedByMe.value = false
  if (!props.loggedIn || !props.currentUserId) return
  if (Number(authorId) === Number(props.currentUserId)) return
  const mine = await userApi().following(props.currentUserId)
  if (mine.body?.code === 0) {
    const list = mine.body.data || []
    followedByMe.value = list.some((u) => Number(u.id) === Number(authorId))
  }
}

async function toggleFollow() {
  if (!props.loggedIn) return emit('need-login')
  const id = detail.value?.userId || author.value?.id
  if (!id || isOwnNote.value) return
  const res = followedByMe.value
    ? await userApi().unfollow(id)
    : await userApi().follow(id)
  if (res.body?.code === 0) {
    followedByMe.value = !followedByMe.value
    emit('toast', followedByMe.value ? '已关注' : '已取消关注')
  } else {
    emit('toast', res.body?.message || '操作失败')
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
    if (liked.value) bumpPop(likePop)
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
  if (res.body?.code === 0) {
    collected.value = !collected.value
    if (collected.value) bumpPop(collectPop)
  } else emit('toast', res.body?.message || '操作失败')
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
      <div class="cover" :class="{ empty: !activeMedia }">
        <SignedImg v-if="activeMedia" :src="activeMedia" alt="" />
        <div v-else class="cover-ph">{{ (detail?.title || '笔记').slice(0, 1) }}</div>
        <div v-if="mediaList.length > 1" class="media-nav">
          <button
            type="button"
            class="nav-btn"
            :disabled="mediaIndex <= 0"
            @click="mediaIndex = Math.max(0, mediaIndex - 1)"
          >
            ‹
          </button>
          <span class="media-dots">{{ mediaIndex + 1 }} / {{ mediaList.length }}</span>
          <button
            type="button"
            class="nav-btn"
            :disabled="mediaIndex >= mediaList.length - 1"
            @click="mediaIndex = Math.min(mediaList.length - 1, mediaIndex + 1)"
          >
            ›
          </button>
        </div>
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
                </div>
              </button>
              <div class="author-actions">
                <button
                  v-if="showFollow"
                  type="button"
                  class="follow-btn"
                  :class="{ ghost: followedByMe }"
                  @click="toggleFollow"
                >
                  {{ followedByMe ? '已关注' : '关注' }}
                </button>
                <button type="button" class="home-btn" @click="goAuthor">主页</button>
              </div>
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
            <button
              type="button"
              class="act"
              :class="{ on: liked, pop: likePop }"
              aria-label="点赞"
              @click="toggleLike"
            >
              <svg class="ico" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <path
                  v-if="liked"
                  fill="currentColor"
                  d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
                />
                <path
                  v-else
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linejoin="round"
                  d="M12 20.5s-7.2-4.6-8.7-8.9C2.2 8.7 3.7 5.8 6.6 5.3c1.7-.3 3.4.5 4.4 1.8 1-1.3 2.7-2.1 4.4-1.8 2.9.5 4.4 3.4 3.3 6.3C19.2 15.9 12 20.5 12 20.5z"
                />
              </svg>
              <span>{{ likeCount }}</span>
            </button>
            <button
              type="button"
              class="act"
              :class="{ on: collected, pop: collectPop }"
              aria-label="收藏"
              @click="toggleCollect"
            >
              <svg class="ico" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
                <path
                  v-if="collected"
                  fill="currentColor"
                  d="M6 2.8A1.8 1.8 0 0 1 7.8 1h8.4A1.8 1.8 0 0 1 18 2.8V22l-6-3.6L6 22V2.8z"
                />
                <path
                  v-else
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linejoin="round"
                  d="M7 3.2h10a1 1 0 0 1 1 1V20.5l-6-3.5-6 3.5V4.2a1 1 0 0 1 1-1z"
                />
              </svg>
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
  animation: ff-fade-in var(--dur) ease;
}

.panel {
  width: 100%;
  height: 100%;
  background: #fff;
  display: flex;
  flex-direction: column;
  animation: ff-rise var(--dur-slow) var(--ease-out);
  overflow: hidden;
}

.cover {
  position: relative;
  background: #111;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  overflow: hidden;
}

/* SignedImg 是子组件，scoped 必须用 :deep 才能作用到内部 img */
.cover :deep(img) {
  display: block;
  width: 100%;
  max-height: 42vh;
  height: auto;
  object-fit: contain;
  margin: 0 auto;
  background: #111;
}

.media-nav {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  pointer-events: none;
}

.media-nav .nav-btn,
.media-nav .media-dots {
  pointer-events: auto;
}

.media-nav .nav-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 1.2rem;
  line-height: 1;
  cursor: pointer;
}

.media-nav .nav-btn:disabled {
  opacity: 0.35;
  cursor: default;
}

.media-dots {
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 0.75rem;
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

.author-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-shrink: 0;
}

.follow-btn,
.home-btn {
  padding: 0.35rem 0.7rem;
  border-radius: 14px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
}

.follow-btn {
  border: none;
  background: var(--xhs-red);
  color: #fff;
}

.follow-btn.ghost {
  background: #f5f5f5;
  color: var(--ink-2);
}

.home-btn {
  border: 1px solid var(--line);
  background: #fff;
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
  border-radius: 16px;
  background: #f5f5f5;
  border: none;
  padding: 0.4rem 0.75rem;
  font-size: 0.8rem;
  line-height: 1.35;
}

.footer-row input::placeholder {
  font-size: 0.8rem;
  color: var(--ink-3);
}

.act {
  min-width: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.28rem;
  color: var(--ink-2);
  font-size: 0.82rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  transition: color var(--dur-fast) ease, transform var(--dur-fast) var(--ease-out);
}
.act .ico {
  flex-shrink: 0;
  display: block;
}
.act.on { color: var(--xhs-red); }
.act.pop {
  animation: ff-pop 0.38s var(--ease-spring);
}

.send {
  padding: 0.45rem 0.75rem;
  border-radius: 14px;
  background: var(--xhs-red);
  color: #fff;
  font-size: 0.82rem;
  font-weight: 600;
}

.follow-btn,
.home-btn,
.send {
  transition:
    background-color var(--dur-fast) ease,
    color var(--dur-fast) ease,
    border-color var(--dur-fast) ease,
    transform var(--dur-fast) var(--ease-out),
    box-shadow var(--dur-fast) ease;
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
    min-height: 0;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .cover :deep(img) {
    max-width: 100%;
    max-height: 100%;
    width: auto;
    height: auto;
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
