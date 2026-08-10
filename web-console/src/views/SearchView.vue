<script setup>
import TodoBadge from '../components/TodoBadge.vue'

defineEmits(['back'])

const hot = ['穿搭', '美食探店', '旅行攻略', '数码测评', '家居改造']
</script>

<template>
  <div class="search">
    <header>
      <button type="button" @click="$emit('back')">←</button>
      <input placeholder="搜索笔记、用户" disabled />
      <TodoBadge />
    </header>
    <section>
      <h3>历史搜索 <TodoBadge /></h3>
      <p class="muted">本地历史与 search-service 待接入</p>
    </section>
    <section>
      <h3>热门搜索 <TodoBadge text="热榜待实现" /></h3>
      <div class="tags">
        <span v-for="t in hot" :key="t">{{ t }}</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.search {
  min-height: 100%;
  background: #fff;
}

@media (min-width: 769px) {
  .search {
    max-width: 720px;
    margin: 16px auto 0;
    border: 1px solid var(--line);
    border-radius: 12px;
    min-height: 60vh;
  }
}

header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.7rem 0.85rem;
  border-bottom: 1px solid var(--line);
}

header input {
  flex: 1;
  border: none;
  background: #f5f5f5;
  border-radius: 16px;
}

section { padding: 1rem; }

h3 {
  margin: 0 0 0.6rem;
  font-size: 0.92rem;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.muted { color: var(--ink-3); font-size: 0.82rem; }

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.tags span {
  padding: 0.35rem 0.7rem;
  background: #f5f5f5;
  border-radius: 14px;
  font-size: 0.8rem;
  color: var(--ink-2);
}
</style>
