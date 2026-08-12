<script setup>
import { computed } from 'vue'
import { resolveAvatarUrl } from '../avatars'
import { toLocalMediaUrl } from '../api'

const props = defineProps({
  user: { type: Object, default: null },
  userId: { type: [Number, String], default: null },
  avatarUrl: { type: String, default: '' },
  name: { type: String, default: '' },
  size: { type: [Number, String], default: 40 },
})

const src = computed(() => {
  const raw = props.avatarUrl || props.user?.avatarUrl || ''
  const resolved = resolveAvatarUrl(
    raw || props.user || null,
    props.userId ?? props.user?.id,
  )
  return toLocalMediaUrl(resolved) || resolved
})

const letter = computed(() => (props.name || props.user?.nickname || props.user?.username || 'U').slice(0, 1))

const boxStyle = computed(() => {
  const s = typeof props.size === 'number' ? `${props.size}px` : props.size
  return { width: s, height: s, fontSize: `calc(${s} * 0.4)` }
})
</script>

<template>
  <span class="ua" :style="boxStyle" :title="name || undefined">
    <img v-if="src" :src="src" alt="" loading="lazy" @error="$event.target.style.display='none'" />
    <span v-else class="letter">{{ letter }}</span>
  </span>
</template>

<style scoped>
.ua {
  display: inline-grid;
  place-items: center;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--brand-soft, #ffe4e8);
  color: var(--brand, #ff2442);
  font-weight: 700;
  vertical-align: middle;
}

.ua img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.letter {
  line-height: 1;
}
</style>
