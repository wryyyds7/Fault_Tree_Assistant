<template>
  <div class="app-container">
    <el-container>
      <el-header height="64px">
        <div class="header-content">
          <div class="logo-section">
            <el-icon class="logo-icon"><Grid /></el-icon>
            <h1 class="logo">工业设备故障树智能生成系统</h1>
          </div>
          <el-menu
            :default-active="activeIndex"
            class="el-menu-horizontal"
            mode="horizontal"
            :ellipsis="false"
            @select="handleSelect"
          >
            <el-menu-item index="1">
              <el-icon><HomeFilled /></el-icon>
              <span>首页</span>
            </el-menu-item>
            <el-menu-item index="2">
              <el-icon><Document /></el-icon>
              <span>文档管理</span>
            </el-menu-item>
            <el-menu-item index="3">
              <el-icon><Share /></el-icon>
              <span>故障树管理</span>
            </el-menu-item>
            <el-menu-item index="4">
              <el-icon><Connection /></el-icon>
              <span>知识图谱</span>
            </el-menu-item>
            <el-menu-item index="6">
              <el-icon><ChatLineSquare /></el-icon>
              <span>AI助手</span>
            </el-menu-item>
            <el-menu-item index="5">
              <el-icon><ChatDotRound /></el-icon>
              <span>{{ isAdmin ? '反馈管理' : '意见反馈' }}</span>
            </el-menu-item>
          </el-menu>
          <div class="user-section">
            <el-tag v-if="isAdmin" type="warning" size="small" effect="dark" class="admin-tag">
              管理员
            </el-tag>
            <el-dropdown @command="handleUserCommand">
              <span class="user-dropdown-link">
                <el-avatar :size="32" icon="UserFilled" />
                <span class="username">{{ username }}</span>
                <el-icon class="el-icon--arrow-down"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <el-icon><User /></el-icon>
                    个人中心
                  </el-dropdown-item>
                  <el-dropdown-item command="settings">
                    <el-icon><Setting /></el-icon>
                    设置
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  HomeFilled,
  Document,
  Share,
  Connection,
  ChatDotRound,
  ChatLineSquare,
  Grid,
  User,
  Setting,
  SwitchButton,
  ArrowDown
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const username = ref(localStorage.getItem('username') || '用户')
const isAdmin = computed(() => localStorage.getItem('role') === 'ADMIN')

const routeIndexMap = {
  '/': '1',
  '/document': '2',
  '/fault-tree': '3',
  '/knowledge-graph': '4',
  '/ai-assistant': '6',
  '/feedback': '5'
}

const activeIndex = computed(() => {
  const path = route.path
  if (path.startsWith('/fault-tree/edit')) {
    return '3'
  }
  return routeIndexMap[path] || '1'
})

const handleSelect = (index) => {
  const routeMap = {
    '1': '/',
    '2': '/document',
    '3': '/fault-tree',
    '4': '/knowledge-graph',
    '6': '/ai-assistant',
    '5': '/feedback'
  }
  if (routeMap[index]) {
    router.push(routeMap[index])
  }
}

const handleUserCommand = (command) => {
  switch (command) {
    case 'logout':
      ElMessage.success('已退出登录')
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('role')
      localStorage.removeItem('username')
      router.push('/login')
      break
    case 'profile':
      ElMessage.info('个人中心功能开发中')
      break
    case 'settings':
      ElMessage.info('设置功能开发中')
      break
  }
}

watch(() => route.path, (newPath) => {
  const index = routeIndexMap[newPath]
  if (index) {
    activeIndex.value = index
  }
})
</script>

<style scoped>
.app-container {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

.el-container {
  height: 100%;
}

.el-header {
  background: linear-gradient(135deg, #1e3a5f 0%, #0f2847 50%, #1e3a5f 100%);
  color: #fff;
  box-shadow: 0 4px 12px rgba(30, 58, 95, 0.4);
  padding: 0 24px;
  position: relative;
}

.el-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.2), transparent);
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  font-size: 28px;
  color: #fff;
}

.logo {
  font-size: 20px;
  font-weight: 600;
  color: #fff;
  margin: 0;
  white-space: nowrap;
}

.el-menu-horizontal {
  flex: 1;
  max-width: 700px;
  margin: 0 40px;
  border-bottom: none;
  background: transparent;
}

.el-menu-item {
  color: rgba(255, 255, 255, 0.75);
  font-size: 15px;
  padding: 0 20px;
  height: 64px;
  line-height: 64px;
  border-bottom: 3px solid transparent;
  transition: all 0.25s ease;
  position: relative;
  margin: 0 4px;
  border-radius: 8px 8px 0 0;
}

.el-menu-item::before {
  content: '';
  position: absolute;
  bottom: -3px;
  left: 50%;
  transform: translateX(-50%) scaleX(0);
  width: 60%;
  height: 3px;
  background: linear-gradient(90deg, #60a5fa, #3b82f6);
  border-radius: 3px 3px 0 0;
  transition: transform 0.25s ease;
}

.el-menu-item:hover {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.08);
}

.el-menu-item.is-active {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.12);
}

.el-menu-item.is-active::before {
  transform: translateX(-50%) scaleX(1);
}

.el-menu-item span {
  margin-left: 6px;
}

.user-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-tag {
  font-weight: 600;
}

.user-dropdown-link {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 20px;
  transition: background-color 0.3s;
  color: #fff;
}

.user-dropdown-link:hover {
  background-color: rgba(255, 255, 255, 0.15);
}

.username {
  font-size: 14px;
  font-weight: 500;
}

.el-icon--arrow-down {
  font-size: 12px;
}

.main-content {
  padding: 20px;
  background-color: #f5f7fa;
  overflow-y: auto;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
}

:deep(.el-dropdown-menu__item .el-icon) {
  margin-right: 4px;
}
</style>
