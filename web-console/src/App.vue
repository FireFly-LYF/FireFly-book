<script setup>
import { computed, ref, watch } from 'vue'
import { mediaApi, noteApi, notifyApi, socialApi, toLocalMediaUrl, userApi } from './api'

const tab = ref('user')
const log = ref([])
const session = ref(loadSession())

const reg = ref({ username: '', password: '123456', nickname: '' })
const loginForm = ref({ username: '', password: '123456' })
const followTargetId = ref('')
const followers = ref([])
const following = ref([])

const uploaded = ref([])
const noteForm = ref({ title: '', content: '', coverUrl: '' })
const noteId = ref('')
const noteDetail = ref(null)
const noteList = ref([])

const likeCount = ref(0)
const liked = ref(false)
const collected = ref(false)
const comments = ref([])
const commentText = ref('')
const commentParentId = ref('')

const notifications = ref([])
/** userId -> nickname */
const nicknames = ref({})

const typeLabel = { LIKE: '点赞', COMMENT: '评论', FOLLOW: '关注' }

const userId = computed(() => session.value?.id ?? null)

function displayName(uid) {
  if (uid == null) return ''
  return nicknames.value[uid] || `用户${uid}`
}

function parentAuthorId(comment) {
  if (!comment?.parentId) return null
  const parent = comments.value.find((c) => c.id === comment.parentId)
  return parent?.userId ?? null
}

async function ensureNicknames(ids) {
  const missing = [...new Set(ids.filter(Boolean))].filter((id) => !nicknames.value[id])
  if (!missing.length) return
  const api = userApi()
  await Promise.all(
    missing.map(async (id) => {
      const res = await api.getById(id)
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

function loadSession() {
  try {
    return JSON.parse(localStorage.getItem('ff-session') || 'null')
  } catch {
    return null
  }
}

function saveSession(user) {
  session.value = user
  if (user) localStorage.setItem('ff-session', JSON.stringify(user))
  else localStorage.removeItem('ff-session')
}

function pushLog(title, payload) {
  log.value.unshift({
    time: new Date().toLocaleTimeString(),
    title,
    payload: typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2),
  })
  if (log.value.length > 30) log.value.pop()
}

async function doRegister() {
  const res = await userApi().register(reg.value)
  pushLog('注册', res.body)
  if (res.body?.code === 0) {
    saveSession(res.body.data)
    loginForm.value.username = reg.value.username
  }
}

async function doLogin() {
  const res = await userApi().login(loginForm.value)
  pushLog('登录', res.body)
  if (res.body?.code === 0) saveSession(res.body.data)
}

async function doMe() {
  if (!userId.value) return pushLog('当前用户', '请先登录')
  const res = await userApi().me(userId.value)
  pushLog('GET /me', res.body)
}

async function doFollow() {
  if (!userId.value) return pushLog('关注', '请先登录')
  const target = Number(followTargetId.value)
  if (!target) return pushLog('关注', '请填写对方用户 id')
  const res = await userApi().follow(userId.value, target)
  pushLog(`关注 #${target}`, res.body)
  if (res.body?.code === 0) await loadFollowLists()
}

async function doUnfollow() {
  if (!userId.value) return pushLog('取消关注', '请先登录')
  const target = Number(followTargetId.value)
  if (!target) return pushLog('取消关注', '请填写对方用户 id')
  const res = await userApi().unfollow(userId.value, target)
  pushLog(`取消关注 #${target}`, res.body)
  if (res.body?.code === 0) await loadFollowLists()
}

async function loadFollowLists() {
  if (!userId.value) return pushLog('关注列表', '请先登录')
  const api = userApi()
  const [fr, fg] = await Promise.all([
    api.followers(userId.value),
    api.following(userId.value),
  ])
  pushLog('粉丝列表', fr.body)
  pushLog('关注列表', fg.body)
  if (fr.body?.code === 0) followers.value = fr.body.data || []
  if (fg.body?.code === 0) following.value = fg.body.data || []
}

function logout() {
  saveSession(null)
  followers.value = []
  following.value = []
  notifications.value = []
  pushLog('退出', '已清除本地会话')
}

async function onPickFiles(e) {
  if (!userId.value) return pushLog('上传', '请先登录')
  const files = [...(e.target.files || [])]
  e.target.value = ''
  for (const file of files) {
    const res = await mediaApi().upload(userId.value, file)
    pushLog(`上传 ${file.name}`, res.body)
    if (res.body?.code === 0) {
      uploaded.value.unshift(res.body.data)
      if (!noteForm.value.coverUrl) noteForm.value.coverUrl = res.body.data.url
    }
  }
}

function removeUploaded(i) {
  uploaded.value.splice(i, 1)
}

async function createNote() {
  if (!userId.value) return pushLog('发笔记', '请先登录')
  const mediaUrls = uploaded.value.map((x) => x.url)
  const payload = {
    title: noteForm.value.title,
    content: noteForm.value.content,
    coverUrl: noteForm.value.coverUrl || mediaUrls[0] || '',
    mediaUrls,
  }
  const res = await noteApi().create(userId.value, payload)
  pushLog('发笔记', res.body)
  if (res.body?.code === 0) {
    noteDetail.value = res.body.data
    noteId.value = String(res.body.data?.note?.id ?? '')
  }
}

async function loadNote() {
  if (!noteId.value) return
  const res = await noteApi().detail(noteId.value)
  pushLog(`笔记详情 #${noteId.value}`, res.body)
  if (res.body?.code === 0) noteDetail.value = res.body.data
}

async function loadMyNotes() {
  if (!userId.value) return pushLog('我的笔记', '请先登录')
  const res = await noteApi().listByUser(userId.value, 1, 20)
  pushLog('我的笔记列表', res.body)
  if (res.body?.code === 0) noteList.value = res.body.data || []
}

async function deleteNote(id) {
  if (!userId.value) return
  const res = await noteApi().remove(userId.value, id)
  pushLog(`删除笔记 #${id}`, res.body)
  if (res.body?.code === 0) {
    await loadMyNotes()
    if (String(noteDetail.value?.note?.id) === String(id)) noteDetail.value = null
  }
}

async function refreshSocial() {
  if (!noteId.value) return pushLog('互动', '请填写笔记 id')
  const api = socialApi()
  const countRes = await api.likeCount(noteId.value)
  pushLog(`赞数 #${noteId.value}`, countRes.body)
  if (countRes.body?.code === 0) likeCount.value = countRes.body.data?.count ?? 0

  if (userId.value) {
    const meRes = await api.likedByMe(userId.value, noteId.value)
    pushLog(`是否已赞 #${noteId.value}`, meRes.body)
    if (meRes.body?.code === 0) liked.value = !!meRes.body.data?.liked
  } else {
    liked.value = false
  }

  const listRes = await api.comments(noteId.value)
  pushLog(`评论列表 #${noteId.value}`, listRes.body)
  if (listRes.body?.code === 0) {
    comments.value = listRes.body.data || []
    await ensureNicknames(comments.value.map((c) => c.userId))
  }
}

async function doLike() {
  if (!userId.value) return pushLog('点赞', '请先登录')
  if (!noteId.value) return pushLog('点赞', '请填写笔记 id')
  const res = await socialApi().like(userId.value, noteId.value)
  pushLog(`点赞 #${noteId.value}`, res.body)
  if (res.body?.code === 0) {
    liked.value = true
    await refreshSocial()
  }
}

async function doUnlike() {
  if (!userId.value) return pushLog('取消赞', '请先登录')
  if (!noteId.value) return pushLog('取消赞', '请填写笔记 id')
  const res = await socialApi().unlike(userId.value, noteId.value)
  pushLog(`取消赞 #${noteId.value}`, res.body)
  if (res.body?.code === 0) {
    liked.value = false
    await refreshSocial()
  }
}

async function doCollect() {
  if (!userId.value) return pushLog('收藏', '请先登录')
  if (!noteId.value) return pushLog('收藏', '请填写笔记 id')
  const res = await socialApi().collect(userId.value, noteId.value)
  pushLog(`收藏 #${noteId.value}`, res.body)
  if (res.body?.code === 0) collected.value = true
}

async function doUncollect() {
  if (!userId.value) return pushLog('取消收藏', '请先登录')
  if (!noteId.value) return pushLog('取消收藏', '请填写笔记 id')
  const res = await socialApi().uncollect(userId.value, noteId.value)
  pushLog(`取消收藏 #${noteId.value}`, res.body)
  if (res.body?.code === 0) collected.value = false
}

async function doComment() {
  if (!userId.value) return pushLog('发评论', '请先登录')
  if (!noteId.value) return pushLog('发评论', '请填写笔记 id')
  const parentRaw = commentParentId.value.trim()
  const payload = {
    noteId: Number(noteId.value),
    content: commentText.value,
    parentId: parentRaw === '' ? null : Number(parentRaw),
  }
  const res = await socialApi().comment(userId.value, payload)
  pushLog(`发评论 #${noteId.value}`, res.body)
  if (res.body?.code === 0) {
    commentText.value = ''
    commentParentId.value = ''
    await refreshSocial()
  }
}

async function loadNotifications() {
  if (!userId.value) return pushLog('通知', '请先登录')
  const res = await notifyApi().list(userId.value, 1, 50)
  pushLog('通知列表', res.body)
  if (res.body?.code === 0) {
    notifications.value = res.body.data || []
    await ensureNicknames(notifications.value.map((n) => n.fromUserId))
  }
}

async function markNotifyRead(ids) {
  if (!userId.value) return pushLog('已读', '请先登录')
  const res = await notifyApi().read(userId.value, { ids })
  pushLog('标已读', res.body)
  if (res.body?.code === 0) await loadNotifications()
}

async function markAllNotifyRead() {
  if (!userId.value) return pushLog('全部已读', '请先登录')
  const res = await notifyApi().read(userId.value, { all: true })
  pushLog('全部已读', res.body)
  if (res.body?.code === 0) await loadNotifications()
}

watch(tab, (t) => {
  if (t === 'social' && noteId.value) refreshSocial()
  if (t === 'notify') loadNotifications()
  if (t === 'user' && userId.value) loadFollowLists()
})
</script>

<template>
  <div class="page">
    <header class="hero">
      <div>
        <p class="eyebrow">FireFly · Dev Console</p>
        <h1>联调台</h1>
        <p class="sub">注册登录 → 上传/发笔记 → 赞藏评/关注 → 通知，可视化走通主链路</p>
      </div>
      <div class="session" v-if="session">
        <div class="avatar">{{ session.nickname?.[0] || 'U' }}</div>
        <div>
          <strong>{{ session.nickname || session.username }}</strong>
          <div class="muted">id={{ session.id }} · {{ session.username }}</div>
        </div>
        <button class="ghost" type="button" @click="logout">退出</button>
      </div>
      <div class="session empty" v-else>尚未登录</div>
    </header>

    <nav class="tabs">
      <button type="button" :class="{ active: tab === 'user' }" class="tab" @click="tab = 'user'">用户</button>
      <button type="button" :class="{ active: tab === 'media' }" class="tab" @click="tab = 'media'">上传</button>
      <button type="button" :class="{ active: tab === 'note' }" class="tab" @click="tab = 'note'">笔记</button>
      <button type="button" :class="{ active: tab === 'social' }" class="tab" @click="tab = 'social'">互动</button>
      <button type="button" :class="{ active: tab === 'notify' }" class="tab" @click="tab = 'notify'">通知</button>
    </nav>

    <main class="grid">
      <section class="panel" v-show="tab === 'user'">
        <h2>注册</h2>
        <div class="field">
          <label>用户名</label>
          <input v-model="reg.username" placeholder="alice" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="reg.password" type="password" />
        </div>
        <div class="field">
          <label>昵称</label>
          <input v-model="reg.nickname" placeholder="小红" />
        </div>
        <button type="button" @click="doRegister">注册并登录</button>

        <h2 class="mt">登录</h2>
        <div class="field">
          <label>用户名</label>
          <input v-model="loginForm.username" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="loginForm.password" type="password" />
        </div>
        <div class="row">
          <button type="button" @click="doLogin">登录</button>
          <button type="button" class="ghost" @click="doMe">拉取 /me</button>
        </div>

        <h2 class="mt">关注</h2>
        <p class="hint">走 user-service；关注成功后会经 RabbitMQ 给对方写 FOLLOW 通知</p>
        <div class="row">
          <input v-model="followTargetId" placeholder="对方用户 id" style="max-width: 140px" />
          <button type="button" @click="doFollow">关注</button>
          <button type="button" class="ghost" @click="doUnfollow">取消关注</button>
          <button type="button" class="ghost" @click="loadFollowLists">刷新列表</button>
        </div>
        <div class="follow-cols" v-if="followers.length || following.length">
          <div>
            <h3 class="list-title">粉丝 {{ followers.length }}</h3>
            <ul class="user-list">
              <li v-for="u in followers" :key="'fr-' + u.id">
                <strong>{{ u.nickname || u.username }}</strong>
                <span class="muted">id={{ u.id }}</span>
              </li>
            </ul>
          </div>
          <div>
            <h3 class="list-title">关注 {{ following.length }}</h3>
            <ul class="user-list">
              <li v-for="u in following" :key="'fg-' + u.id">
                <strong>{{ u.nickname || u.username }}</strong>
                <span class="muted">id={{ u.id }}</span>
              </li>
            </ul>
          </div>
        </div>
      </section>

      <section class="panel" v-show="tab === 'media'">
        <h2>上传图片</h2>
        <p class="hint">走 media-service `:9003`，字段名 <code>file</code>，Header <code>X-User-Id</code></p>
        <label class="upload">
          <input type="file" accept="image/*" multiple hidden @change="onPickFiles" />
          <span>选择图片上传</span>
        </label>
        <div class="thumbs" v-if="uploaded.length">
          <figure v-for="(item, i) in uploaded" :key="item.id || i">
            <img :src="toLocalMediaUrl(item.url)" :alt="item.url" />
            <figcaption>
              <a :href="toLocalMediaUrl(item.url)" target="_blank" rel="noreferrer">#{{ item.id }}</a>
              <button type="button" class="ghost tiny" @click="removeUploaded(i)">移除</button>
            </figcaption>
          </figure>
        </div>
      </section>

      <section class="panel" v-show="tab === 'note'">
        <h2>发笔记</h2>
        <p class="hint">会把「上传」页里的图片 url 作为 mediaUrls；封面默认第一张</p>
        <div class="field">
          <label>标题</label>
          <input v-model="noteForm.title" placeholder="周末探店" />
        </div>
        <div class="field">
          <label>正文</label>
          <textarea v-model="noteForm.content" rows="4" placeholder="这家咖啡真不错……" />
        </div>
        <div class="field">
          <label>封面 coverUrl</label>
          <input v-model="noteForm.coverUrl" placeholder="可自动带入上传结果" />
        </div>
        <div class="chip-row" v-if="uploaded.length">
          <span class="chip" v-for="u in uploaded" :key="u.url">{{ u.url.split('/').pop() }}</span>
        </div>
        <button type="button" @click="createNote">发布笔记</button>

        <h2 class="mt">查详情 / 列表</h2>
        <div class="row">
          <input v-model="noteId" placeholder="笔记 id" style="max-width: 140px" />
          <button type="button" class="ghost" @click="loadNote">查详情</button>
          <button type="button" class="ghost" @click="loadMyNotes">我的笔记</button>
        </div>

        <article class="detail" v-if="noteDetail?.note">
          <h3>{{ noteDetail.note.title }}</h3>
          <p>{{ noteDetail.note.content }}</p>
          <div class="thumbs">
            <figure v-for="(url, i) in noteDetail.mediaUrls || []" :key="i">
              <img :src="toLocalMediaUrl(url)" alt="" />
            </figure>
          </div>
        </article>

        <ul class="note-list" v-if="noteList.length">
          <li v-for="n in noteList" :key="n.id">
            <div>
              <strong>#{{ n.id }} {{ n.title }}</strong>
              <div class="muted">{{ n.content }}</div>
            </div>
            <div class="row">
              <button type="button" class="ghost tiny" @click="noteId = String(n.id); loadNote()">打开</button>
              <button type="button" class="ghost tiny" @click="deleteNote(n.id)">删除</button>
            </div>
          </li>
        </ul>
      </section>

      <section class="panel" v-show="tab === 'social'">
        <h2>赞 / 藏 / 评</h2>
        <p class="hint">走 social-service `:9004`；写接口需要 Header <code>X-User-Id</code></p>
        <div class="row">
          <input v-model="noteId" placeholder="笔记 id" style="max-width: 140px" />
          <button type="button" class="ghost" @click="refreshSocial">刷新状态</button>
        </div>

        <div class="social-status">
          <span>赞数 <strong>{{ likeCount }}</strong></span>
          <span>{{ liked ? '已赞' : '未赞' }}</span>
          <span>{{ collected ? '已收藏' : '未收藏' }}</span>
        </div>

        <div class="row wrap">
          <button type="button" @click="doLike" :disabled="liked">点赞</button>
          <button type="button" class="ghost" @click="doUnlike" :disabled="!liked">取消赞</button>
          <button type="button" @click="doCollect" :disabled="collected">收藏</button>
          <button type="button" class="ghost" @click="doUncollect" :disabled="!collected">取消收藏</button>
        </div>

        <h2 class="mt">发评论</h2>
        <div class="field">
          <label>内容</label>
          <textarea v-model="commentText" rows="3" placeholder="写得真好" maxlength="512" />
        </div>
        <div class="field">
          <label>parentId（可选，回复某条评论）</label>
          <input v-model="commentParentId" placeholder="留空=一级评论" style="max-width: 160px" />
        </div>
        <button type="button" @click="doComment">发表评论</button>

        <h2 class="mt">评论列表</h2>
        <ul class="comment-list" v-if="comments.length">
          <li v-for="c in comments" :key="c.id">
            <div>
              <strong>{{ displayName(c.userId) }}</strong>
              <span class="muted" v-if="parentAuthorId(c)"> · 回复 {{ displayName(parentAuthorId(c)) }}</span>
              <div>{{ c.content }}</div>
            </div>
            <button type="button" class="ghost tiny" @click="commentParentId = String(c.id)">回复</button>
          </li>
        </ul>
        <p class="muted" v-else>暂无评论，先刷新或发一条</p>
        <p class="hint mt">点赞/评论后，笔记作者可到「通知」页查看（需 content + notify + RabbitMQ）</p>
      </section>

      <section class="panel" v-show="tab === 'notify'">
        <h2>我的通知</h2>
        <p class="hint">走 notify-service `:9005`；列表按时间倒序。异步写入，点赞后可稍等再刷新</p>
        <div class="row wrap">
          <button type="button" @click="loadNotifications">刷新列表</button>
          <button type="button" class="ghost" @click="markAllNotifyRead" :disabled="!notifications.length">
            全部已读
          </button>
        </div>
        <ul class="notify-list" v-if="notifications.length">
          <li v-for="n in notifications" :key="n.id" :class="{ unread: !n.isRead }">
            <div>
              <span class="badge">{{ typeLabel[n.type] || n.type }}</span>
              <strong>{{ displayName(n.fromUserId) }}</strong>
              <span class="muted"> {{ n.content || '' }}</span>
              <div class="muted">
                #{{ n.id }}
                <template v-if="n.refId"> · ref={{ n.refId }}</template>
                <template v-if="n.createdAt"> · {{ n.createdAt }}</template>
                · {{ n.isRead ? '已读' : '未读' }}
              </div>
            </div>
            <button
              v-if="!n.isRead"
              type="button"
              class="ghost tiny"
              @click="markNotifyRead([n.id])"
            >
              标已读
            </button>
          </li>
        </ul>
        <p class="muted" v-else>暂无通知。用另一账号点赞/评论/关注当前用户后再刷新</p>
      </section>

      <aside class="panel log">
        <h2>响应日志</h2>
        <p class="hint">最近请求的 JSON，方便对照 Apifox / curl</p>
        <div v-if="!log.length" class="muted">暂无请求</div>
        <details v-for="(item, i) in log" :key="i" open>
          <summary>
            <span>{{ item.time }}</span>
            <strong>{{ item.title }}</strong>
          </summary>
          <pre>{{ item.payload }}</pre>
        </details>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.page {
  max-width: 1120px;
  margin: 0 auto;
  padding: 2rem 1.25rem 3rem;
}

.hero {
  display: flex;
  justify-content: space-between;
  gap: 1.5rem;
  align-items: flex-end;
  margin-bottom: 1.5rem;
  animation: rise 0.55s ease both;
}

.eyebrow {
  margin: 0 0 0.35rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 0.75rem;
  color: var(--ember-deep);
  font-weight: 700;
}

h1 {
  margin: 0;
  font-family: 'Instrument Serif', Georgia, serif;
  font-size: clamp(2.4rem, 5vw, 3.4rem);
  font-weight: 400;
  letter-spacing: -0.02em;
}

.sub {
  margin: 0.4rem 0 0;
  color: var(--ink-soft);
  max-width: 36rem;
}

.session {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow);
  min-width: 220px;
  animation: rise 0.7s ease both;
}

.session.empty {
  color: var(--ink-soft);
  justify-content: center;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--ink);
  color: #fff;
  font-weight: 700;
}

.muted { color: var(--ink-soft); font-size: 0.85rem; }

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 1rem;
  animation: rise 0.65s ease both;
}

.tab {
  background: transparent;
  color: var(--ink-soft);
  border: 1px solid transparent;
}

.tab.active {
  background: #fffdf8;
  color: var(--ink);
  border-color: var(--line);
  box-shadow: var(--shadow);
}

.grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 1rem;
  align-items: start;
}

.panel {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 20px;
  padding: 1.25rem 1.3rem 1.4rem;
  box-shadow: var(--shadow);
  animation: rise 0.75s ease both;
}

.panel h2 {
  margin: 0 0 0.85rem;
  font-size: 1.05rem;
}

.mt { margin-top: 1.6rem; }

.hint {
  margin: -0.3rem 0 1rem;
  color: var(--ink-soft);
  font-size: 0.88rem;
}

.upload {
  display: grid;
  place-items: center;
  min-height: 120px;
  border: 1.5px dashed var(--line);
  border-radius: 16px;
  background: rgba(255, 253, 248, 0.7);
  cursor: pointer;
  font-weight: 600;
  color: var(--ember-deep);
  transition: border-color 0.2s ease, background 0.2s ease;
}

.upload:hover {
  border-color: var(--ember);
  background: #fff;
}

.thumbs {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 0.75rem;
  margin-top: 1rem;
}

.thumbs figure {
  margin: 0;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--line);
}

.thumbs img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
}

.thumbs figcaption {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.4rem 0.5rem;
  font-size: 0.78rem;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.9rem;
}

.chip {
  font-size: 0.75rem;
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  background: var(--paper-2);
  color: var(--ink-soft);
}

.detail {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--line);
}

.note-list {
  list-style: none;
  padding: 0;
  margin: 1rem 0 0;
}

.note-list li {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--line);
}

.social-status {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem 1.25rem;
  margin: 1rem 0;
  padding: 0.75rem 0.9rem;
  background: var(--paper-2);
  border-radius: 12px;
  font-size: 0.92rem;
}

.row.wrap { flex-wrap: wrap; }

.comment-list {
  list-style: none;
  padding: 0;
  margin: 0.5rem 0 0;
}

.comment-list li {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: flex-start;
  padding: 0.7rem 0;
  border-bottom: 1px solid var(--line);
}

.follow-cols {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-top: 1rem;
}

.list-title {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
}

.user-list,
.notify-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.user-list li {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.45rem 0;
  border-bottom: 1px solid var(--line);
  font-size: 0.9rem;
}

.notify-list {
  margin-top: 1rem;
}

.notify-list li {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: flex-start;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--line);
}

.notify-list li.unread {
  background: linear-gradient(90deg, rgba(232, 120, 72, 0.08), transparent);
  margin: 0 -0.5rem;
  padding-left: 0.5rem;
  padding-right: 0.5rem;
  border-radius: 8px;
}

.badge {
  display: inline-block;
  margin-right: 0.4rem;
  padding: 0.1rem 0.45rem;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--ember-deep);
  background: var(--paper-2);
  border-radius: 6px;
  vertical-align: middle;
}

button.tiny {
  padding: 0.35rem 0.6rem;
  font-size: 0.8rem;
}

.log details {
  margin-top: 0.65rem;
  border-top: 1px solid var(--line);
  padding-top: 0.55rem;
}

.log summary {
  cursor: pointer;
  display: flex;
  gap: 0.6rem;
  align-items: baseline;
}

.log summary span {
  color: var(--ink-soft);
  font-size: 0.8rem;
}

.log pre {
  margin: 0.5rem 0 0;
  padding: 0.75rem;
  background: #1b212b;
  color: #e8edf5;
  border-radius: 12px;
  overflow: auto;
  font-size: 0.78rem;
  max-height: 220px;
}

@keyframes rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: none; }
}

@media (max-width: 860px) {
  .hero { flex-direction: column; align-items: stretch; }
  .grid { grid-template-columns: 1fr; }
  .follow-cols { grid-template-columns: 1fr; }
}
</style>
