<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import {
  getAuthEpoch,
  isSignedMediaPath,
  onAuthChange,
  resolveSignedMediaUrl,
  toLocalMediaUrl,
} from '../api'

const props = defineProps({
  src: { type: String, default: '' },
  alt: { type: String, default: '' },
  loading: { type: String, default: 'lazy' },
})

const resolved = ref('')
const failed = ref(false)
const authEpoch = ref(getAuthEpoch())

const emit = defineEmits(['error', 'load'])

let offAuth = null
onMounted(() => {
  offAuth = onAuthChange((epoch) => {
    authEpoch.value = epoch
  })
})
onUnmounted(() => {
  if (offAuth) offAuth()
})

watch(
  () => [props.src, authEpoch.value],
  async ([raw]) => {
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
      emit('error')
    }
  },
  { immediate: true },
)

function onImgError() {
  failed.value = true
  emit('error')
}
</script>

<template>
  <img
    v-if="resolved && !failed"
    :src="resolved"
    :alt="alt"
    :loading="loading"
    @error="onImgError"
    @load="emit('load')"
  />
</template>
