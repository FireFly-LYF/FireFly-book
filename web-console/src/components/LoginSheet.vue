<script setup>
import { ref } from 'vue'
import { userApi } from '../api'

defineProps({ open: Boolean })
const emit = defineEmits(['close', 'success'])

const mode = ref('login')
const form = ref({ username: '', password: '123456', nickname: '' })
const err = ref('')
const loading = ref(false)

async function submit() {
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
    if (!res.body?.data?.token || !res.body?.data?.user) {
      err.value = '未返回 JWT，请重启 user-service'
      return
    }
    emit('success', res.body.data)
  } catch (e) {
    err.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-if="open" class="mask" @click.self="$emit('close')">
    <div class="sheet">
      <header>
        <strong>{{ mode === 'login' ? '登录 FireFly' : '注册 FireFly' }}</strong>
        <button type="button" class="x" @click="$emit('close')">×</button>
      </header>
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
      <button type="button" class="primary" :disabled="loading || !form.username" @click="submit">
        {{ loading ? '提交中…' : mode === 'login' ? '登录' : '注册并登录' }}
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
}

.sheet {
  width: 100%;
  background: #fff;
  border-radius: 16px 16px 0 0;
  padding: 1rem 1.1rem calc(1.2rem + var(--safe-bottom));
  animation: up 0.22s ease;
}

@keyframes up {
  from { transform: translateY(24px); opacity: 0.6; }
  to { transform: none; opacity: 1; }
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
}

.primary:disabled { opacity: 0.5; }

.switch {
  width: 100%;
  margin-top: 0.7rem;
  color: var(--ink-2);
  font-size: 0.85rem;
}
</style>
