<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import NoteCard from '../components/NoteCard.vue'
import TodoBadge from '../components/TodoBadge.vue'
import { noteApi, socialApi, userApi } from '../api'

const props = defineProps({
  session: { type: Object, default: null },
})
const emit = defineEmits(['need-login', 'logout', 'open-note', 'toast'])

const user = computed(() => props.session?.user || null)
const notes = ref([])
const likes = ref({})
const followers = ref([])
const following = ref([])
const followTarget = ref('')
const tab = ref('notes')

watch(
  () => props.session?.user?.id,
  () => {
    if (user.value) refresh()
    else {
      notes.value = []
      followers.value = []
      following.value = []
    }
  },
)

onMounted(() => {
  if (user.value) refresh()
})

async function refresh() {
  if (!user.value) return
  const uid = user.value.id
  const [n, fr, fg] = await Promise.all([
    noteApi().listByUser(uid, 1, 50),
    userApi().followers(uid),
    userApi().following(uid),
  ])
  if (n.body?.code === 0) notes.value = n.body.data || []
  if (fr.body?.code === 0) followers.value = fr.body.data || []
  if (fg.body?.code === 0) following.value = fg.body.data || []
  await Promise.all(
    notes.value.slice(0, 30).map(async (item) => {
      const c = await socialApi().likeCount(item.id)
      if (c.body?.code === 0) likes.value = { ...likes.value, [item.id]: c.body.data ?? 0 }
    }),
  )
}

async function doFollow() {
  if (!user.value) return emit('need-login')
  const id = Number(followTarget.value)
  if (!id) return emit('toast', '填写对方用户 id')
  const res = await userApi().follow(id)
  emit('toast', res.body?.message || (res.body?.code === 0 ? '已关注' : '失败'))
  if (res.body?.code === 0) {
    followTarget.value = ''
    await refresh()
  }
}

async function doUnfollow() {
  if (!user.value) return emit('need-login')
  const id = Number(followTarget.value)
  if (!id) return emit('toast', '填写对方用户 id')
  const res = await userApi().unfollow(id)
  emit('toast', res.body?.message || (res.body?.code === 0 ? '已取消' : '失败'))
  if (res.body?.code === 0) await refresh()
}
</script>

<template>
  <div class="me">
    <template v-if="!user">
      <div class="guest">
        <div class="brand">FireFly</div>
        <p>登录后查看主页、笔记与关注关系</p>
        <button type="button" class="login" @click="$emit('need-login')">登录 / 注册</button>
      </div>
    </template>

    <template v-else>
      <header class="hero">
        <div class="row">
          <div class="av">{{ (user.nickname || user.username || 'U').slice(0, 1) }}</div>
          <div class="stats">
            <div><b>{{ notes.length }}</b><span>笔记</span></div>
            <div><b>{{ followers.length }}</b><span>粉丝</span></div>
            <div><b>{{ following.length }}</b><span>关注</span></div>
            <div class="todo"><b>—</b><span>获赞 <TodoBadge /></span></div>
          </div>
        </div>
        <h1>{{ user.nickname || user.username }}</h1>
        <p class="bio">{{ user.bio || '这个人很懒，还没有简介' }}</p>
        <p class="uid">小红书号式展示 · uid {{ user.id }} · @{{ user.username }}</p>
        <div class="actions">
          <button type="button" class="edit" disabled>编辑资料 <TodoBadge /></button>
          <button type="button" class="out" @click="$emit('logout')">退出登录</button>
        </div>
      </header>

      <section class="follow-box">
        <input v-model="followTarget" placeholder="关注用户 id" />
        <button type="button" @click="doFollow">关注</button>
        <button type="button" class="ghost" @click="doUnfollow">取关</button>
      </section>

      <nav class="sub">
        <button type="button" :class="{ on: tab === 'notes' }" @click="tab = 'notes'">笔记</button>
        <button type="button" :class="{ on: tab === 'collect' }" @click="tab = 'collect'">
          收藏 <TodoBadge />
        </button>
        <button type="button" :class="{ on: tab === 'likes' }" @click="tab = 'likes'">
          赞过 <TodoBadge />
        </button>
      </nav>

      <div v-if="tab === 'notes'" class="notes-wrap">
        <div class="xhs-waterfall">
          <NoteCard
            v-for="n in notes"
            :key="n.id"
            :note="n"
            :author-name="user.nickname || user.username"
            :like-count="likes[n.id] || 0"
            @open="(note) => $emit('open-note', note.id)"
          />
        </div>
        <p v-if="!notes.length" class="empty">还没有笔记，去发布一页吧</p>
      </div>
      <div v-else class="empty">
        <TodoBadge :text="tab === 'collect' ? '收藏列表待实现' : '赞过列表待实现'" />
      </div>
    </template>
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
  .guest, .hero, .follow-box, .sub {
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
  .follow-box, .sub {
    border-left: 1px solid var(--line);
    border-right: 1px solid var(--line);
  }
  .sub {
    border-radius: 0 0 12px 12px;
    border-bottom: 1px solid var(--line);
    margin-bottom: 8px;
  }
  .notes-wrap { max-width: 1440px; margin: 0 auto; }
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
}

.stats {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  text-align: center;
  gap: 0.2rem;
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

.edit, .out {
  flex: 1;
  padding: 0.5rem;
  border-radius: 16px;
  background: #f5f5f5;
  font-size: 0.84rem;
  font-weight: 600;
}

.out { color: var(--ink-2); }

.follow-box {
  display: flex;
  gap: 0.4rem;
  padding: 0.65rem 0.85rem;
  background: #fff;
  border-top: 1px solid var(--line);
}

.follow-box input {
  flex: 1;
  background: #f5f5f5;
  border: none;
  border-radius: 14px;
}

.follow-box button {
  padding: 0.45rem 0.7rem;
  border-radius: 14px;
  background: var(--xhs-red);
  color: #fff;
  font-size: 0.8rem;
  font-weight: 600;
}

.follow-box .ghost {
  background: #f5f5f5;
  color: var(--ink-2);
}

.sub {
  display: flex;
  justify-content: center;
  gap: 1.4rem;
  padding: 0.7rem;
  background: #fff;
  margin-top: 8px;
  border-bottom: 1px solid var(--line);
}

.sub button {
  color: var(--ink-3);
  font-size: 0.9rem;
}
.sub button.on {
  color: var(--ink);
  font-weight: 700;
}

.empty {
  text-align: center;
  color: var(--ink-3);
  padding: 2.5rem 1rem;
}
</style>
