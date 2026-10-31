<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { mediaApi, noteApi, toLocalMediaUrl, newIdempotencyKey } from '../api'
import SignedImg from '../components/SignedImg.vue'

const props = defineProps({ loggedIn: Boolean })
const emit = defineEmits(['need-login', 'toast', 'published'])

const MAX_IMAGES = 9
const MAX_VIDEO_MB = 100
const DRAFT_KEY = 'ff-publish-draft'

/** 南京演示地点（发布可选） */
const NJ_PLACES = [
  { id: 'xjk', name: '南京·新街口', lat: 32.041544, lng: 118.784485 },
  { id: 'xwh', name: '南京·玄武湖', lat: 32.0706, lng: 118.7965 },
  { id: 'yht', name: '南京·雨花台', lat: 31.995, lng: 118.774 },
  { id: 'cjl', name: '南京·夫子庙', lat: 32.0217, lng: 118.7898 },
  { id: 'njn', name: '南京·南站', lat: 31.9688, lng: 118.798 },
]

/** image | video */
const mode = ref('image')
/** @type {import('vue').Ref<Array<{ url: string, accessUrl: string, kind: 'image'|'video' }>>} */
const media = ref([])
const form = ref({ title: '', content: '' })
const place = ref(null)
const placeOpen = ref(false)
const uploading = ref(false)
const publishing = ref(false)
const publishIdemKey = ref(null)
const fileInput = ref(null)

const placeLabel = computed(() => place.value?.name || '')
const canAddMore = computed(() => {
  if (mode.value === 'video') return media.value.length < 1
  return media.value.length < MAX_IMAGES
})
const acceptTypes = computed(() =>
  mode.value === 'video' ? 'video/mp4,video/webm' : 'image/*',
)

onMounted(() => {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return
    const d = JSON.parse(raw)
    if (d?.title) form.value.title = d.title
    if (d?.content) form.value.content = d.content
    if (d?.mode === 'video' || d?.mode === 'image') mode.value = d.mode
    if (Array.isArray(d?.media) && d.media.length) media.value = d.media
    if (d?.place) place.value = d.place
  } catch {
    /* ignore */
  }
})

watch(
  [form, media, mode, place],
  () => {
    try {
      localStorage.setItem(
        DRAFT_KEY,
        JSON.stringify({
          title: form.value.title,
          content: form.value.content,
          mode: mode.value,
          media: media.value,
          place: place.value,
        }),
      )
    } catch {
      /* ignore quota */
    }
  },
  { deep: true },
)

function clearDraft() {
  localStorage.removeItem(DRAFT_KEY)
}

function switchMode(next) {
  if (next === mode.value) return
  if (media.value.length) {
    emit('toast', '切换模式会清空已选媒体')
    media.value = []
  }
  mode.value = next
}

function pickFiles() {
  if (!props.loggedIn) return emit('need-login')
  fileInput.value?.click()
}

async function onPick(e) {
  if (!props.loggedIn) return emit('need-login')
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return

  if (mode.value === 'video') {
    const file = files[0]
    if (!file.type.startsWith('video/')) {
      emit('toast', '请选择视频文件')
      return
    }
    if (file.size > MAX_VIDEO_MB * 1024 * 1024) {
      emit('toast', `视频请小于 ${MAX_VIDEO_MB}MB`)
      return
    }
    await uploadOne(file, 'video')
    return
  }

  const room = MAX_IMAGES - media.value.length
  const images = files.filter((f) => f.type.startsWith('image/')).slice(0, room)
  if (!images.length) {
    emit('toast', '请选择图片')
    return
  }
  for (const file of images) {
    // eslint-disable-next-line no-await-in-loop
    await uploadOne(file, 'image')
  }
}

async function uploadOne(file, kind) {
  uploading.value = true
  try {
    const res = await mediaApi().upload(file)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '上传失败')
      return
    }
    const data = res.body.data || {}
    const url = toLocalMediaUrl(data.url) || data.url || ''
    if (!url) {
      emit('toast', '上传结果无效')
      return
    }
    if (kind === 'video') {
      media.value = [
        {
          url,
          accessUrl: data.accessUrl || url,
          kind: 'video',
        },
      ]
    } else {
      media.value = [
        ...media.value,
        { url, accessUrl: data.accessUrl || url, kind: 'image' },
      ]
    }
  } finally {
    uploading.value = false
  }
}

function removeAt(i) {
  media.value = media.value.filter((_, idx) => idx !== i)
}

function move(i, dir) {
  const j = i + dir
  if (j < 0 || j >= media.value.length) return
  const next = media.value.slice()
  const tmp = next[i]
  next[i] = next[j]
  next[j] = tmp
  media.value = next
}

function pickPlace(p) {
  place.value = p
  placeOpen.value = false
}

function clearPlace() {
  place.value = null
  placeOpen.value = false
}

async function useDeviceLocation() {
  if (!navigator.geolocation) {
    emit('toast', '浏览器不支持定位')
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      place.value = {
        id: 'device',
        name: '当前位置',
        lat: pos.coords.latitude,
        lng: pos.coords.longitude,
      }
      placeOpen.value = false
      emit('toast', '已使用当前位置')
    },
    () => emit('toast', '定位失败，可选手动南京地点'),
    { enableHighAccuracy: false, timeout: 8000 },
  )
}

async function publish() {
  if (!props.loggedIn) return emit('need-login')
  if (!form.value.title.trim() && !form.value.content.trim()) {
    return emit('toast', '写点标题或正文吧')
  }
  if (!media.value.length) {
    return emit('toast', mode.value === 'video' ? '请添加视频' : '请至少添加一张图片')
  }
  publishing.value = true
  if (!publishIdemKey.value) publishIdemKey.value = newIdempotencyKey()
  try {
    const mediaUrls = media.value.map((m) => m.url)
    const body = {
      title: form.value.title || '无标题',
      content: form.value.content,
      coverUrl: mediaUrls[0] || null,
      mediaUrls,
      mediaKind: mode.value === 'video' ? 'video' : 'image',
    }
    if (place.value?.lat != null && place.value?.lng != null) {
      body.lat = place.value.lat
      body.lng = place.value.lng
      body.placeName = place.value.name || null
    }
    const res = await noteApi().create(body, publishIdemKey.value)
    if (res.body?.code !== 0) {
      emit('toast', res.body?.message || '发布失败')
      return
    }
    publishIdemKey.value = null
    form.value = { title: '', content: '' }
    media.value = []
    place.value = null
    mode.value = 'image'
    clearDraft()
    emit('toast', '已发布，审核通过后会出现在发现页')
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
      <button type="button" class="go" :disabled="publishing || uploading" @click="publish">
        {{ publishing ? '发布中…' : '发布' }}
      </button>
    </header>

    <div class="mode-tabs">
      <button type="button" :class="{ on: mode === 'image' }" @click="switchMode('image')">图文</button>
      <button type="button" :class="{ on: mode === 'video' }" @click="switchMode('video')">视频</button>
    </div>

    <input
      ref="fileInput"
      type="file"
      :accept="acceptTypes"
      :multiple="mode === 'image'"
      hidden
      @change="onPick"
    />

    <div class="media-grid">
      <div v-for="(m, i) in media" :key="m.url + i" class="media-item">
        <SignedImg v-if="m.kind === 'image'" :src="m.accessUrl || m.url" alt="" />
        <video v-else :src="m.accessUrl || m.url" muted playsinline preload="metadata" />
        <span v-if="i === 0" class="badge">封面</span>
        <span v-if="m.kind === 'video'" class="badge video">视频</span>
        <div class="media-actions">
          <button v-if="mode === 'image'" type="button" :disabled="i === 0" @click="move(i, -1)">‹</button>
          <button
            v-if="mode === 'image'"
            type="button"
            :disabled="i === media.length - 1"
            @click="move(i, 1)"
          >
            ›
          </button>
          <button type="button" class="del" @click="removeAt(i)">×</button>
        </div>
      </div>
      <button
        v-if="canAddMore"
        type="button"
        class="media-add"
        :disabled="uploading"
        @click="pickFiles"
      >
        <span>{{ uploading ? '上传中…' : mode === 'video' ? '+' : '+' }}</span>
        <small>
          {{
            mode === 'video'
              ? '添加视频'
              : `添加图片 ${media.length}/${MAX_IMAGES}`
          }}
        </small>
      </button>
    </div>

    <input v-model="form.title" class="title" placeholder="添加标题" maxlength="40" />
    <textarea
      v-model="form.content"
      rows="8"
      placeholder="分享你的生活… 可用 #话题 和 @用户"
    />

    <div class="tools">
      <button
        type="button"
        class="chip"
        :class="{ on: !!place }"
        @click="placeOpen = !placeOpen"
      >
        {{ placeLabel || '添加地点' }}
      </button>
      <button v-if="place" type="button" class="chip ghost" @click="clearPlace">清除地点</button>
      <button
        v-if="form.title || form.content || media.length"
        type="button"
        class="chip ghost"
        @click="
          form = { title: '', content: '' };
          media = [];
          place = null;
          clearDraft();
          emit('toast', '已清空草稿');
        "
      >
        清空草稿
      </button>
    </div>

    <div v-if="placeOpen" class="place-panel">
      <button type="button" class="place-item" @click="useDeviceLocation">使用当前位置</button>
      <button
        v-for="p in NJ_PLACES"
        :key="p.id"
        type="button"
        class="place-item"
        :class="{ on: place?.id === p.id }"
        @click="pickPlace(p)"
      >
        {{ p.name }}
      </button>
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

.mode-tabs {
  display: flex;
  gap: 0.35rem;
  padding: 0.75rem 1rem 0.25rem;
}
.mode-tabs button {
  border: none;
  background: #f5f5f5;
  border-radius: 999px;
  padding: 0.35rem 0.9rem;
  font-size: 0.82rem;
  color: var(--ink-2);
  cursor: pointer;
}
.mode-tabs button.on {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 600;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.45rem;
  padding: 0.85rem 1rem;
}

.media-item,
.media-add {
  position: relative;
  aspect-ratio: 1;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f5f5;
}

.media-item :deep(img),
.media-item video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.badge {
  position: absolute;
  left: 0.3rem;
  top: 0.3rem;
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 0.65rem;
}
.badge.video { left: auto; right: 0.3rem; background: rgba(255, 36, 66, 0.85); }

.media-actions {
  position: absolute;
  right: 0.2rem;
  bottom: 0.2rem;
  display: flex;
  gap: 0.15rem;
}
.media-actions button {
  width: 1.4rem;
  height: 1.4rem;
  border: none;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.92);
  font-size: 0.85rem;
  cursor: pointer;
  line-height: 1;
}
.media-actions .del { color: var(--brand); font-weight: 700; }
.media-actions button:disabled { opacity: 0.35; }

.media-add {
  border: 1px dashed #d4d4d4;
  display: grid;
  place-content: center;
  gap: 0.15rem;
  color: var(--ink-3);
  cursor: pointer;
}
.media-add span { font-size: 1.4rem; line-height: 1; }
.media-add small { font-size: 0.68rem; }
.media-add:disabled { opacity: 0.5; }

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
  border: none;
  cursor: pointer;
}
.chip.on {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 600;
}
.chip.ghost {
  background: transparent;
  color: var(--ink-3);
  text-decoration: underline;
}

.place-panel {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin: 0 1rem 1rem;
  padding: 0.45rem;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fafafa;
}

.place-item {
  text-align: left;
  padding: 0.55rem 0.65rem;
  border-radius: 8px;
  font-size: 0.86rem;
  color: var(--ink);
  border: none;
  background: transparent;
  cursor: pointer;
}
.place-item:hover,
.place-item.on {
  background: #fff;
  color: var(--brand-deep);
  font-weight: 600;
}
</style>
