<script setup>
import { ref } from 'vue'
import { userApi } from '../api'

defineProps({ open: Boolean })
const emit = defineEmits(['close', 'success'])

const mode = ref('login')
const form = ref({ username: '', password: '', nickname: '' })
const err = ref('')
const loading = ref(false)
/** 仅当按下与抬起都在遮罩上时才关闭，避免输入框拖选文字时误关 */
const closeOnMaskUp = ref(false)

function fillDemo() {
  mode.value = 'login'
  form.value = { username: 'test', password: 'test', nickname: '' }
  err.value = ''
}

function onMaskPointerDown(e) {
  closeOnMaskUp.value = e.target === e.currentTarget
}

function onMaskClick(e) {
  if (closeOnMaskUp.value && e.target === e.currentTarget) {
    emit('close')
  }
  closeOnMaskUp.value = false
}

async function submit() {
  if (loading.value || !form.value.username) return
  err.value = ''
  loading.value = true
  try {
    const api = userApi()
    const res =
      mode.value === 'login'
        ? await api.login({ username: form.value.username, password: form.value.password })
        : await api.register({
            username: form.value.username,
            password: form.value.password,
            nickname: form.value.nickname || form.value.username,
          })
    if (res.body?.code !== 0) {
      err.value = res.body?.message || '失败'
      return
    }
    if (!res.body?.data?.user || !(res.body.data.accessToken || res.body.data.token)) {
      err.value = '登录异常，请稍后重试'
      return
    }
    emit('success', res.body.data)
  } catch (e) {
    err.value = '网络异常，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div
    v-if="open"
    class="mask"
    @pointerdown="onMaskPointerDown"
    @click="onMaskClick"
  >
    <div class="sheet" @pointerdown.stop>
      <header>
        <strong>{{ mode === 'login' ? '登录 FireFly' : '注册 FireFly' }}</strong>
        <button type="button" class="x" @click="$emit('close')">×</button>
      </header>
      <form @submit.prevent="submit">
        <div class="field">
          <label>用户名</label>
          <input v-model="form.username" placeholder="alice" autocomplete="username" />
        </div>
        <div class="field" v-if="mode === 'register'">
          <label>昵称</label>
          <input v-model="form.nickname" placeholder="可选" />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="form.password" type="password" autocomplete="current-password" />
        </div>
        <p v-if="err" class="err">{{ err }}</p>
        <button type="submit" class="primary" :disabled="loading || !form.username">
          {{ loading ? '提交中…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>
      <button v-if="mode === 'login'" type="button" class="demo" @click="fillDemo">
        使用演示账号
      </button>
      <button type="button" class="switch" @click="mode = mode === 'login' ? 'register' : 'login'">
        {{ mode === 'login' ? '没有账号？去注册' : '已有账号？去登录' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  animation: ff-fade-in var(--dur) ease;
}

.sheet {
  width: 100%;
  background: #fff;
  border-radius: 16px 16px 0 0;
  padding: 1rem 1.1rem calc(1.2rem + var(--safe-bottom));
  animation: ff-rise var(--dur-slow) var(--ease-out);
}

@media (min-width: 769px) {
  .mask {
    align-items: center;
    padding: 1.5rem;
  }
  .sheet {
    width: min(420px, 100%);
    border-radius: 16px;
    padding: 1.25rem 1.35rem 1.4rem;
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.18);
  }
}

header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.x { font-size: 1.4rem; color: var(--ink-3); padding: 0.2rem; }

.field { margin-bottom: 0.75rem; }
label {
  display: block;
  font-size: 0.78rem;
  color: var(--ink-2);
  margin-bottom: 0.3rem;
}

.err {
  margin: 0 0 0.6rem;
  color: var(--xhs-red);
  font-size: 0.82rem;
}

.primary {
  width: 100%;
  padding: 0.75rem;
  border-radius: 22px;
  background: var(--xhs-red);
  color: #fff;
  font-weight: 600;
  transition:
    opacity var(--dur-fast) ease,
    transform var(--dur-fast) var(--ease-out),
    background-color var(--dur-fast) ease;
}

.primary:disabled { opacity: 0.5; transform: none; }

.demo {
  width: 100%;
  margin-top: 0.65rem;
  padding: 0.55rem;
  border-radius: 18px;
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-size: 0.82rem;
  font-weight: 600;
}

.switch {
  width: 100%;
  margin-top: 0.55rem;
  color: var(--ink-2);
  font-size: 0.85rem;
}
</style>
