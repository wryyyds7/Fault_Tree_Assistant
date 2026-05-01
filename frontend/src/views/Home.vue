<template>
  <div class="home-container" aria-label="首页">
    <el-card class="welcome-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="welcome-title">
            <el-icon class="title-icon" aria-label="数据分析"><DataAnalysis /></el-icon>
            <span>欢迎使用工业设备故障树智能生成系统</span>
          </div>
          <div class="header-stats">
            <div class="stat-item">
              <span class="stat-value">{{ stats.documentCount }}</span>
              <span class="stat-label">文档</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.treeCount }}</span>
              <span class="stat-label">故障树</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.nodeCount }}</span>
              <span class="stat-label">节点</span>
            </div>
          </div>
        </div>
      </template>

      <div class="welcome-content">
        <p class="intro-text">
          本系统基于知识图谱和RAG技术，实现工业设备故障树的智能生成与辅助构建，提供可视化的故障树编辑、溯源分析和协同修正功能。
        </p>

        <el-divider content-position="left">
          <span class="divider-title">核心功能</span>
        </el-divider>

        <el-row :gutter="24" class="feature-row">
          <el-col :xs="24" :sm="12" :md="6">
            <div class="feature-card" @click="navigateTo('/document')" tabindex="0" aria-label="文档管理">
              <div class="feature-icon-wrapper feature-blue">
                <el-icon class="feature-icon"><Document /></el-icon>
              </div>
              <div class="feature-info">
                <h3>文档管理</h3>
                <p>支持PDF、DOCX、TXT、Excel、CSV等格式文档上传和解析</p>
                <div class="feature-tag">
                  <el-tag size="small" type="info">上传解析</el-tag>
                </div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="feature-card" @click="navigateTo('/fault-tree')" tabindex="0" aria-label="故障树管理">
              <div class="feature-icon-wrapper feature-green">
                <el-icon class="feature-icon"><Share /></el-icon>
              </div>
              <div class="feature-info">
                <h3>故障树管理</h3>
                <p>智能生成故障树，支持可视化编辑和协同工作</p>
                <div class="feature-tag">
                  <el-tag size="small" type="success">编辑管理</el-tag>
                </div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="feature-card" @click="navigateTo('/knowledge-graph')" tabindex="0" aria-label="知识图谱">
              <div class="feature-icon-wrapper feature-yellow">
                <el-icon class="feature-icon"><Connection /></el-icon>
              </div>
              <div class="feature-info">
                <h3>知识图谱</h3>
                <p>基于Neo4j构建设备故障知识图谱，支持知识查询</p>
                <div class="feature-tag">
                  <el-tag size="small" type="warning">知识查询</el-tag>
                </div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="feature-card" @click="navigateTo('/feedback')" tabindex="0" aria-label="反馈管理">
              <div class="feature-icon-wrapper feature-red">
                <el-icon class="feature-icon"><ChatDotRound /></el-icon>
              </div>
              <div class="feature-info">
                <h3>反馈管理</h3>
                <p>用户反馈收集与分析，持续优化AI生成结果</p>
                <div class="feature-tag">
                  <el-tag size="small" type="danger">反馈优化</el-tag>
                </div>
              </div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="feature-card" @click="navigateTo('/ai-assistant')" tabindex="0" aria-label="AI助手">
              <div class="feature-icon-wrapper feature-purple">
                <el-icon class="feature-icon"><ChatLineSquare /></el-icon>
              </div>
              <div class="feature-info">
                <h3>AI助手</h3>
                <p>智能问答助手，可关联故障树和文档进行专业咨询</p>
                <div class="feature-tag">
                  <el-tag size="small" type="primary">智能问答</el-tag>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>

        <el-divider content-position="left">
          <span class="divider-title">快速开始</span>
        </el-divider>

        <el-steps :active="currentStep" align-center class="quick-start-steps">
          <el-step title="上传文档" description="上传设备维修手册、工单记录等">
            <template #icon>
              <div class="step-icon">1</div>
            </template>
          </el-step>
          <el-step title="智能生成" description="指定顶事件，AI自动生成故障树">
            <template #icon>
              <div class="step-icon">2</div>
            </template>
          </el-step>
          <el-step title="专家修正" description="验证节点，修正逻辑冲突">
            <template #icon>
              <div class="step-icon">3</div>
            </template>
          </el-step>
          <el-step title="分析导出" description="完成分析，导出结果报告">
            <template #icon>
              <div class="step-icon">4</div>
            </template>
          </el-step>
        </el-steps>

        <div class="action-buttons">
          <el-button type="primary" size="large" @click="navigateTo('/document')">
            <el-icon><Upload /></el-icon>
            上传文档开始
          </el-button>
          <el-button type="success" size="large" @click="navigateTo('/fault-tree')">
            <el-icon><Share /></el-icon>
            查看故障树
          </el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card class="recent-card">
          <template #header>
            <div class="card-header">
              <span>最近故障树</span>
              <el-button type="primary" link @click="navigateTo('/fault-tree')">查看全部</el-button>
            </div>
          </template>
          <div v-if="recentTrees.length > 0" class="recent-list">
            <div
              v-for="tree in recentTrees"
              :key="tree.treeId"
              class="recent-item"
              @click="editTree(tree.treeId)"
            >
              <div class="recent-info">
                <span class="recent-name">{{ tree.name }}</span>
                <el-tag size="small" :type="getStatusType(tree.status)">
                  {{ getStatusText(tree.status) }}
                </el-tag>
              </div>
              <div class="recent-meta">
                <span class="recent-time">{{ formatTime(tree.updatedAt) }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无故障树" />
        </el-card>
      </el-col>

      
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Document,
  Share,
  Connection,
  ChatDotRound,
  DataAnalysis,
  Upload,
  Bell,
  ChatLineSquare
} from '@element-plus/icons-vue'
import { faultTreeAPI, documentAPI } from '@/api'

const router = useRouter()

const currentStep = ref(0)
const stats = ref({
  documentCount: 0,
  treeCount: 0,
  nodeCount: 0
})

const recentTrees = ref([])
const loadingStats = ref(false)
const loadingRecent = ref(false)

const navigateTo = (path) => {
  router.push(path)
}

const editTree = (treeId) => {
  router.push(`/fault-tree/edit/${treeId}`)
}

const getStatusType = (status) => {
  const map = {
    DRAFT: 'info',
    PUBLISHED: 'success',
    ARCHIVED: 'warning'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档'
  }
  return map[status] || status
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) {
    return '今天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days === 1) {
    return '昨天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } else if (days < 7) {
    return `${days}天前`
  } else {
    return date.toLocaleDateString('zh-CN')
  }
}

const loadStats = async () => {
  loadingStats.value = true
  try {
    const [treeResponse, docResponse] = await Promise.all([
      faultTreeAPI.getAll({ limit: 100 }),
      documentAPI.getDocumentList().catch(() => [])
    ])

    let trees = []
    if (treeResponse && Array.isArray(treeResponse)) {
      trees = treeResponse
    } else if (treeResponse && treeResponse.data && Array.isArray(treeResponse.data)) {
      trees = treeResponse.data
    }

    let docs = []
    if (docResponse && Array.isArray(docResponse)) {
      docs = docResponse
    } else if (docResponse && docResponse.data && Array.isArray(docResponse.data)) {
      docs = docResponse.data
    }

    const nodeCount = trees.reduce((count, tree) => {
      const countNodes = (node) => {
        if (!node) return 0
        return 1 + (node.children ? node.children.reduce((c, n) => c + countNodes(n), 0) : 0)
      }
      return count + countNodes(tree.treeData || tree.rootEvent)
    }, 0)

    stats.value = {
      documentCount: docs.length,
      treeCount: trees.length,
      nodeCount: nodeCount
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    loadingStats.value = false
  }
}

const loadRecentTrees = async () => {
  loadingRecent.value = true
  try {
    const response = await faultTreeAPI.getAll({ limit: 3, sortBy: 'updatedAt', order: 'desc' })
    if (response && Array.isArray(response)) {
      recentTrees.value = response
    } else if (response && response.data && Array.isArray(response.data)) {
      recentTrees.value = response.data
    } else {
      recentTrees.value = [
        {
          treeId: 'tree_001',
          name: '电机过热故障树',
          status: 'DRAFT',
          updatedAt: '2026-03-20T14:30:00'
        },
        {
          treeId: 'tree_002',
          name: '液压泵压力不足',
          status: 'PUBLISHED',
          updatedAt: '2026-03-19T10:20:00'
        },
        {
          treeId: 'tree_003',
          name: '控制系统异常',
          status: 'DRAFT',
          updatedAt: '2026-03-18T16:45:00'
        }
      ]
    }
  } catch (error) {
    console.error('加载最近故障树失败:', error)
  } finally {
    loadingRecent.value = false
  }
}

onMounted(() => {
  const step = Math.floor(Math.random() * 4)
  currentStep.value = step
  loadStats()
  loadRecentTrees()
})
</script>

<style scoped>
.home-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 120px);
}

.welcome-card {
  margin-bottom: 20px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.welcome-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.title-icon {
  font-size: 24px;
  color: #409eff;
}

.header-stats {
  display: flex;
  gap: 30px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 16px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e8f4fd 100%);
  border-radius: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #409eff;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.welcome-content {
  padding: 20px 0;
}

.intro-text {
  font-size: 15px;
  color: #606266;
  line-height: 1.8;
  margin-bottom: 30px;
  padding: 16px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e8f4fd 100%);
  border-radius: 8px;
  border-left: 4px solid #409eff;
}

.divider-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}


.feature-row {
  margin-bottom: 16px;
}
.feature-card {
  background: #fff;
  border-radius: 14px;
  padding: 22px 18px 18px 18px;
  cursor: pointer;
  transition: box-shadow 0.25s ease, border-color 0.25s ease, transform 0.25s ease;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 15px;
  border: 1px solid #e4e7ed;
  outline: none;
  position: relative;
  overflow: hidden;
}
.feature-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, transparent, var(--accent-color, #409eff), transparent);
  opacity: 0;
  transition: opacity 0.25s ease;
}
.feature-card:focus {
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
  border-color: #2563eb;
}
.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 28px rgba(30, 41, 59, 0.12);
  border-color: #c7d2fe;
}
.feature-card:hover::before {
  opacity: 1;
}
.feature-icon-wrapper {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(30, 41, 59, 0.15);
  margin-bottom: 6px;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.feature-card:hover .feature-icon-wrapper {
  transform: scale(1.08);
  box-shadow: 0 6px 16px rgba(30, 41, 59, 0.2);
}
.feature-blue {
  background: linear-gradient(135deg, #3b82f6 0%, #1d4ed8 100%);
  --accent-color: #3b82f6;
}
.feature-green {
  background: linear-gradient(135deg, #10b981 0%, #047857 100%);
  --accent-color: #10b981;
}
.feature-yellow {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  --accent-color: #f59e0b;
}
.feature-red {
  background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%);
  --accent-color: #ef4444;
}
.feature-purple {
  background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
  --accent-color: #8b5cf6;
}
.feature-icon {
  font-size: 26px;
  color: #fff;
}

.feature-info h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  color: #303133;
}

.feature-info p {
  margin: 0 0 12px 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.feature-tag {
  margin-top: auto;
}

.quick-start-steps {
  margin: 30px 0;
}

.step-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.4);
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 30px;
}

.action-buttons .el-button {
  padding: 16px 32px;
  font-size: 15px;
}

.action-buttons .el-icon {
  margin-right: 8px;
}

.recent-card {
  margin-bottom: 20px;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border: none;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recent-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  background-color: #fafbfc;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s ease;
  border: 1px solid transparent;
}

.recent-item:hover {
  background-color: #f0f7ff;
  transform: translateX(6px);
  border-color: #dbeafe;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.1);
}

.recent-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.recent-name {
  font-size: 14px;
  color: #1e293b;
  font-weight: 500;
  transition: color 0.2s ease;
}

.recent-item:hover .recent-name {
  color: #2563eb;
}

.recent-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.recent-time {
  font-size: 12px;
  color: #94a3b8;
}

.announcement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.announcement-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  transition: all 0.3s;
}

.announcement-item:hover {
  background: #ecf5ff;
}

.announcement-icon {
  font-size: 20px;
  color: #e6a23c;
  flex-shrink: 0;
}

.announcement-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.announcement-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.announcement-text {
  font-size: 13px;
  color: #606266;
}

.announcement-time {
  font-size: 12px;
  color: #909399;
}
</style>
