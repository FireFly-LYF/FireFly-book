<script setup>
import { onMounted, ref, watch } from 'vue'
import { searchApi, socialApi } from '../api'
import NoteCard from '../components/NoteCard.vue'

const props = defineProps({
  topic: { type: String, default: '' },
})
const emit = defineEmits(['back', 'open-note', 'open-profile', 'toast'])

const loading = ref(false)
const notes = ref([])
const likeMap = ref({})

async function load() {
  const q = String(props.topic || '').trim()
  if (!q) {
    notes.value = []
    return
  }
  loading.value = true
  try {
    const res = await searchApi().notes(q, 1, 30)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '话题加载失败')
      notes.value = []
      return
    }
    notes.value = res.body.data || []
    const map = {}
    await Promise.all(
      notes.value.slice(0, 20).map(async (n) => {
        try {
          const c = await socialApi().likeCount(n.id)
          if (c.body?.code === 0) map[n.id] = c.body.data?.count ?? 0
        } catch {
          /* ignore */
        }
      }),
    )
    likeMap.value = map
  } finally {
    loading.value = false
  }
}

watch(() => props.topic, load)
onMounted(load)
</script>

<template>
  <div class="topic">
    <header>
      <button type="button" class="back" @click="$emit('back')">←</button>
      <div>
        <h2>#{{ topic }}</h2>
        <p>相关笔记</p>
      </div>
    </header>
    <p v-if="loading" class="muted">加载中…</p>
    <p v-else-if="!notes.length" class="muted">暂无相关笔记，发一篇带 #{{ topic }} 试试</p>
    <div v-else class="xhs-waterfall pad">
      <NoteCard
        v-for="n in notes"
        :key="n.id"
        :note="n"
        :author-name="n.nickname || n.username || ''"
        :author-avatar="n.avatarUrl || ''"
        :like-count="likeMap[n.id] ?? n.likeCount ?? 0"
        @open="(note) => $emit('open-note', note.id)"
        @open-profile="(uid) => $emit('open-profile', uid)"
      />
    </div>
  </div>
</template>

<style scoped>
.topic { min-height: 100%; background: #fff; }
header {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 2;
}
.back {
  border: none;
  background: transparent;
  font-size: 1.1rem;
  cursor: pointer;
}
h2 {
  margin: 0;
  font-family: "ZCOOL XiaoWei", serif;
  font-size: 1.2rem;
  font-weight: 400;
}
header p {
  margin: 0.15rem 0 0;
  font-size: 0.72rem;
  color: var(--ink-3);
}
.muted { color: var(--ink-3); padding: 1.2rem 1rem; font-size: 0.86rem; }
.pad { padding: 0.75rem; }
</style>
