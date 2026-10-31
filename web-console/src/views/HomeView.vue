<script setup>
import { onMounted, ref, watch } from 'vue'
import NoteCard from '../components/NoteCard.vue'
import { feedApi, socialApi, userApi } from '../api'

const props = defineProps({
  userId: { type: Number, default: null },
  loggedIn: Boolean,
})
const emit = defineEmits(['open-note', 'need-login', 'search', 'open-profile'])

/** 南京新街口：定位失败 / 拒绝时的演示默认点 */
const NANJING_XINJIEKOU = { lat: 32.041544, lng: 118.784485, label: '南京·新街口' }

const channel = ref('discover')
const loading = ref(false)
const notes = ref([])
const likes = ref({})
const names = ref({})
const avatars = ref({})
const nearbyMeta = ref({ label: '', usingFallback: false })

watch(channel, () => loadFeed())
onMounted(() => loadFeed())
watch(
  () => [props.userId, props.loggedIn],
  () => {
    if (channel.value !== 'nearby') loadFeed()
  },
)

function mapFeedItems(items) {
  return (items || []).map((it) => ({
    id: it.noteId ?? it.id,
    userId: it.userId,
    title: it.title,
    coverUrl: it.coverUrl,
    mediaCount: it.mediaCount,
    mediaKind: it.mediaKind,
    likeCount: it.likeCount,
    createdAt: it.createdAt,
    distanceKm: it.distanceKm,
    placeName: it.placeName,
  }))
}

async function enrichNotes(list) {
  if (!list?.length) return
  try {
    await ensureNames(list.map((n) => n.userId))
  } catch {
    /* ignore */
  }
  try {
    await loadLikes(list)
  } catch {
    /* ignore */
  }
}

function resolvePosition() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve({ ...NANJING_XINJIEKOU, usingFallback: true })
      return
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        resolve({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
          label: '当前位置',
          usingFallback: false,
        })
      },
      () => {
        resolve({ ...NANJING_XINJIEKOU, usingFallback: true })
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 },
    )
  })
}

async function loadNearby() {
  const pos = await resolvePosition()
  nearbyMeta.value = {
    label: pos.label || NANJING_XINJIEKOU.label,
    usingFallback: !!pos.usingFallback,
  }
  const res = await feedApi().nearby(pos.lat, pos.lng, 8, 40)
  if (res.body?.code !== 0) {
    notes.value = []
    return
  }
  notes.value = mapFeedItems(res.body.data || [])
  void enrichNotes(notes.value)
}

async function loadDiscover() {
  const res = await feedApi().discover(40)
  if (res.body?.code !== 0) {
    notes.value = []
    return
  }
  notes.value = mapFeedItems(res.body.data || [])
  void enrichNotes(notes.value)
}

async function loadFeed() {
  loading.value = true
  notes.value = []
  try {
    if (channel.value === 'nearby') {
      await loadNearby()
      return
    }

    if (channel.value === 'discover') {
      await loadDiscover()
      return
    }

    if (!props.loggedIn || !props.userId) {
      notes.value = []
      return
    }

    if (channel.value === 'follow') {
      const res = await feedApi().following(40)
      if (res.body?.code !== 0) {
        notes.value = []
        return
      }
      notes.value = mapFeedItems(res.body.data || [])
      void enrichNotes(notes.value)
    }
  } finally {
    loading.value = false
  }
}

async function ensureNames(userIds) {
  const missing = [...new Set(userIds.filter(Boolean))].filter((id) => !names.value[id])
  await Promise.all(
    missing.map(async (uid) => {
      const u = await userApi().getById(uid)
      if (u.body?.code === 0) {
        const user = u.body.data
        names.value = {
          ...names.value,
          [uid]: user.nickname || user.username || '',
        }
        avatars.value = {
          ...avatars.value,
          [uid]: user.avatarUrl || '',
        }
      }
    }),
  )
}

async function loadLikes(list) {
  const countOf = (body) => {
    const d = body?.data
    if (typeof d === 'number') return d
    if (d && typeof d.count === 'number') return d.count
    return 0
  }
  await Promise.all(
    list.slice(0, 24).map(async (n) => {
      const c = await socialApi().likeCount(n.id)
      if (c.body?.code === 0) {
        likes.value = { ...likes.value, [n.id]: countOf(c.body) }
      }
    }),
  )
}

function openNote(note) {
  emit('open-note', note.id)
}

function goFollowLogin() {
  emit('need-login')
}
</script>

<template>
  <div class="home">
    <header class="top">
      <div class="channels">
        <button type="button" :class="{ on: channel === 'follow' }" @click="channel = 'follow'">关注</button>
        <button type="button" :class="{ on: channel === 'discover' }" @click="channel = 'discover'">发现</button>
        <button type="button" :class="{ on: channel === 'nearby' }" @click="channel = 'nearby'">
          附近
        </button>
      </div>
      <button type="button" class="search mobile-search" @click="$emit('search')" aria-label="搜索">
        <svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M10.5 3a7.5 7.5 0 1 1 0 15 7.5 7.5 0 0 1 0-15zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11zm7.2 11.1 3.5 3.5-1.4 1.4-3.5-3.5 1.4-1.4z"/></svg>
      </button>
    </header>

    <p v-if="channel === 'nearby' && !loading" class="nearby-bar">
      <template v-if="nearbyMeta.usingFallback">
        已按附近默认位置展示
      </template>
      <template v-else>
        附近 8km · {{ nearbyMeta.label || '当前位置' }}
      </template>
    </p>

    <div v-if="loading" class="skel" aria-busy="true">
      <div v-for="i in 4" :key="i" class="skel-card" />
    </div>

    <div v-else-if="notes.length" class="xhs-waterfall">
      <NoteCard
        v-for="n in notes"
        :key="n.id"
        :note="n"
        :author-name="names[n.userId]"
        :author-avatar="avatars[n.userId]"
        :like-count="likes[n.id] || 0"
        :distance-km="channel === 'nearby' ? n.distanceKm : null"
        @open="openNote"
        @open-profile="(uid) => $emit('open-profile', uid)"
      />
    </div>

    <div v-else class="empty">
      <template v-if="channel === 'nearby'">
        <p class="empty-title">附近还没有笔记</p>
        <p>发布时带上地点，会出现在附近</p>
        <button type="button" class="empty-btn" @click="loadFeed">重新定位</button>
      </template>
      <template v-else-if="channel === 'follow'">
        <template v-if="!loggedIn">
          <p class="empty-title">登录后看关注动态</p>
          <p>关注感兴趣的人，他们的新笔记会出现在这里</p>
          <button type="button" class="empty-btn" @click="goFollowLogin">去登录</button>
        </template>
        <template v-else>
          <p class="empty-title">关注流还是空的</p>
          <p>去搜索页找找感兴趣的作者，关注后再回来看</p>
          <button type="button" class="empty-btn" @click="$emit('search')">去搜索</button>
        </template>
      </template>
      <template v-else>
        <p class="empty-title">暂时没有内容</p>
        <p>稍后再试，或去搜索看看热门关键词</p>
        <button type="button" class="empty-btn" @click="$emit('search')">去搜索</button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.home {
  min-height: 100%;
  background: var(--bg);
}

.top {
  position: sticky;
  top: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 0.7rem 0.75rem 0.45rem;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
}

.channels {
  display: flex;
  gap: 1.4rem;
  align-items: center;
}

.channels button {
  position: relative;
  font-size: 0.95rem;
  color: var(--ink-3);
  font-weight: 500;
  padding: 0.35rem 0;
  transition:
    color var(--dur-fast) ease,
    font-size var(--dur) var(--ease-out),
    transform var(--dur-fast) var(--ease-out);
}

.channels button.on {
  color: var(--ink);
  font-weight: 700;
  font-size: 1.05rem;
}

.channels button::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 28px;
  height: 3px;
  border-radius: 2px;
  background: var(--xhs-red);
  transform: translateX(-50%) scaleX(0);
  transform-origin: center;
  transition: transform var(--dur) var(--ease-out);
}

.channels button.on::after {
  transform: translateX(-50%) scaleX(1);
}

.mobile-search {
  position: absolute;
  right: 0.75rem;
  color: var(--ink);
  padding: 0.3rem;
}

.nearby-bar {
  margin: 0;
  padding: 0.35rem 0.85rem 0;
  font-size: 0.78rem;
  color: var(--ink-2);
}

.nearby-bar strong {
  color: var(--ink);
  font-weight: 650;
}

.skel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.55rem;
  padding: 0.75rem 0.7rem 1.5rem;
}

.skel-card {
  aspect-ratio: 3 / 4;
  border-radius: 10px;
  background: linear-gradient(110deg, #f2f2f2 30%, #fafafa 45%, #f2f2f2 60%);
  background-size: 200% 100%;
  animation: ff-skel 1.1s ease infinite;
}

@keyframes ff-skel {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}

.empty {
  display: grid;
  place-items: center;
  gap: 0.45rem;
  padding: 4rem 1.25rem;
  color: var(--ink-2);
  text-align: center;
}

.empty-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 650;
  color: var(--ink);
}

.empty p {
  margin: 0;
  font-size: 0.86rem;
  line-height: 1.5;
  max-width: 280px;
}

.empty-btn {
  margin-top: 0.65rem;
  padding: 0.45rem 1.15rem;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-size: 0.86rem;
  font-weight: 600;
}

@media (min-width: 769px) {
  .top {
    top: 0;
    justify-content: flex-start;
    padding: 1rem 0 0.5rem;
    background: transparent;
    backdrop-filter: none;
  }
  .mobile-search { display: none; }
  .channels { gap: 1.8rem; }
  .channels button { font-size: 1rem; }
  .channels button.on { font-size: 1.15rem; }
  .nearby-bar { padding-left: 0; }
}
</style>
