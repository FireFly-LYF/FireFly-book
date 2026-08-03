<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAuth, getTenant } from '../api/gateway'

const route = useRoute()
const router = useRouter()

/** 当前登录租户，展示在顶栏 */
const tenant = computed(() => getTenant() ?? '未知')

/** 侧边栏高亮项与路由 name 对应 */
const activeMenu = computed(() => route.name as string)

/** 退出登录：清 localStorage 并回登录页 */
function handleLogout() {
  clearAuth()
  router.push({ name: 'Login' })
}
</script>

<template>
  <el-container class="admin-layout">
    <!-- 左侧导航 -->
    <el-aside width="200px" class="aside">
      <div class="logo">Gateway Admin</div>
      <el-menu :default-active="activeMenu" router>
        <el-menu-item index="Services" :route="{ name: 'Services' }">
          服务管理
        </el-menu-item>
        <el-menu-item index="Dashboard" :route="{ name: 'Dashboard' }">
          网关状态
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏：租户信息 + 退出 -->
      <el-header class="header">
        <span>当前租户：{{ tenant }}</span>
        <el-button type="danger" link @click="handleLogout">退出登录</el-button>
      </el-header>

      <!-- 子路由页面出口 -->
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.aside {
  background: #304156;
  color: #fff;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-weight: bold;
  font-size: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}

.main {
  background: #f5f7fa;
}
</style>
