<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deregisterService,
  listServices,
  registerService,
  type RegisterServiceRequest,
  type ServiceNode,
} from '../api/gateway'

// 下游节点列表
const services = ref<ServiceNode[]>([])
const loading = ref(false)
const submitting = ref(false)

// 注册表单，字段与后端 RegisterService 一致
const form = reactive<RegisterServiceRequest>({
  http: '',
  grpc: '',
  tcp: '',
  weight: 1,
})

/** 拉取 GET /gateway/services */
async function loadServices() {
  loading.value = true
  try {
    services.value = await listServices()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

/** 校验：http / grpc / tcp 至少填一项 */
function validateForm(): boolean {
  if (!form.http?.trim() && !form.grpc?.trim() && !form.tcp?.trim()) {
    ElMessage.warning('http、grpc、tcp 至少填写一项')
    return false
  }
  return true
}

/** POST /gateway/services 注册新下游 */
async function handleRegister() {
  if (!validateForm()) return

  submitting.value = true
  try {
    const { id } = await registerService({
      http: form.http?.trim() || undefined,
      grpc: form.grpc?.trim() || undefined,
      tcp: form.tcp?.trim() || undefined,
      weight: form.weight ?? 1,
    })
    ElMessage.success(`注册成功，节点 id: ${id}`)
    // 清空表单并刷新列表
    form.http = ''
    form.grpc = ''
    form.tcp = ''
    form.weight = 1
    await loadServices()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '注册失败')
  } finally {
    submitting.value = false
  }
}

/** DELETE /gateway/services 按 id 下线 */
async function handleDeregister(row: ServiceNode) {
  try {
    await ElMessageBox.confirm(`确认下线节点「${row.id}」？`, '提示', { type: 'warning' })
    await deregisterService({ id: row.id })
    ElMessage.success('下线成功')
    await loadServices()
  } catch (err) {
    // 用户点取消时 err 为 'cancel'，不提示
    if (err !== 'cancel' && err instanceof Error) {
      ElMessage.error(err.message)
    }
  }
}

/** 格式化健康检查时间 */
function formatTime(iso: string): string {
  if (!iso) return '-'
  return new Date(iso).toLocaleString()
}

onMounted(loadServices)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <template #header>注册下游服务</template>
      <el-form :inline="true" @submit.prevent="handleRegister">
        <el-form-item label="HTTP">
          <el-input v-model="form.http" placeholder="http://localhost:9004" clearable />
        </el-form-item>
        <el-form-item label="gRPC">
          <el-input v-model="form.grpc" placeholder="localhost:50054" clearable />
        </el-form-item>
        <el-form-item label="TCP">
          <el-input v-model="form.tcp" placeholder="localhost:9010" clearable />
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="100" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleRegister">注册</el-button>
          <el-button @click="loadServices">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <template #header>下游节点列表</template>
      <el-table v-loading="loading" :data="services" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" min-width="180" />
        <el-table-column prop="http" label="HTTP" min-width="200">
          <template #default="{ row }">{{ row.http || '-' }}</template>
        </el-table-column>
        <el-table-column prop="grpc" label="gRPC" min-width="160">
          <template #default="{ row }">{{ row.grpc || '-' }}</template>
        </el-table-column>
        <el-table-column prop="tcp" label="TCP" min-width="160">
          <template #default="{ row }">{{ row.tcp || '-' }}</template>
        </el-table-column>
        <el-table-column prop="weight" label="权重" width="80" align="center" />
        <el-table-column prop="healthy" label="健康" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.healthy ? 'success' : 'danger'">
              {{ row.healthy ? '健康' : '异常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="last_check" label="最近检查" min-width="170">
          <template #default="{ row }">{{ formatTime(row.last_check) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleDeregister(row)">下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.table-card {
  flex: 1;
}
</style>
