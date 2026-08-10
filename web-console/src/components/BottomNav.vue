<script setup>
defineProps({
  active: { type: String, required: true },
  unread: { type: Number, default: 0 },
})
defineEmits(['change'])

const items = [
  { id: 'home', label: '首页' },
  { id: 'market', label: '市集', todo: true },
  { id: 'publish', label: '发布', center: true },
  { id: 'msg', label: '消息' },
  { id: 'me', label: '我' },
]
</script>

<template>
  <nav class="nav">
    <button
      v-for="item in items"
      :key="item.id"
      type="button"
      class="item"
      :class="{ active: active === item.id, center: item.center }"
      @click="$emit('change', item.id)"
    >
      <template v-if="item.center">
        <span class="plus">+</span>
      </template>
      <template v-else>
        <span class="icon" aria-hidden="true">
          <svg v-if="item.id === 'home'" viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M12 3.2 3 10.5V21h6.2v-6.2h5.6V21H21V10.5L12 3.2z"/></svg>
          <svg v-else-if="item.id === 'market'" viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M4 7h16l-1.2 12.2a2 2 0 0 1-2 1.8H7.2a2 2 0 0 1-2-1.8L4 7zm4.5-3h7l1 3H7.5l1-3z"/></svg>
          <svg v-else-if="item.id === 'msg'" viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M4 5h16a1 1 0 0 1 1 1v11.2a1 1 0 0 1-1.5.86L14.2 15H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"/></svg>
          <svg v-else viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M12 12a4.2 4.2 0 1 0-4.2-4.2A4.2 4.2 0 0 0 12 12zm0 2.2c-3.7 0-7 1.9-7 4.2V20h14v-1.6c0-2.3-3.3-4.2-7-4.2z"/></svg>
        </span>
        <span class="label">
          {{ item.label }}
          <i v-if="item.todo" class="dot-todo" title="待实现" />
          <i v-if="item.id === 'msg' && unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</i>
        </span>
      </template>
    </button>
  </nav>
</template>

<style scoped>
.nav {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 25;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  align-items: end;
  padding: 0.35rem 0.2rem calc(0.35rem + var(--safe-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid var(--line);
  backdrop-filter: blur(8px);
}

.item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  color: var(--ink-3);
  min-height: 48px;
  justify-content: center;
}

.item.active { color: var(--ink); font-weight: 600; }

.label {
  position: relative;
  font-size: 0.68rem;
}

.dot-todo {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-left: 2px;
  border-radius: 50%;
  background: #f59e0b;
  vertical-align: super;
}

.badge {
  position: absolute;
  top: -10px;
  right: -14px;
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

.center { transform: translateY(-8px); }

.plus {
  width: 44px;
  height: 36px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ff5a6e, var(--brand));
  color: #fff;
  font-size: 1.6rem;
  font-weight: 500;
  line-height: 1;
  display: grid;
  place-items: center;
  box-shadow: 0 6px 14px rgba(255, 36, 66, 0.28);
}
</style>
