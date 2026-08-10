<script setup>
defineProps({
  text: { type: String, default: '待实现' },
})
</script>

<template>
  <span class="todo-tag">{{ text }}</span>
</template>
