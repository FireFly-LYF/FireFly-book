<script setup>
import { computed, ref, watch } from 'vue'
import { mediaApi, noteApi, toLocalMediaUrl, userApi } from './api'

const tab = ref('user')
const log = ref([])
const session = ref(loadSession())

const reg = ref({ username: '', password: '123456', nickname: '' })
const loginForm = ref({ username: '', password: '123456' })

const uploaded = ref([])
const noteForm = ref({ title: '', content: '', coverUrl: '' })
const noteId = ref('')
const noteDetail = ref(null)
const noteList = ref([])

const userId = computed(() => session.value?.id ?? null)

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

function logout() {
  saveSession(null)
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

watch(tab, () => {
  /* keep session */
})
</script>

<template>
  <div class="page">
    <header class="hero">
      <div>
        <p class="eyebrow">FireFly · Dev Console</p>
        <h1>联调台</h1>
        <p class="sub">注册登录 → 上传图片 → 发笔记，可视化走通 P0 主链路</p>
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
}
</style>
