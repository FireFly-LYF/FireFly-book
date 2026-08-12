<script setup>
import { onMounted, ref, watch } from 'vue'
import NoteCard from '../components/NoteCard.vue'
import { feedApi, noteApi, socialApi, userApi } from '../api'

const props = defineProps({
  userId: { type: Number, default: null },
  loggedIn: Boolean,
})
const emit = defineEmits(['open-note', 'need-login', 'search', 'open-profile'])

const channel = ref('discover')
const loading = ref(false)
const notes = ref([])
const likes = ref({})
const names = ref({})
const avatars = ref({})
const tip = ref('')

const DEMO_NOTES = [
  {
    id: 'demo-1',
    demo: true,
    userId: 0,
    title: '发现流接入 feed-service',
    content: '全局推荐/发现流后端尚未实现；登录后可看关注流',
    coverUrl: '',
  },
  {
    id: 'demo-2',
    demo: true,
    userId: 0,
    title: '搜索笔记与用户',
    content: '点顶栏搜索，试「穿搭」「美食」「咖啡」',
    coverUrl: '',
  },
  {
    id: 'demo-3',
    demo: true,
    userId: 0,
    title: '附近的人',
    content: 'LBS 附近笔记待实现',
    coverUrl: '',
  },
]

watch(channel, () => loadFeed())
onMounted(() => loadFeed())
watch(
  () => props.userId,
  () => loadFeed(),
)

async function loadFeed() {
  if (channel.value === 'nearby') {
    notes.value = []
    tip.value = '附近频道依赖 LBS，后端待实现'
    return
  }

  loading.value = true
  tip.value = ''
  try {
    if (!props.loggedIn || !props.userId) {
      notes.value = DEMO_NOTES
      tip.value = '登录后可聚合「我的 + 关注」笔记；全局发现流待 feed-service'
      return
    }

    if (channel.value === 'follow') {
      // 读扩散：经 Gateway → feed-service → user following-ids + content 笔记
      const res = await feedApi().following(40)
      if (res.body?.code !== 0) {
        notes.value = []
        tip.value = res.body?.message || '关注流加载失败（请确认 feed-service :9006 已启动）'
        return
      }
      const items = res.body.data || []
      notes.value = items.map((it) => ({
        id: it.noteId,
        userId: it.userId,
        title: it.title,
        coverUrl: it.coverUrl,
        createdAt: it.createdAt,
      }))
      await ensureNames(notes.value.map((n) => n.userId))
      await loadLikes(notes.value)
      tip.value = items.length
        ? '关注流：feed-service 读扩散（关注作者笔记，按时间倒序）'
        : '还没有关注的人或对方暂无笔记，去个人页关注后再刷新'
      return
    }

    // 发现：我的 + 关注，凑瀑布流；无数据则示例卡
    const fg = await userApi().following(props.userId)
    const users = fg.body?.code === 0 ? fg.body.data || [] : []
    const ids = [props.userId, ...users.map((u) => u.id)].filter(Boolean)
    await collectNotes(ids)
    if (!notes.value.length) {
      notes.value = DEMO_NOTES
      tip.value = '暂无真实笔记，展示示例卡。发布笔记后会出现在这里'
    } else {
      tip.value = '发现页暂用「我的+关注」聚合；关注频道已接 feed-service 读扩散'
    }
  } finally {
    loading.value = false
  }
}

async function collectNotes(userIds) {
  const unique = [...new Set(userIds)]
  const chunks = await Promise.all(
    unique.map(async (uid) => {
      const res = await noteApi().listByUser(uid, 1, 20)
      if (res.body?.code !== 0) return []
      return res.body.data || []
    }),
  )
  const merged = chunks.flat().sort((a, b) => (b.id || 0) - (a.id || 0))
  notes.value = merged
  await ensureNames(unique)
  await loadLikes(merged)
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
          [uid]: user.nickname || user.username || `用户${uid}`,
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
  if (note.demo) return
  emit('open-note', note.id)
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

    <p v-if="tip" class="tip">{{ tip }}</p>
    <p v-if="loading" class="tip">加载中…</p>

    <div v-if="channel === 'nearby'" class="empty">
      <p>附近笔记即将上线</p>
    </div>

    <div v-else class="xhs-waterfall">
      <NoteCard
        v-for="n in notes"
        :key="n.id"
        :note="n"
        :demo="!!n.demo"
        :author-name="n.demo ? 'FireFly' : names[n.userId]"
        :author-avatar="n.demo ? '/avatars/ff-01.svg' : avatars[n.userId]"
        :like-count="likes[n.id] || 0"
        @open="openNote"
        @open-profile="(uid) => $emit('open-profile', uid)"
      />
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
}

.channels button.on {
  color: var(--ink);
  font-weight: 700;
  font-size: 1.05rem;
}

.channels button.on::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 28px;
  height: 3px;
  border-radius: 2px;
  background: var(--xhs-red);
  transform: translateX(-50%);
}

.mobile-search {
  position: absolute;
  right: 0.75rem;
  color: var(--ink);
  padding: 0.3rem;
}

.tip {
  margin: 0;
  padding: 0.55rem 0.25rem 0.1rem;
  font-size: 0.75rem;
  color: var(--ink-3);
}

.empty {
  display: grid;
  place-items: center;
  gap: 0.6rem;
  padding: 4rem 1rem;
  color: var(--ink-2);
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
}
</style>
