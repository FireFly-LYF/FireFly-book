<script setup>
import { computed } from 'vue'
import { toLocalMediaUrl } from '../api'

const props = defineProps({
  note: { type: Object, required: true },
  authorName: { type: String, default: '' },
  likeCount: { type: Number, default: 0 },
  demo: { type: Boolean, default: false },
})

defineEmits(['open'])

const cover = computed(() => toLocalMediaUrl(props.note.coverUrl))
const title = computed(() => props.note.title || props.note.content || '无标题笔记')
</script>

<template>
  <article class="card" @click="$emit('open', note)">
    <div class="cover" :class="{ empty: !cover }">
      <img v-if="cover" :src="cover" :alt="title" loading="lazy" />
      <div v-else class="cover-ph">{{ title.slice(0, 1) }}</div>
      <span v-if="demo" class="demo">示例</span>
    </div>
    <div class="body">
      <h3>{{ title }}</h3>
      <div class="meta">
        <div class="author">
          <span class="avatar">{{ (authorName || 'U').slice(0, 1) }}</span>
          <span class="name">{{ authorName || `用户${note.userId}` }}</span>
        </div>
        <span class="likes">♡ {{ likeCount || '赞' }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.card {
  break-inside: avoid;
  margin-bottom: 8px;
  background: var(--card);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s ease;
}

.card:active { transform: scale(0.985); }

.cover {
  position: relative;
  background: #ececec;
  min-height: 120px;
}

.cover img {
  width: 100%;
  height: auto;
  vertical-align: middle;
}

.cover-ph {
  display: grid;
  place-items: center;
  min-height: 160px;
  font-size: 2rem;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(145deg, #ff6b81, #ff2442 55%, #c91830);
}

.demo {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 0.12rem 0.4rem;
  font-size: 0.65rem;
  font-weight: 600;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 4px;
}

.body { padding: 0.55rem 0.6rem 0.7rem; }

h3 {
  margin: 0;
  font-size: 0.86rem;
  font-weight: 500;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.meta {
  margin-top: 0.45rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.4rem;
  color: var(--ink-3);
  font-size: 0.72rem;
}

.author {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  min-width: 0;
}

.avatar {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f0f0f0;
  color: var(--ink-2);
  display: grid;
  place-items: center;
  font-size: 0.6rem;
  font-weight: 700;
  flex-shrink: 0;
}

.name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.likes { flex-shrink: 0; }
</style>
