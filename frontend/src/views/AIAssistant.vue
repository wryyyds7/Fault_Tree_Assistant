<template>
  <div class="ai-assistant-container">
    <el-card class="assistant-card">
      <template #header>
        <div class="assistant-header">
          <div class="header-left">
            <el-icon class="header-icon"><ChatDotRound /></el-icon>
            <span>AI 故障树助手</span>
          </div>
          <div class="header-actions">
            <el-button type="primary" size="small" @click="showNewChatDialog = true">
              <el-icon><Plus /></el-icon>
              新对话
            </el-button>
          </div>
        </div>
      </template>

      <div class="assistant-layout">
        <div class="sidebar" v-if="showSidebar">
          <div class="sidebar-header">
            <span>对话历史</span>
            <el-button type="text" @click="loadSessions">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
          <div class="session-list">
            <div
              v-for="session in sessions"
              :key="session.sessionId"
              class="session-item"
              :class="{ active: currentSession?.sessionId === session.sessionId }"
              @click="selectSession(session)"
            >
              <div class="session-title">{{ session.title }}</div>
              <div class="session-time">{{ formatTime(session.updatedAt) }}</div>
            </div>
            <div v-if="sessions.length === 0" class="empty-sessions">
              暂无对话记录
            </div>
          </div>
        </div>

        <div class="chat-area">
          <div class="chat-toolbar">
            <el-button
              type="text"
              @click="showSidebar = !showSidebar"
              class="toggle-sidebar"
            >
              <el-icon><Fold v-if="showSidebar" /><Expand v-else /></el-icon>
            </el-button>
            <el-button
              type="primary"
              plain
              size="small"
              @click="showLinkDialog = true"
              :disabled="!currentSession"
            >
              <el-icon><Link /></el-icon>
              关联故障树/文档
            </el-button>
            <el-button
              :type="deepThinking ? 'warning' : 'info'"
              plain
              size="small"
              @click="deepThinking = !deepThinking"
            >
              <el-icon><Sunny /></el-icon>
              深度思考
            </el-button>
            <el-dropdown @command="handleExport">
              <el-button type="text" size="small">
                <el-icon><Download /></el-icon>
                导出
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="markdown">导出为 Markdown</el-dropdown-item>
                  <el-dropdown-item command="json">导出为 JSON</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <div class="chat-main">
            <div class="messages-wrapper" ref="messagesWrapper">
              <div class="messages-container" ref="messagesContainer">
                <div v-if="!currentSession" class="welcome-state">
                  <div class="welcome-icon">
                    <el-icon><ChatLineSquare /></el-icon>
                  </div>
                  <h3>欢迎使用 AI 故障树助手</h3>
                  <p>我可以帮助您：</p>
                  <ul>
                    <li>解答故障树构建和分析的问题</li>
                    <li>基于您的故障树数据给出专业建议</li>
                    <li>解释 AND、OR、XOR 等逻辑门的含义</li>
                    <li>帮助您理解和改进故障树结构</li>
                  </ul>
                  <el-button type="primary" @click="showNewChatDialog = true">
                    开始新对话
                  </el-button>
                </div>

                <div v-else-if="!currentSession.messages || currentSession.messages.length === 0" class="empty-messages">
                  <div class="empty-icon">
                    <el-icon><ChatDotRound /></el-icon>
                  </div>
                  <h4>暂无对话历史</h4>
                  <p>在下方输入您的问题，开始与 AI 对话吧</p>
                </div>

                <div v-else class="message-list">
                  <div
                    v-for="(msg, index) in currentSession.messages"
                    :key="index"
                    class="message"
                    :class="msg.role"
                  >
                    <div class="message-avatar">
                      <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                      <el-icon v-else><Service /></el-icon>
                    </div>
                    <div class="message-content">
                      <div class="message-text" v-html="formatMessage(msg.content)"></div>
                      <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
                    </div>
                  </div>

                  <div v-if="loading" class="message assistant">
                    <div class="message-avatar">
                      <el-icon><Service /></el-icon>
                    </div>
                    <div class="message-content">
                      <div class="message-text loading">
                        <span class="loading-dot"></span>
                        <span class="loading-dot"></span>
                        <span class="loading-dot"></span>
                        AI 正在思考...
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="resize-handle" @mousedown="startResizeInput"></div>

            <div class="input-area" ref="inputArea" :style="{ height: inputHeight + 'px', minHeight: minInputHeight + 'px', maxHeight: maxInputHeight + 'px' }">
              <div class="pending-questions" v-if="pendingQuestions.length > 0">
                <div class="pending-header">
                  <span>待发送问题 ({{ pendingQuestions.length }})</span>
                  <el-button type="text" size="small" @click="clearPendingQuestions">清空</el-button>
                </div>
                <div class="pending-list">
                  <div
                    v-for="(q, index) in pendingQuestions"
                    :key="index"
                    class="pending-item"
                  >
                    <span class="pending-index">{{ index + 1 }}</span>
                    <span class="pending-text">{{ q }}</span>
                    <el-button type="text" size="small" @click="removePendingQuestion(index)">
                      <el-icon><Close /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>

              <div class="linked-items" v-if="currentSession && (currentSession.linkedTreeIds?.length || currentSession.linkedDocIds?.length)">
                <el-tag
                  v-for="treeId in currentSession.linkedTreeIds"
                  :key="'tree-' + treeId"
                  closable
                  size="small"
                  @close="unlinkTree(treeId)"
                >
                  <el-icon><Connection /></el-icon>
                  {{ getTreeName(treeId) }}
                </el-tag>
                <el-tag
                  v-for="docId in currentSession.linkedDocIds"
                  :key="'doc-' + docId"
                  closable
                  size="small"
                  type="success"
                  @close="unlinkDoc(docId)"
                >
                  <el-icon><Document /></el-icon>
                  {{ getDocName(docId) }}
                </el-tag>
              </div>
              <div class="input-wrapper">
                <el-input
                  v-model="inputMessage"
                  type="textarea"
                  :rows="inputRows"
                  placeholder="输入您的问题..."
                  @keydown.enter.ctrl="handleEnterKey"
                  @input="adjustInputRows"
                  :disabled="!currentSession || loading"
                />
                <el-button
                  type="primary"
                  @click="sendMessage"
                  :loading="loading"
                  :disabled="!inputMessage.trim() || !currentSession"
                >
                  <el-icon><Promotion /></el-icon>
                  发送
                </el-button>
              </div>
              <div class="input-hint">
                按 Ctrl+Enter 发送
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="showNewChatDialog" title="开始新对话" width="500px">
      <el-form :model="newChatForm" label-width="100px">
        <el-form-item label="对话标题">
          <el-input v-model="newChatForm.title" placeholder="留空将使用默认标题" />
        </el-form-item>
        <el-form-item label="关联故障树">
          <el-select
            v-model="newChatForm.linkedTreeIds"
            multiple
            placeholder="选择要关联的故障树"
            style="width: 100%"
            clearable
          >
            <el-option
              v-for="tree in availableTrees"
              :key="tree.treeId"
              :label="tree.name"
              :value="tree.treeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联文档">
          <el-select
            v-model="newChatForm.linkedDocIds"
            multiple
            placeholder="选择要关联的文档"
            style="width: 100%"
            clearable
          >
            <el-option
              v-for="doc in availableDocs"
              :key="doc.documentId"
              :label="doc.fileName"
              :value="doc.documentId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewChatDialog = false">取消</el-button>
        <el-button type="primary" @click="createNewSession">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showLinkDialog" title="关联故障树/文档" width="600px">
      <el-tabs>
        <el-tab-pane label="故障树">
          <div class="link-list">
            <div
              v-for="tree in availableTrees"
              :key="tree.treeId"
              class="link-item"
              :class="{ linked: currentSession?.linkedTreeIds?.includes(tree.treeId) }"
              @click="toggleTreeLink(tree.treeId)"
            >
              <el-icon v-if="currentSession?.linkedTreeIds?.includes(tree.treeId)"><Check /></el-icon>
              <div class="link-info">
                <div class="link-name">{{ tree.name }}</div>
                <div class="link-meta">{{ tree.equipmentType }}</div>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="文档">
          <div class="link-list">
            <div
              v-for="doc in availableDocs"
              :key="doc.documentId"
              class="link-item"
              :class="{ linked: currentSession?.linkedDocIds?.includes(doc.documentId) }"
              @click="toggleDocLink(doc.documentId)"
            >
              <el-icon v-if="currentSession?.linkedDocIds?.includes(doc.documentId)"><Check /></el-icon>
              <div class="link-info">
                <div class="link-name">{{ doc.fileName }}</div>
                <div class="link-meta">{{ doc.sourceType }}</div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ChatDotRound, Plus, Refresh, Fold, Expand, Link,
  Download, ChatLineSquare, User, Service, Promotion,
  Document, Connection, Check, Sunny, Close
} from '@element-plus/icons-vue'
import { chatAPI, faultTreeAPI, documentAPI } from '@/api'

const showSidebar = ref(true)
const showNewChatDialog = ref(false)
const showLinkDialog = ref(false)
const loading = ref(false)
const deepThinking = ref(false)
const inputMessage = ref('')
const inputRows = ref(2)
const messagesContainer = ref(null)
const inputArea = ref(null)
const sessions = ref([])
const currentSession = ref(null)
const availableTrees = ref([])
const availableDocs = ref([])
const pendingQuestions = ref([])
const inputResizing = ref(false)
const inputHeight = ref(120)
const minInputHeight = 80
const maxInputHeight = 400

const newChatForm = reactive({
  title: '',
  linkedTreeIds: [],
  linkedDocIds: []
})

const formatTime = (timestamp) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)} 天前`

  return date.toLocaleDateString('zh-CN')
}

const formatMessage = (content) => {
  if (!content) return ''
  return content
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
}

const loadSessions = async () => {
  const userId = localStorage.getItem('userId') || 'anonymous'
  try {
    const response = await chatAPI.getUserSessions(userId)
    sessions.value = response || []
  } catch (error) {
    console.error('加载会话列表失败:', error)
    // 错误时设置为空数组，不显示错误提示
    sessions.value = []
  }
}

const loadUserResources = async () => {
  const userId = localStorage.getItem('userId') || 'anonymous'
  try {
    const [treesRes, docsRes] = await Promise.all([
      faultTreeAPI.getAll({ createdBy: userId }),
      documentAPI.getDocumentList()
    ])
    availableTrees.value = treesRes || []
    availableDocs.value = docsRes || []
  } catch (error) {
    console.error('加载用户资源失败:', error)
  }
}

const selectSession = async (session) => {
  try {
    const fullSession = await chatAPI.getSession(session.sessionId)
    currentSession.value = fullSession
    await nextTick()
    scrollToBottom()
  } catch (error) {
    console.error('加载会话详情失败:', error)
    currentSession.value = session
  }
}

const createNewSession = async () => {
  const userId = localStorage.getItem('userId') || 'anonymous'
  try {
    const session = await chatAPI.createSession({
      userId,
      title: newChatForm.title || undefined,
      linkedTreeIds: newChatForm.linkedTreeIds,
      linkedDocIds: newChatForm.linkedDocIds
    })
    sessions.value.unshift(session)
    currentSession.value = session
    showNewChatDialog.value = false
    newChatForm.title = ''
    newChatForm.linkedTreeIds = []
    newChatForm.linkedDocIds = []
    ElMessage.success('对话已创建')
  } catch (error) {
    console.error('创建会话失败:', error)
    ElMessage.error('创建失败')
  }
}

const sendMessage = async (messageToSend = null) => {
  const message = messageToSend || inputMessage.value.trim()
  if (!message || !currentSession.value || loading.value) return

  if (!messageToSend) {
    inputMessage.value = ''
  }
  loading.value = true

  const userMsg = { role: 'user', content: message }
  if (!currentSession.value.messages) {
    currentSession.value.messages = []
  }
  currentSession.value.messages.push(userMsg)

  let assistantMsg = { role: 'assistant', content: '' }
  currentSession.value.messages.push(assistantMsg)

  await nextTick()
  scrollToBottom()

  const userId = localStorage.getItem('userId') || 'anonymous'

  try {
    for await (const chunk of chatAPI.chatStream({
      sessionId: currentSession.value.sessionId,
      userId,
      message,
      linkedTreeIds: currentSession.value.linkedTreeIds || [],
      linkedDocIds: currentSession.value.linkedDocIds || []
    })) {
      if (chunk.content) {
        assistantMsg.content += chunk.content
        await nextTick()
        scrollToBottom()
      }
      if (chunk.tree) {
        if (!currentSession.value.relatedTrees) {
          currentSession.value.relatedTrees = []
        }
        if (!currentSession.value.relatedTrees.find(t => t.treeId === chunk.tree.treeId)) {
          currentSession.value.relatedTrees.push(chunk.tree)
        }
      }
      if (chunk.doc) {
        if (!currentSession.value.relatedDocs) {
          currentSession.value.relatedDocs = []
        }
        if (!currentSession.value.relatedDocs.find(d => d.documentId === chunk.doc.documentId)) {
          currentSession.value.relatedDocs.push(chunk.doc)
        }
      }
      if (chunk.done) {
        break
      }
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    assistantMsg.content = '发送失败，请稍后重试'
    ElMessage.error('发送失败，请稍后重试')
  } finally {
    loading.value = false
    if (pendingQuestions.value.length > 0) {
      const nextQuestion = pendingQuestions.value.shift()
      await sendMessage(nextQuestion)
    }
  }
}

const adjustInputRows = () => {
  const lines = inputMessage.value.split('\n').length
  inputRows.value = Math.min(Math.max(lines, 2), 8)
}

const handleEnterKey = (e) => {
  if (inputMessage.value.trim()) {
    if (loading.value) {
      pendingQuestions.value.push(inputMessage.value.trim())
      inputMessage.value = ''
      ElMessage.info('问题已加入待发送队列')
    } else {
      sendMessage()
    }
  }
}

const removePendingQuestion = (index) => {
  pendingQuestions.value.splice(index, 1)
}

const clearPendingQuestions = () => {
  pendingQuestions.value = []
  inputMessage.value = ''
}

const startResizeInput = (e) => {
  inputResizing.value = true
  const startY = e.clientY
  const startHeight = inputHeight.value

  const handleMouseMove = (moveEvent) => {
    const delta = startY - moveEvent.clientY
    const newHeight = Math.min(Math.max(startHeight + delta, minInputHeight), maxInputHeight)
    inputHeight.value = newHeight
  }

  const handleMouseUp = () => {
    inputResizing.value = false
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
  }

  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}

const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const getTreeName = (treeId) => {
  const tree = availableTrees.value.find(t => t.treeId === treeId)
  return tree ? tree.name : treeId.substring(0, 8)
}

const getDocName = (docId) => {
  const doc = availableDocs.value.find(d => d.documentId === docId)
  return doc ? doc.fileName : docId.substring(0, 8)
}

const toggleTreeLink = async (treeId) => {
  if (!currentSession.value) return

  const isLinked = currentSession.value.linkedTreeIds?.includes(treeId)
  try {
    if (isLinked) {
      currentSession.value.linkedTreeIds = currentSession.value.linkedTreeIds.filter(id => id !== treeId)
      await chatAPI.unlinkTree(currentSession.value.sessionId, treeId)
    } else {
      await chatAPI.linkTree(currentSession.value.sessionId, treeId)
      currentSession.value.linkedTreeIds.push(treeId)
    }
  } catch (error) {
    console.error('关联故障树失败:', error)
    ElMessage.error('操作失败')
  }
}

const toggleDocLink = async (docId) => {
  if (!currentSession.value) return

  const isLinked = currentSession.value.linkedDocIds?.includes(docId)
  try {
    if (isLinked) {
      currentSession.value.linkedDocIds = currentSession.value.linkedDocIds.filter(id => id !== docId)
      await chatAPI.unlinkDocument(currentSession.value.sessionId, docId)
    } else {
      await chatAPI.linkDocument(currentSession.value.sessionId, docId)
      currentSession.value.linkedDocIds.push(docId)
    }
  } catch (error) {
    console.error('关联文档失败:', error)
    ElMessage.error('操作失败')
  }
}

const unlinkTree = async (treeId) => {
  if (!currentSession.value) return
  currentSession.value.linkedTreeIds = currentSession.value.linkedTreeIds.filter(id => id !== treeId)
  try {
    await chatAPI.unlinkTree(currentSession.value.sessionId, treeId)
  } catch (error) {
    console.error('取消关联失败:', error)
  }
}

const unlinkDoc = async (docId) => {
  if (!currentSession.value) return
  currentSession.value.linkedDocIds = currentSession.value.linkedDocIds.filter(id => id !== docId)
  try {
    await chatAPI.unlinkDocument(currentSession.value.sessionId, docId)
  } catch (error) {
    console.error('取消关联失败:', error)
  }
}

const handleExport = (format) => {
  if (!currentSession.value) return

  if (format === 'markdown') {
    let md = `# ${currentSession.value.title}\n\n`
    currentSession.value.messages.forEach(msg => {
      const role = msg.role === 'user' ? '用户' : '助手'
      md += `## ${role}\n\n${msg.content}\n\n`
    })
    const blob = new Blob([md], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${currentSession.value.title}.md`
    a.click()
  } else if (format === 'json') {
    const json = JSON.stringify(currentSession.value, null, 2)
    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${currentSession.value.title}.json`
    a.click()
  }
}

onMounted(() => {
  loadSessions()
  loadUserResources()
})
</script>

<style scoped>
.ai-assistant-container {
  height: 100%;
  padding: 20px;
  background-color: #f5f7fa;
}

.assistant-card {
  height: 100%;
  border-radius: 12px;
}

.assistant-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.header-icon {
  font-size: 22px;
  color: #409eff;
}

.assistant-layout {
  display: flex;
  height: calc(100vh - 200px);
  min-height: 500px;
}

.sidebar {
  width: 260px;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e2e8f0;
  font-weight: 600;
  color: #475569;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: all 0.2s;
}

.session-item:hover {
  background: #e2e8f0;
}

.session-item.active {
  background: #dbeafe;
  border-left: 3px solid #409eff;
}

.session-title {
  font-size: 14px;
  color: #1e293b;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 12px;
  color: #94a3b8;
}

.empty-sessions {
  text-align: center;
  padding: 40px 20px;
  color: #94a3b8;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.chat-toolbar {
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  gap: 12px;
  align-items: center;
}

.toggle-sidebar {
  padding: 4px 8px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.chat-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.messages-wrapper {
  flex: 1;
  min-height: 150px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.messages-wrapper .messages-container {
  flex: 1;
  min-height: 0;
}

.resize-handle {
  height: 6px;
  background: #e2e8f0;
  cursor: ns-resize;
  transition: background 0.2s;
}

.resize-handle:hover {
  background: #cbd5e1;
}

.empty-messages {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: #64748b;
  padding: 40px 20px;
}

.empty-icon {
  font-size: 48px;
  color: #cbd5e1;
  margin-bottom: 16px;
}

.empty-messages h4 {
  font-size: 18px;
  color: #64748b;
  margin-bottom: 8px;
}

.empty-messages p {
  font-size: 14px;
  color: #94a3b8;
}

.pending-questions {
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
}

.pending-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  color: #0369a1;
}

.pending-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 120px;
  overflow-y: auto;
}

.pending-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #fff;
  border-radius: 6px;
  font-size: 13px;
}

.pending-index {
  width: 20px;
  height: 20px;
  background: #0ea5e9;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  flex-shrink: 0;
}

.pending-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.welcome-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: #64748b;
}

.welcome-icon {
  font-size: 64px;
  color: #409eff;
  margin-bottom: 20px;
}

.welcome-state h3 {
  font-size: 24px;
  color: #1e293b;
  margin-bottom: 16px;
}

.welcome-state ul {
  text-align: left;
  margin: 20px 0;
  padding-left: 24px;
}

.welcome-state li {
  margin: 8px 0;
  color: #475569;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.message.user {
  flex-direction: row-reverse;
  margin-left: auto;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff;
}

.message.assistant .message-avatar {
  background: linear-gradient(135deg, #67c23a, #95d475);
  color: #fff;
}

.message-content {
  flex: 1;
}

.message-text {
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.message.user .message-text {
  background: linear-gradient(135deg, #409eff, #79bbff);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-text {
  background: #f1f5f9;
  color: #1e293b;
  border-bottom-left-radius: 4px;
}

.message-text code {
  background: rgba(0, 0, 0, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.message.user .message-text code {
  background: rgba(255, 255, 255, 0.2);
}

.message-time {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 4px;
  padding: 0 8px;
}

.message.user .message-time {
  text-align: right;
}

.loading {
  display: flex;
  align-items: center;
  gap: 4px;
}

.loading-dot {
  width: 8px;
  height: 8px;
  background: #409eff;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dot:nth-child(1) { animation-delay: -0.32s; }
.loading-dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.input-area {
  padding: 16px;
  border-top: 1px solid #e2e8f0;
  overflow-y: auto;
  resize: none;
  flex-shrink: 0;
}

.linked-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.linked-items .el-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-wrapper .el-textarea {
  flex: 1;
}

.input-hint {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 8px;
  text-align: right;
}

.link-list {
  max-height: 400px;
  overflow-y: auto;
}

.link-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
}

.link-item:hover {
  background: #f1f5f9;
}

.link-item.linked {
  background: #dbeafe;
}

.link-item .el-icon {
  font-size: 18px;
  color: #409eff;
}

.link-info {
  flex: 1;
}

.link-name {
  font-size: 14px;
  color: #1e293b;
}

.link-meta {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}
</style>