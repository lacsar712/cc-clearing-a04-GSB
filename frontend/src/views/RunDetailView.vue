<template>
  <div class="page">
    <h2 class="page-title">批次详情</h2>
    <p class="page-desc">查看批次状态、参与义务、净头寸，并可确认 settle</p>

    <div class="toolbar">
      <el-button @click="$router.back()">返回</el-button>
      <el-button @click="load">刷新</el-button>
      <el-button
        type="success"
        :disabled="!auth.isOperator || detail?.run?.status !== 'COMPLETED' || alreadySettled"
        :loading="settling"
        @click="settle"
      >确认 Settle</el-button>
      <el-tooltip
        v-if="detail?.run?.status === 'FAILED' && !auth.isOperator"
        content="仅操作员可重试,只读账户无权限"
        placement="top"
      >
        <span><el-button type="warning" disabled>重试批次</el-button></span>
      </el-tooltip>
      <el-button
        v-else-if="detail?.run?.status === 'FAILED'"
        type="warning"
        :loading="retrying"
        @click="retry"
      >重试批次</el-button>
    </div>

    <el-alert
      v-if="detail?.run?.status === 'FAILED'"
      class="fail-alert"
      style="margin-top:12px"
      type="error"
      :closable="false"
      show-icon
      title="该批次执行失败"
      :description="failureAdvice(detail.run.failureCode)"
    />

    <div class="card-panel" v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Run ID"><span class="mono">{{ detail.run.runId }}</span></el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.run.status === 'COMPLETED' ? 'success' : detail.run.status === 'FAILED' ? 'danger' : 'info'">
              {{ detail.run.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="交割日">{{ detail.run.settleDate }}</el-descriptions-item>
          <el-descriptions-item label="币种">{{ detail.run.currency }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.run.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="ΣnetAmount">{{ detail.sumNetAmount }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.run.failureReason" label="失败码" :span="2">
            <el-tag type="danger">{{ detail.run.failureCode || 'NETTING_FAILED' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.run.failureReason" label="失败原因" :span="2">
            {{ detail.run.failureReason }}
          </el-descriptions-item>
        </el-descriptions>

        <h3 style="margin:20px 0 10px">净头寸</h3>
        <el-table :data="detail.positions" stripe>
          <el-table-column prop="memberId" label="会员 ID" min-width="220">
            <template #default="{ row }"><span class="mono">{{ row.memberId }}</span></template>
          </el-table-column>
          <el-table-column prop="currency" label="币种" width="90" />
          <el-table-column prop="netAmount" label="净头寸" min-width="160" />
        </el-table>

        <h3 style="margin:20px 0 10px">参与义务</h3>
        <el-table :data="detail.obligations" stripe>
          <el-table-column prop="obligationId" label="义务 ID" min-width="200">
            <template #default="{ row }"><span class="mono">{{ row.obligationId }}</span></template>
          </el-table-column>
          <el-table-column prop="payerMemberId" label="付款方" min-width="180" />
          <el-table-column prop="payeeMemberId" label="收款方" min-width="180" />
          <el-table-column prop="amount" label="金额" width="140" />
          <el-table-column prop="status" label="状态" width="110" />
        </el-table>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import { failureAdvice } from '../utils/failureCodes'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const settling = ref(false)
const retrying = ref(false)
const detail = ref(null)

const alreadySettled = computed(() =>
  (detail.value?.obligations || []).every((o) => o.status === 'SETTLED') &&
  (detail.value?.obligations || []).length > 0
)

function formatTime(v) {
  return v ? new Date(v).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    const { data } = await api.get(`/netting-runs/${route.params.id}`)
    detail.value = data
  } finally {
    loading.value = false
  }
}

async function settle() {
  settling.value = true
  try {
    await api.post(`/netting-runs/${route.params.id}/settle`)
    ElMessage.success('Settle 完成，义务已 SETTLED')
    await load()
  } finally {
    settling.value = false
  }
}

async function retry() {
  try {
    await ElMessageBox.confirm(
      '将按原参数（交割日/币种）重新执行轧差，成功后会生成新批次。是否继续？',
      '重试失败批次',
      { confirmButtonText: '重试', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  retrying.value = true
  try {
    const { data } = await api.post(`/netting-runs/${route.params.id}/retry`)
    ElMessage.success('重试成功，已生成新批次 ' + data.run.runId)
    router.push(`/netting-runs/${data.run.runId}`)
  } finally {
    retrying.value = false
  }
}

onMounted(load)
</script>
