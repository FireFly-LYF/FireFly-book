<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, saveAuth } from '../api/gateway'

const router = useRouter()
const route = useRoute()

// 表单：租户名（后端白名单如 tenant-a / tenant-b）
const tenant = ref('tenant-a')
const loading = ref(false)

/** 提交登录：调用 POST /gateway/login，JWT 写入 localStorage */
async function handleLogin() {
  const name = tenant.value.trim()
  if (!name) {
    ElMessage.warning('请输入租户名')
    return
  }

  loading.value = true
  try {
    const { token } = await login(name)
    saveAuth(token, name)
    ElMessage.success('登录成功')

    // 支持从守卫带来的 redirect 参数，登录后跳回原页面
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
            <!-- 与后端 internal/tenant 白名单一致 -->
            <el-option label="tenant-a" value="tenant-a" />
            <el-option label="tenant-b" value="tenant-b" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" native-type="submit" style="width: 100%">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="hint">登录后 Token 存入 localStorage，后续请求自动携带 Authorization 头。</p>
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
