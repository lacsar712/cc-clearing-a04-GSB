<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">轧差清算工作台</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/members">会员</el-menu-item>
        <el-menu-item index="/obligations">义务</el-menu-item>
        <el-menu-item index="/netting">轧差执行</el-menu-item>
        <el-menu-item index="/failures">
          <span>失败诊断</span>
          <el-badge v-if="failedCount > 0" :value="failedCount" class="fail-badge" type="danger" />
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-right">
          <span>{{ auth.username }}（{{ auth.role === 'OPERATOR' ? '操作员' : '只读' }}）</span>
          <el-button link type="primary" @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api/client'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const failedCount = ref(0)
let timer = null

async function refreshFailedCount() {
  try {
    const { data } = await api.get('/netting-runs')
    failedCount.value = data.filter((r) => r.status === 'FAILED').length
  } catch {
    // unauthenticated / transient errors: leave badge unchanged
  }
}

function onLogout() {
  auth.logout()
  router.push({ name: 'login' })
}

onMounted(() => {
  refreshFailedCount()
  timer = setInterval(refreshFailedCount, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.layout {
  min-height: 100vh;
}
.aside {
  background: #102a43;
  color: #fff;
}
.brand {
  padding: 20px 16px;
  font-weight: 700;
  font-size: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.aside :deep(.el-menu) {
  border-right: none;
  background: transparent;
}
.aside :deep(.el-menu-item) {
  color: #d9e2ec;
}
.aside :deep(.el-menu-item.is-active) {
  background: #243b53;
  color: #fff;
}
.fail-badge {
  margin-left: 8px;
}
.header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid var(--line);
}
.header-right {
  display: flex;
  gap: 12px;
  align-items: center;
  color: var(--muted);
}
</style>
