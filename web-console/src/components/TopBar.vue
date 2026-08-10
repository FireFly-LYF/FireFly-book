<script setup>
import { computed } from 'vue'
import TodoBadge from './TodoBadge.vue'

const props = defineProps({
  active: { type: String, required: true },
  unread: { type: Number, default: 0 },
  loggedIn: Boolean,
  displayName: { type: String, default: '' },
})

defineEmits(['change', 'search', 'login', 'logout'])

const links = [
  { id: 'home', label: '发现' },
  { id: 'market', label: '市集', todo: true },
  { id: 'publish', label: '发布' },
  { id: 'msg', label: '消息' },
  { id: 'me', label: '我' },
]

const initial = computed(() => (props.displayName || 'U').slice(0, 1))
</script>

<template>
  <header class="topbar">
    <div class="inner">
      <button type="button" class="brand" @click="$emit('change', 'home')">FireFly</button>

      <nav class="links">
        <button
          v-for="item in links"
          :key="item.id"
          type="button"
          class="link"
          :class="{ on: active === item.id }"
          @click="$emit('change', item.id)"
        >
          {{ item.label }}
          <TodoBadge v-if="item.todo" />
          <i v-if="item.id === 'msg' && unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</i>
        </button>
      </nav>

      <button type="button" class="search" @click="$emit('search')">
        <svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M10.5 3a7.5 7.5 0 1 1 0 15 7.5 7.5 0 0 1 0-15zm0 2a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11zm7.2 11.1 3.5 3.5-1.4 1.4-3.5-3.5 1.4-1.4z"/></svg>
        <span>搜索笔记、用户</span>
        <TodoBadge />
      </button>

      <div class="right">
        <button type="button" class="pub" @click="$emit('change', 'publish')">发布笔记</button>
        <template v-if="loggedIn">
          <button type="button" class="user" @click="$emit('change', 'me')" :title="displayName">
            <span class="av">{{ initial }}</span>
            <span class="name">{{ displayName }}</span>
          </button>
          <button type="button" class="out" @click="$emit('logout')">退出</button>
        </template>
        <button v-else type="button" class="login" @click="$emit('login')">登录</button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid var(--line);
  backdrop-filter: blur(10px);
}

.inner {
  max-width: 1440px;
  margin: 0 auto;
  min-height: 64px;
  padding: 0 1.25rem;
  display: flex;
  align-items: center;
  gap: 1.25rem;
}

.brand {
  font-family: 'ZCOOL XiaoWei', serif;
  font-size: 1.55rem;
  color: var(--xhs-red);
  flex-shrink: 0;
}

.links {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.45rem 0.7rem;
  border-radius: 8px;
  color: var(--ink-2);
  font-weight: 500;
  font-size: 0.92rem;
}

.link:hover { background: #f7f7f7; color: var(--ink); }
.link.on {
  color: var(--ink);
  font-weight: 700;
}

.badge {
  position: absolute;
  top: 2px;
  right: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--xhs-red);
  color: #fff;
  font-size: 0.6rem;
  font-style: normal;
  display: grid;
  place-items: center;
}

.search {
  flex: 1;
  max-width: 420px;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-left: auto;
  padding: 0.55rem 0.9rem;
  border-radius: 22px;
  background: #f5f5f5;
  color: var(--ink-3);
  font-size: 0.86rem;
}

.search:hover { background: #efefef; }

.right {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  flex-shrink: 0;
}

.pub {
  padding: 0.45rem 0.95rem;
  border-radius: 18px;
  background: var(--xhs-red);
  color: #fff;
  font-weight: 600;
  font-size: 0.86rem;
}

.login {
  padding: 0.45rem 0.95rem;
  border-radius: 18px;
  border: 1px solid var(--xhs-red);
  color: var(--xhs-red);
  font-weight: 600;
  font-size: 0.86rem;
}

.user {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.2rem 0.45rem 0.2rem 0.2rem;
  border-radius: 20px;
}

.user:hover { background: #f7f7f7; }

.av {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(145deg, #ff8a9a, var(--brand));
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 0.85rem;
  font-weight: 700;
}

.name {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.86rem;
}

.out {
  color: var(--ink-3);
  font-size: 0.82rem;
  padding: 0.35rem;
}

@media (max-width: 1100px) {
  .search span, .name, .out { display: none; }
  .search { max-width: 44px; padding: 0.55rem; justify-content: center; }
  .inner { gap: 0.7rem; }
}
</style>
