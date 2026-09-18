<template>
  <div class="page">
    <h2 class="page-title">失败批次诊断</h2>
    <p class="page-desc">查看所有 FAILED 轧差批次的失败原因;修复数据后操作员可重试,重试成功将生成新批次</p>

    <div class="toolbar">
      <el-button @click="load">刷新</el-button>
    </div>

    <div class="card-panel">
      <el-empty v-if="!loading && failures.length === 0" description="暂无失败批次" />
      <el-table v-else :data="failures" v-loading="loading" stripe row-key="runId">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="diag-detail">
              <div class="diag-row">
                <span class="diag-label">错误码</span>
                <el-tag type="danger">{{ row.failureCode || 'NETTING_FAILED' }}</el-tag>
              </div>
              <div class="diag-row">
                <span class="diag-label">失败原因</span>
                <span class="diag-text">{{ row.failureReason || '（无详细信息）' }}</span>
              </div>
              <div class="diag-row">
                <span class="diag-label">修复建议</span>
                <span class="diag-text advice">{{ failureAdvice(row.failureCode) }}</span>
              </div>
              <div class="diag-row">
                <span class="diag-label">操作</span>
                <el-tooltip v-if="!auth.isOperator" content="仅操作员可重试,只读账户无权限" placement="top">
                  <span><el-button type="warning" disabled>重试批次</el-button></span>
                </el-tooltip>
                <el-button v-else type="warning" :loading="retryingId === row.runId" @click="retry(row)">
                  重试批次
                </el-button>
                <router-link :to="`/netting-runs/${row.runId}`">查看原批次详情</router-link>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="runId" label="Run ID" min-width="200">
          <template #default="{ row }">
            <router-link class="mono" :to="`/netting-runs/${row.runId}`">{{ row.runId }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="settleDate" label="交割日" width="120" />
        <el-table-column prop="currency" label="币种" width="90" />
        <el-table-column label="错误码" width="180">
          <template #default="{ row }">
            <el-tag type="danger">{{ row.failureCode || 'NETTING_FAILED' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureReason" label="失败原因" min-width="240" show-overflow-tooltip />
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-tooltip v-if="!auth.isOperator" content="仅操作员可重试" placement="top">
              <span><el-button size="small" type="warning" disabled>重试</el-button></span>
            </el-tooltip>
            <el-button
              v-else
              size="small"
              type="warning"
              :loading="retryingId === row.runId"
              @click="retry(row)"
            >重试</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { failureAdvice } from '../utils/failureCodes'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const retryingId = ref(null)
const failures = ref([])

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get('/netting-runs')
    failures.value = data.filter((r) => r.status === 'FAILED')
  } finally {
    loading.value = false
  }
}

async function retry(row) {
  try {
    await ElMessageBox.confirm(
      `将按原参数（交割日 ${row.settleDate}，币种 ${row.currency}）重新执行轧差,成功后会生成新批次。是否继续?`,
      '重试失败批次',
      { confirmButtonText: '重试', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  retryingId.value = row.runId
  try {
    const { data } = await api.post(`/netting-runs/${row.runId}/retry`)
    ElMessage.success('重试成功,已生成新批次 ' + data.run.runId)
    await load()
    router.push(`/netting-runs/${data.run.runId}`)
  } finally {
    retryingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.diag-detail {
  padding: 8px 24px 16px;
}
.diag-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 6px 0;
}
.diag-label {
  width: 72px;
  flex-shrink: 0;
  color: var(--muted);
}
.diag-text {
  white-space: pre-wrap;
  word-break: break-all;
}
.advice {
  color: #b8860b;
}
</style>
