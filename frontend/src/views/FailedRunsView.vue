<template>
  <div class="page">
    <h2 class="page-title">失败诊断</h2>
    <p class="page-desc">查看 FAILED 批次的失败原因；修好数据后操作员可重试，重试成功会生成新批次</p>

    <div class="card-panel">
      <div class="toolbar" style="justify-content:space-between">
        <strong>失败批次（{{ failedRuns.length }}）</strong>
        <el-button @click="load">刷新</el-button>
      </div>
      <el-table :data="failedRuns" v-loading="loading" stripe style="margin-top:12px">
        <el-table-column prop="runId" label="Run ID" min-width="220">
          <template #default="{ row }">
            <router-link class="mono" :to="`/netting-runs/${row.runId}`">{{ row.runId }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="settleDate" label="交割日" width="120" />
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column prop="failureReason" label="失败原因" min-width="240">
          <template #default="{ row }">
            <span class="reason">{{ row.failureReason || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              :disabled="!auth.isOperator"
              :loading="retryingId === row.runId"
              @click="retry(row)"
            >重试</el-button>
          </template>
        </el-table-column>
        <template #empty>暂无失败批次</template>
      </el-table>
      <p v-if="!auth.isOperator" class="hint">当前为只读账号，可查看失败原因但不能重试。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const retryingId = ref('')
const runs = ref([])

const failedRuns = computed(() => runs.value.filter((r) => r.status === 'FAILED'))

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/netting-runs')
    runs.value = data
  } finally {
    loading.value = false
  }
}

async function retry(row) {
  retryingId.value = row.runId
  try {
    const { data } = await api.post(`/netting-runs/${row.runId}/retry`)
    ElMessage.success(`重试成功，新批次 ${data.run.runId} 已 ${data.run.status}`)
    router.push(`/netting-runs/${data.run.runId}`)
  } catch (e) {
    // 重试后仍失败：后端已生成新的 FAILED 批次，刷新列表展示最新原因
    await load()
  } finally {
    retryingId.value = ''
  }
}

onMounted(load)
</script>

<style scoped>
.reason {
  color: #c0392b;
  word-break: break-all;
}
.hint {
  margin-top: 10px;
  color: var(--muted);
  font-size: 13px;
}
</style>
