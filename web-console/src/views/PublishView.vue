<script setup>
import { ref } from 'vue'
import { mediaApi, noteApi, toLocalMediaUrl } from '../api'

const props = defineProps({ loggedIn: Boolean })
const emit = defineEmits(['need-login', 'toast', 'published'])

const form = ref({ title: '', content: '', coverUrl: '' })
const uploading = ref(false)
const publishing = ref(false)

async function onPick(e) {
  if (!props.loggedIn) return emit('need-login')
  const file = e.target.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const res = await mediaApi().upload(file)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '上传失败')
      return
    }
    const url = res.body.data?.url || res.body.data?.accessUrl || ''
    form.value.coverUrl = toLocalMediaUrl(url) || url
    emit('toast', '封面已上传')
  } finally {
    uploading.value = false
    e.target.value = ''
  }
}

async function publish() {
  if (!props.loggedIn) return emit('need-login')
  if (!form.value.title.trim() && !form.value.content.trim()) {
    return emit('toast', '写点标题或正文吧')
  }
  publishing.value = true
  try {
    const res = await noteApi().create({
      title: form.value.title || '无标题',
      content: form.value.content,
      coverUrl: form.value.coverUrl || null,
    })
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '发布失败')
      return
    }
    form.value = { title: '', content: '', coverUrl: '' }
    emit('toast', '发布成功')
    emit('published', res.body.data)
  } finally {
    publishing.value = false
  }
}
</script>

<template>
  <div class="pub">
    <header>
      <strong>发布笔记</strong>
      <button type="button" class="go" :disabled="publishing" @click="publish">
        {{ publishing ? '发布中…' : '发布' }}
      </button>
    </header>

    <label class="cover">
      <input type="file" accept="image/*" hidden @change="onPick" />
      <img v-if="form.coverUrl" :src="form.coverUrl" alt="封面" />
      <div v-else class="ph">
        <span>{{ uploading ? '上传中…' : '添加封面图' }}</span>
        <small>走 media-service</small>
      </div>
    </label>

    <input v-model="form.title" class="title" placeholder="添加标题" maxlength="40" />
    <textarea v-model="form.content" rows="8" placeholder="分享你的生活…" />

    <div class="tools">
      <button type="button" class="chip" disabled>话题</button>
      <button type="button" class="chip" disabled>地点</button>
      <button type="button" class="chip" disabled>@用户</button>
    </div>
  </div>
</template>

<style scoped>
.pub {
  min-height: 100%;
  background: #fff;
}

@media (min-width: 769px) {
  .pub {
    max-width: 720px;
    margin: 16px auto 0;
    border: 1px solid var(--line);
    border-radius: 12px;
    min-height: auto;
  }
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid var(--line);
}

.go {
  padding: 0.4rem 1rem;
  border-radius: 16px;
  background: var(--xhs-red);
  color: #fff;
  font-weight: 600;
  font-size: 0.88rem;
}
.go:disabled { opacity: 0.5; }

.cover {
  display: block;
  margin: 0.85rem 1rem;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f5f5;
  cursor: pointer;
  min-height: 180px;
}

.cover img {
  width: 100%;
  max-height: 280px;
  object-fit: cover;
}

.ph {
  min-height: 180px;
  display: grid;
  place-content: center;
  gap: 0.3rem;
  color: var(--ink-3);
  text-align: center;
}
.ph small { font-size: 0.72rem; }

.title, textarea {
  display: block;
  width: calc(100% - 2rem);
  margin: 0 1rem 0.7rem;
  border: none;
  background: transparent;
  border-radius: 0;
  padding: 0.4rem 0;
}

.title {
  font-size: 1.1rem;
  font-weight: 600;
  border-bottom: 1px solid var(--line);
}

textarea {
  resize: vertical;
  min-height: 140px;
}

.tools {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  padding: 0.5rem 1rem;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.4rem 0.65rem;
  border-radius: 14px;
  background: #f5f5f5;
  color: var(--ink-2);
  font-size: 0.8rem;
}
</style>
