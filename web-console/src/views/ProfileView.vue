<script setup>
import { computed, ref, watch } from 'vue'
import NoteCard from '../components/NoteCard.vue'
import UserAvatar from '../components/UserAvatar.vue'
import { DEFAULT_AVATARS, resolveAvatarUrl } from '../avatars'
import { noteApi, socialApi, userApi } from '../api'

const props = defineProps({
  session: { type: Object, default: null },
  /** 查看他人主页时传入；空则展示当前登录用户 */
  profileUserId: { type: [Number, String, null], default: null },
})
const emit = defineEmits([
  'need-login',
  'logout',
  'open-note',
  'open-profile',
  'back',
  'toast',
  'profile-updated',
])

const me = computed(() => props.session?.user || null)
const targetId = computed(() => {
  if (props.profileUserId != null && props.profileUserId !== '') {
    return Number(props.profileUserId)
  }
  return me.value?.id ?? null
})
const isSelf = computed(() => {
  if (!me.value?.id || targetId.value == null) return !props.profileUserId
  return Number(me.value.id) === Number(targetId.value)
})

const profile = ref(null)
const notes = ref([])
const likedNotes = ref([])
const collectedNotes = ref([])
const likes = ref({})
const authorNames = ref({})
const authorAvatars = ref({})
const followers = ref([])
const following = ref([])
const followedByMe = ref(false)
const loading = ref(false)
const tab = ref('notes')
const listTab = ref(null) // null | followers | following
const editOpen = ref(false)
const saving = ref(false)
const editForm = ref({ nickname: '', bio: '', avatarUrl: '' })

watch(
  () => [targetId.value, me.value?.id, me.value?.avatarUrl, me.value?.nickname],
  () => {
    if (targetId.value) refresh()
    else reset()
  },
  { immediate: true },
)

watch(tab, (t) => {
  if (t === 'likes' && !likedNotes.value.length) loadLiked()
  if (t === 'collect' && !collectedNotes.value.length) loadCollected()
})

function reset() {
  profile.value = null
  notes.value = []
  likedNotes.value = []
  collectedNotes.value = []
  followers.value = []
  following.value = []
  followedByMe.value = false
  listTab.value = null
  tab.value = 'notes'
}

function countOf(body) {
  const d = body?.data
  if (typeof d === 'number') return d
  if (d && typeof d.count === 'number') return d.count
  return 0
}

async function refresh() {
  const uid = targetId.value
  if (!uid) return
  loading.value = true
  likedNotes.value = []
  collectedNotes.value = []
  try {
    if (isSelf.value && me.value) {
      profile.value = { ...me.value }
    } else {
      const u = await userApi().getById(uid)
      if (u.body?.code !== 0) {
        emit('toast', u.body?.message || '用户不存在')
        profile.value = null
        return
      }
      profile.value = u.body.data
    }

    const [n, fr, fg] = await Promise.all([
      noteApi().listByUser(uid, 1, 50),
      userApi().followers(uid),
      userApi().following(uid),
    ])
    if (n.body?.code === 0) notes.value = n.body.data || []
    else notes.value = []
    if (fr.body?.code === 0) followers.value = fr.body.data || []
    else followers.value = []
    if (fg.body?.code === 0) following.value = fg.body.data || []
    else following.value = []

    if (me.value?.id && !isSelf.value) {
      const mine = await userApi().following(me.value.id)
      const list = mine.body?.code === 0 ? mine.body.data || [] : []
      followedByMe.value = list.some((u) => Number(u.id) === Number(uid))
    } else {
      followedByMe.value = false
    }

    await fillLikeCounts(notes.value)
    if (tab.value === 'likes') await loadLiked()
    if (tab.value === 'collect') await loadCollected()
  } finally {
    loading.value = false
  }
}

async function fillLikeCounts(list) {
  await Promise.all(
    list.slice(0, 40).map(async (item) => {
      const c = await socialApi().likeCount(item.id)
      if (c.body?.code === 0) {
        likes.value = { ...likes.value, [item.id]: countOf(c.body) }
      }
    }),
  )
}

async function resolveNotesByIds(ids) {
  const unique = [...new Set((ids || []).filter(Boolean).map(Number))]
  const out = []
  await Promise.all(
    unique.map(async (id) => {
      const res = await noteApi().detail(id)
      if (res.body?.code !== 0) return
      const data = res.body.data
      const note = data?.note ? { ...data.note, mediaUrls: data.mediaUrls || [] } : data
      if (note?.id) out.push(note)
    }),
  )
  // 保持接口返回顺序
  const map = Object.fromEntries(out.map((n) => [Number(n.id), n]))
  return unique.map((id) => map[id]).filter(Boolean)
}

async function ensureAuthorNames(list) {
  const missing = [...new Set(list.map((n) => n.userId).filter(Boolean))].filter(
    (id) => !authorNames.value[id],
  )
  await Promise.all(
    missing.map(async (id) => {
      const res = await userApi().getById(id)
      if (res.body?.code === 0) {
        const u = res.body.data
        authorNames.value = {
          ...authorNames.value,
          [id]: u.nickname || u.username || `用户${id}`,
        }
        authorAvatars.value = {
          ...authorAvatars.value,
          [id]: u.avatarUrl || '',
        }
      }
    }),
  )
}

async function loadLiked() {
  const uid = targetId.value
  if (!uid) return
  const res = await socialApi().likedOf(uid, 1, 50)
  if (res.body?.code !== 0) {
    emit('toast', res.body?.message || '赞过列表加载失败')
    likedNotes.value = []
    return
  }
  const ids = res.body.data || []
  likedNotes.value = await resolveNotesByIds(ids)
  await ensureAuthorNames(likedNotes.value)
  await fillLikeCounts(likedNotes.value)
}

async function loadCollected() {
  const uid = targetId.value
  if (!uid) return
  const res = await socialApi().collectedOf(uid, 1, 50)
  if (res.body?.code !== 0) {
    emit('toast', res.body?.message || '收藏列表加载失败')
    collectedNotes.value = []
    return
  }
  const ids = res.body.data || []
  collectedNotes.value = await resolveNotesByIds(ids)
  await ensureAuthorNames(collectedNotes.value)
  await fillLikeCounts(collectedNotes.value)
}

async function onTab(next) {
  listTab.value = null
  tab.value = next
  if (next === 'likes') await loadLiked()
  if (next === 'collect') await loadCollected()
}

async function toggleFollow() {
  if (!me.value) return emit('need-login')
  if (isSelf.value) return
  const id = targetId.value
  const res = followedByMe.value
    ? await userApi().unfollow(id)
    : await userApi().follow(id)
  if (res.body?.code === 0) {
    followedByMe.value = !followedByMe.value
    emit('toast', followedByMe.value ? '已关注' : '已取消关注')
    await refresh()
  } else {
    emit('toast', res.body?.message || '操作失败')
  }
}

function openUser(uid) {
  if (!uid) return
  if (Number(uid) === Number(targetId.value)) {
    listTab.value = null
    return
  }
  emit('open-profile', Number(uid))
}

function authorOf(note) {
  if (!note?.userId) return displayName.value
  if (Number(note.userId) === Number(targetId.value)) return displayName.value
  return authorNames.value[note.userId] || `用户${note.userId}`
}

function authorAvatarOf(note) {
  if (!note?.userId) return profile.value?.avatarUrl || ''
  if (Number(note.userId) === Number(targetId.value)) return profile.value?.avatarUrl || ''
  return authorAvatars.value[note.userId] || ''
}

function openEdit() {
  if (!isSelf.value) return
  editForm.value = {
    nickname: profile.value?.nickname || '',
    bio: profile.value?.bio || '',
    avatarUrl: resolveAvatarUrl(profile.value),
  }
  editOpen.value = true
}

async function saveProfile() {
  if (!me.value) return emit('need-login')
  saving.value = true
  try {
    const res = await userApi().updateMe({
      nickname: editForm.value.nickname.trim() || me.value.username,
      bio: editForm.value.bio.trim(),
      avatarUrl: editForm.value.avatarUrl,
    })
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '保存失败')
      return
    }
    profile.value = res.body.data
    emit('profile-updated', res.body.data)
    editOpen.value = false
    emit('toast', '资料已更新')
  } finally {
    saving.value = false
  }
}

const displayName = computed(
  () => profile.value?.nickname || profile.value?.username || `用户${targetId.value}`,
)
</script>

<template>
  <div class="me">
    <template v-if="!me && !profileUserId">
      <div class="guest">
        <div class="brand">FireFly</div>
        <p>登录后查看主页、笔记与关注关系</p>
        <button type="button" class="login" @click="$emit('need-login')">登录 / 注册</button>
      </div>
    </template>

    <template v-else-if="!profile && loading">
      <p class="empty">加载中…</p>
    </template>

    <template v-else-if="!profile">
      <div class="guest">
        <p>找不到该用户</p>
        <button v-if="profileUserId" type="button" class="login" @click="$emit('back')">返回</button>
      </div>
    </template>

    <template v-else>
      <header class="hero">
        <div v-if="!isSelf" class="top-bar">
          <button type="button" class="back" @click="$emit('back')">← 返回</button>
          <span class="top-title">主页</span>
          <span />
        </div>
        <div class="row">
          <UserAvatar :user="profile" :name="displayName" :size="72" />
          <div class="stats">
            <button type="button" @click="listTab = null; tab = 'notes'">
              <b>{{ notes.length }}</b><span>笔记</span>
            </button>
            <button type="button" @click="listTab = 'followers'">
              <b>{{ followers.length }}</b><span>粉丝</span>
            </button>
            <button type="button" @click="listTab = 'following'">
              <b>{{ following.length }}</b><span>关注</span>
            </button>
            <div class="todo"><b>—</b><span>获赞</span></div>
          </div>
        </div>
        <h1>{{ displayName }}</h1>
        <p class="bio">{{ profile.bio || '这个人很懒，还没有简介' }}</p>
        <p class="uid">uid {{ profile.id }} · @{{ profile.username }}</p>
        <div class="actions">
          <template v-if="isSelf">
            <button type="button" class="edit" @click="openEdit">编辑资料</button>
            <button type="button" class="out" @click="$emit('logout')">退出登录</button>
          </template>
          <template v-else>
            <button
              type="button"
              class="follow"
              :class="{ ghost: followedByMe }"
              @click="toggleFollow"
            >
              {{ followedByMe ? '已关注' : '关注' }}
            </button>
          </template>
        </div>
      </header>

      <template v-if="listTab">
        <nav class="sub">
          <button type="button" :class="{ on: listTab === 'followers' }" @click="listTab = 'followers'">
            粉丝
          </button>
          <button type="button" :class="{ on: listTab === 'following' }" @click="listTab = 'following'">
            关注
          </button>
          <button type="button" class="link" @click="listTab = null">看笔记</button>
        </nav>
        <ul class="people">
          <li
            v-for="u in listTab === 'followers' ? followers : following"
            :key="u.id"
          >
            <button type="button" class="person" @click="openUser(u.id)">
              <UserAvatar :user="u" :name="u.nickname || u.username" :size="42" />
              <span class="pinfo">
                <strong>{{ u.nickname || u.username }}</strong>
                <em>@{{ u.username }} · id {{ u.id }}</em>
              </span>
            </button>
          </li>
        </ul>
        <p
          v-if="!(listTab === 'followers' ? followers : following).length"
          class="empty"
        >
          暂无{{ listTab === 'followers' ? '粉丝' : '关注' }}
        </p>
      </template>

      <template v-else>
        <nav class="sub">
          <button type="button" :class="{ on: tab === 'notes' }" @click="onTab('notes')">笔记</button>
          <button type="button" :class="{ on: tab === 'collect' }" @click="onTab('collect')">
            收藏
          </button>
          <button type="button" :class="{ on: tab === 'likes' }" @click="onTab('likes')">
            赞过
          </button>
        </nav>

        <div v-if="tab === 'notes'" class="notes-wrap">
          <div class="xhs-waterfall">
            <NoteCard
              v-for="n in notes"
              :key="n.id"
              :note="n"
              :author-name="displayName"
              :author-avatar="profile?.avatarUrl || ''"
              :like-count="likes[n.id] || 0"
              @open="(note) => $emit('open-note', note.id)"
              @open-profile="(uid) => $emit('open-profile', uid)"
            />
          </div>
          <p v-if="!notes.length" class="empty">
            {{ isSelf ? '还没有笔记，去发布一页吧' : '对方还没有公开笔记' }}
          </p>
        </div>

        <div v-else-if="tab === 'collect'" class="notes-wrap">
          <div class="xhs-waterfall">
            <NoteCard
              v-for="n in collectedNotes"
              :key="'c-' + n.id"
              :note="n"
              :author-name="authorOf(n)"
              :author-avatar="authorAvatarOf(n)"
              :like-count="likes[n.id] || 0"
              @open="(note) => $emit('open-note', note.id)"
              @open-profile="(uid) => $emit('open-profile', uid)"
            />
          </div>
          <p v-if="!collectedNotes.length" class="empty">
            {{ isSelf ? '还没有收藏，去笔记详情点☆吧' : '对方还没有收藏' }}
          </p>
        </div>

        <div v-else class="notes-wrap">
          <div class="xhs-waterfall">
            <NoteCard
              v-for="n in likedNotes"
              :key="'l-' + n.id"
              :note="n"
              :author-name="authorOf(n)"
              :author-avatar="authorAvatarOf(n)"
              :like-count="likes[n.id] || 0"
              @open="(note) => $emit('open-note', note.id)"
              @open-profile="(uid) => $emit('open-profile', uid)"
            />
          </div>
          <p v-if="!likedNotes.length" class="empty">
            {{ isSelf ? '还没有赞过，去笔记详情点♡吧' : '对方还没有赞过' }}
          </p>
        </div>
      </template>
    </template>

    <div v-if="editOpen" class="mask" @click.self="editOpen = false">
      <div class="sheet">
        <header class="sheet-hd">
          <strong>编辑资料</strong>
          <button type="button" class="x" @click="editOpen = false">×</button>
        </header>
        <div class="sheet-bd">
          <p class="label">选择默认头像</p>
          <div class="avatar-grid">
            <button
              v-for="a in DEFAULT_AVATARS"
              :key="a.id"
              type="button"
              class="pick"
              :class="{ on: editForm.avatarUrl === a.url }"
              :title="a.label"
              @click="editForm.avatarUrl = a.url"
            >
              <img :src="a.url" :alt="a.label" />
            </button>
          </div>
          <label class="field">
            <span>昵称</span>
            <input v-model="editForm.nickname" maxlength="32" placeholder="怎么称呼你" />
          </label>
          <label class="field">
            <span>简介</span>
            <textarea v-model="editForm.bio" rows="3" maxlength="120" placeholder="介绍一下自己" />
          </label>
        </div>
        <footer class="sheet-ft">
          <button type="button" class="ghost" @click="editOpen = false">取消</button>
          <button type="button" class="save" :disabled="saving" @click="saveProfile">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.me {
  min-height: 100%;
  background: var(--bg);
}

.guest {
  padding: 4rem 1.5rem;
  text-align: center;
  background: #fff;
  min-height: 60vh;
  border-radius: 12px;
}

@media (min-width: 769px) {
  .guest, .hero, .sub {
    max-width: 960px;
    margin-left: auto;
    margin-right: auto;
  }
  .hero {
    margin-top: 16px;
    border-radius: 12px 12px 0 0;
    border: 1px solid var(--line);
    border-bottom: none;
  }
  .sub {
    border-left: 1px solid var(--line);
    border-right: 1px solid var(--line);
    border-radius: 0 0 12px 12px;
    border-bottom: 1px solid var(--line);
    margin-bottom: 8px;
  }
  .notes-wrap, .people { max-width: 1440px; margin: 0 auto; }
}

.brand {
  font-family: 'ZCOOL XiaoWei', serif;
  font-size: 2.4rem;
  color: var(--xhs-red);
  margin-bottom: 0.8rem;
}

.login {
  margin-top: 1rem;
  padding: 0.7rem 1.6rem;
  border-radius: 22px;
  background: var(--xhs-red);
  color: #fff;
  font-weight: 600;
}

.hero {
  background: #fff;
  padding: 1rem 1rem 0.75rem;
}

.top-bar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  margin-bottom: 0.6rem;
}

.back, .link {
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--ink);
  font-size: 0.88rem;
  justify-self: start;
}

.top-title {
  font-weight: 600;
  font-size: 0.92rem;
}

.row {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.av {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(145deg, #ff8a9a, var(--brand));
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 1.6rem;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
}

.av img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stats {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  text-align: center;
  gap: 0.2rem;
}

.stats button {
  border: none;
  background: transparent;
  cursor: pointer;
  color: inherit;
  padding: 0;
}

.stats b { display: block; font-size: 1rem; }
.stats span { font-size: 0.68rem; color: var(--ink-3); }

h1 {
  margin: 0.85rem 0 0.25rem;
  font-size: 1.15rem;
}

.bio { margin: 0; color: var(--ink-2); font-size: 0.86rem; }
.uid { margin: 0.35rem 0 0; color: var(--ink-3); font-size: 0.75rem; }

.actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.8rem;
}

.edit, .out, .follow {
  flex: 1;
  padding: 0.5rem;
  border-radius: 16px;
  background: #f5f5f5;
  font-size: 0.84rem;
  font-weight: 600;
  border: none;
  cursor: pointer;
}

.edit {
  background: var(--xhs-red);
  color: #fff;
}

.out { color: var(--ink-2); }

.follow {
  background: var(--xhs-red);
  color: #fff;
}

.follow.ghost {
  background: #f5f5f5;
  color: var(--ink-2);
}

.sub {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 1.4rem;
  padding: 0.7rem;
  background: #fff;
  margin-top: 8px;
  border-bottom: 1px solid var(--line);
}

.sub button {
  border: none;
  background: transparent;
  color: var(--ink-3);
  font-size: 0.9rem;
  cursor: pointer;
}
.sub button.on {
  color: var(--ink);
  font-weight: 700;
}
.sub .link { color: var(--xhs-red); font-size: 0.82rem; }

.people {
  list-style: none;
  margin: 0;
  padding: 0.4rem 0 1rem;
  background: #fff;
}

.person {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.person:active { background: #fafafa; }

.pav {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #f0f0f0;
  display: grid;
  place-items: center;
  font-weight: 700;
  color: var(--ink-2);
  flex-shrink: 0;
}

.pinfo {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.pinfo strong {
  font-size: 0.9rem;
}

.pinfo em {
  font-style: normal;
  font-size: 0.75rem;
  color: var(--ink-3);
}

.empty {
  text-align: center;
  color: var(--ink-3);
  padding: 2.5rem 1rem;
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.sheet {
  width: min(520px, 100%);
  max-height: min(88vh, 720px);
  background: #fff;
  border-radius: 16px 16px 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

@media (min-width: 769px) {
  .mask { align-items: center; }
  .sheet {
    border-radius: 16px;
    max-height: 80vh;
  }
}

.sheet-hd, .sheet-ft {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--line);
}

.sheet-ft {
  border-bottom: none;
  border-top: 1px solid var(--line);
}

.sheet-hd .x {
  border: none;
  background: transparent;
  font-size: 1.4rem;
  line-height: 1;
  cursor: pointer;
  color: var(--ink-3);
}

.sheet-bd {
  padding: 0.85rem 1rem 1rem;
  overflow: auto;
}

.label {
  margin: 0 0 0.55rem;
  font-size: 0.82rem;
  color: var(--ink-2);
}

.avatar-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.55rem;
  margin-bottom: 1rem;
}

.pick {
  aspect-ratio: 1;
  border: 2px solid transparent;
  border-radius: 50%;
  padding: 0;
  overflow: hidden;
  background: #f5f5f5;
  cursor: pointer;
}

.pick.on {
  border-color: var(--xhs-red);
  box-shadow: 0 0 0 2px rgba(255, 36, 66, 0.15);
}

.pick img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-bottom: 0.75rem;
  font-size: 0.82rem;
  color: var(--ink-2);
}

.field input, .field textarea {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 0.55rem 0.7rem;
  background: #fafafa;
  color: var(--ink);
  resize: vertical;
}

.sheet-ft .ghost, .sheet-ft .save {
  flex: 1;
  padding: 0.6rem;
  border: none;
  border-radius: 16px;
  font-weight: 600;
  cursor: pointer;
}

.sheet-ft .ghost {
  background: #f5f5f5;
  color: var(--ink-2);
}

.sheet-ft .save {
  background: var(--xhs-red);
  color: #fff;
}

.sheet-ft .save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
