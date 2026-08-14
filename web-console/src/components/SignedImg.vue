<script setup>
import { ref, watch } from 'vue'
import { isSignedMediaPath, resolveSignedMediaUrl, toLocalMediaUrl } from '../api'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  loading: { type: String, default: 'lazy' },
})

const resolved = ref('')
const failed = ref(false)

watch(
  () => props.src,
  async (raw) => {
    failed.value = false
    resolved.value = ''
    if (!raw) return
    const path = toLocalMediaUrl(raw)
    if (!isSignedMediaPath(path)) {
      resolved.value = path || raw
      return
    }
    try {
      resolved.value = await resolveSignedMediaUrl(path)
    } catch {
      failed.value = true
      resolved.value = ''
    }
  },
  { immediate: true },
)
</script>

<template>
  <img
    v-if="resolved && !failed"
    :src="resolved"
    :alt="alt"
    :loading="loading"
    @error="failed = true"
  />
</template>
