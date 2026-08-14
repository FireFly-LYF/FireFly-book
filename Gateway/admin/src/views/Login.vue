<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, saveAuth } from '../api/gateway'

const router = useRouter()
const route = useRoute()

const tenant = ref('tenant-a')
const password = ref('')
const loading = ref(false)

/** 提交登录：POST /gateway/login（运维口令），JWT 写入 localStorage */
async function handleLogin() {
  const name = tenant.value.trim()
  if (!name) {
    ElMessage.warning('请输入租户名')
    return
  }
  if (!password.value) {
    ElMessage.warning('请输入运维口令')
    return
  }

  loading.value = true
  try {
    const { token } = await login(name, password.value)
    saveAuth(token, name)
    ElMessage.success('登录成功')

    const redirect = (route.query.redirect as string) || '/services'
    await router.push(redirect)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="hover">
      <template #header>
        <h2>Gateway 管理后台</h2>
      </template>

      <el-form label-width="80px" @submit.prevent="handleLogin">
        <el-form-item label="租户">
          <el-select v-model="tenant" placeholder="选择租户" style="width: 100%">
            <el-option label="tenant-a" value="tenant-a" />
            <el-option label="tenant-b" value="tenant-b" />
          </el-select>
        </el-form-item>

        <el-form-item label="口令">
          <el-input
            v-model="password"
            type="password"
            show-password
            placeholder="admin.password"
            autocomplete="current-password"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" native-type="submit" style="width: 100%">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="hint">使用 gateway.yaml 中 admin.password；业务用户 Token 无法访问运维 API。</p>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
}

.login-card h2 {
  margin: 0;
  text-align: center;
}

.hint {
  margin: 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
