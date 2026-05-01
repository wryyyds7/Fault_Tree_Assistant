<template>
  <el-drawer
    v-model="visible"
    title="溯源信息"
    direction="rtl"
    size="600px"
    :before-close="handleClose"
  >
    <div v-if="nodeData" class="source-detail">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="事件名称">
          {{ nodeData.eventName }}
        </el-descriptions-item>
        <el-descriptions-item label="事件ID">
          {{ nodeData.eventId }}
        </el-descriptions-item>
        <el-descriptions-item label="事件类型">
          <el-tag :type="getEventTypeTagType(nodeData.eventType)">
            {{ getEventTypeText(nodeData.eventType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="逻辑门类型" v-if="nodeData.gateType">
          <el-tag type="info">{{ nodeData.gateType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AI生成">
          <el-tag :type="nodeData.aiGenerated ? 'primary' : 'info'">
            {{ nodeData.aiGenerated ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="置信度" v-if="nodeData.confidence !== undefined && nodeData.confidence !== null">
          <el-progress
            :percentage="(nodeData.confidence * 100).toFixed(1)"
            :color="getConfidenceColor(nodeData.confidence)"
            :stroke-width="10"
          />
        </el-descriptions-item>
        <el-descriptions-item label="验证状态">
          <el-tag :type="getVerificationTagType(nodeData.verificationStatus)">
            {{ getVerificationText(nodeData.verificationStatus) }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">来源信息</el-divider>

      <div v-if="nodeData.sourceDetail" class="source-info">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="来源类型">
            <el-tag :type="getSourceTypeTagType(nodeData.sourceDetail.sourceType)">
              {{ getSourceTypeText(nodeData.sourceDetail.sourceType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="文档名称" v-if="nodeData.sourceDetail.documentName">
            {{ nodeData.sourceDetail.documentName }}
          </el-descriptions-item>
          <el-descriptions-item label="手册名称" v-if="nodeData.sourceDetail.manualName">
            {{ nodeData.sourceDetail.manualName }}
          </el-descriptions-item>
          <el-descriptions-item label="工单编号" v-if="nodeData.sourceDetail.workOrderNumber">
            {{ nodeData.sourceDetail.workOrderNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="页码" v-if="nodeData.sourceDetail.pageNumber">
            {{ nodeData.sourceDetail.pageNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="段落ID" v-if="nodeData.sourceDetail.paragraphId">
            {{ nodeData.sourceDetail.paragraphId }}
          </el-descriptions-item>
          <el-descriptions-item label="来源ID" v-if="nodeData.sourceDetail.sourceId">
            {{ nodeData.sourceDetail.sourceId }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">段落上下文</el-divider>

        <div class="context-controls">
          <span class="context-label">显示上下文段落数：</span>
          <el-input-number
            v-model="contextBefore"
            :min="0"
            :max="10"
            size="small"
            label="前向段落"
          />
          <span class="context-separator">→</span>
          <el-input-number
            v-model="contextAfter"
            :min="0"
            :max="10"
            size="small"
            label="后向段落"
          />
          <el-button
            type="primary"
            size="small"
            @click="fetchContext"
            :loading="loadingContext"
            style="margin-left: 12px"
          >
            刷新上下文
          </el-button>
        </div>

        <div v-if="contextData && contextData.found" class="context-display">
          <div
            v-for="paragraph in contextData.contextParagraphs"
            :key="paragraph.paragraphId"
            class="context-paragraph"
            :class="{ 'target-paragraph': paragraph.isTarget }"
          >
            <div class="paragraph-header">
              <span class="paragraph-position">
                {{ paragraph.position > 0 ? `+${paragraph.position}` : paragraph.position }}
              </span>
              <span v-if="paragraph.isTarget" class="target-badge">目标段落</span>
              <span class="chunk-index">Chunk #{paragraph.chunkIndex}</span>
            </div>
            <div class="paragraph-content" v-html="highlightContent(paragraph.content)"></div>
          </div>

          <div class="context-info">
            <el-tag type="info" size="small">
              文档共 {{ contextData.totalParagraphsInDoc }} 个段落，当前位于第 {{ contextData.targetPositionInDoc + 1 }} 段
            </el-tag>
          </div>
        </div>

        <div v-else-if="contextData && !contextData.found" class="context-error">
          <el-alert
            type="warning"
            :title="contextData.error || '未找到段落上下文'"
            :closable="false"
            show-icon
          />
        </div>

        <div v-else-if="!nodeData.sourceDetail.paragraphId" class="context-empty">
          <el-empty description="无段落ID，无法获取上下文" image-size="80" />
        </div>
      </div>

      <el-empty v-else description="暂无溯源信息" />
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ragAPI } from '@/api'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  nodeData: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const originalContent = ref('')
const loadingContent = ref(false)
const contextData = ref(null)
const loadingContext = ref(false)
const contextBefore = ref(2)
const contextAfter = ref(2)

const highlightedContent = computed(() => {
  if (!originalContent.value) return ''
  const content = originalContent.value
  const keywords = extractKeywords(props.nodeData?.eventName || '')
  let result = escapeHtml(content)
  for (const keyword of keywords) {
    if (keyword.length > 1) {
      const regex = new RegExp(`(${escapeRegex(keyword)})`, 'gi')
      result = result.replace(regex, '<mark class="highlight">$1</mark>')
    }
  }
  return result
})

function escapeHtml(text) {
  const div = document.createElement('div')
  div.textContent = text
  return div.innerHTML
}

function escapeRegex(string) {
  return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function extractKeywords(text) {
  if (!text) return []
  return text.split(/[,，、\s]+/).filter(k => k.length > 1)
}

function highlightContent(content) {
  if (!content) return ''
  const keywords = extractKeywords(props.nodeData?.eventName || '')
  let result = escapeHtml(content)
  for (const keyword of keywords) {
    if (keyword.length > 1) {
      const regex = new RegExp(`(${escapeRegex(keyword)})`, 'gi')
      result = result.replace(regex, '<mark class="highlight">$1</mark>')
    }
  }
  return result
}

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
  if (val) {
    // 打开抽屉时自动获取上下文
    if (props.nodeData?.sourceDetail?.paragraphId) {
      fetchContext()
    }
  }
})

watch(() => props.nodeData, () => {
  originalContent.value = ''
  contextData.value = null
})

const handleClose = () => {
  emit('update:modelValue', false)
}

const getEventTypeText = (type) => {
  const map = { TOP: '顶事件', INTERMEDIATE: '中间事件', BASIC: '底事件' }
  return map[type] || type
}

const getEventTypeTagType = (type) => {
  const map = { TOP: 'primary', INTERMEDIATE: 'success', BASIC: 'warning' }
  return map[type] || 'info'
}

const getSourceTypeText = (type) => {
  const map = {
    MANUAL: '设备手册',
    WORK_ORDER: '维修工单',
    AI_GENERATED: 'AI生成',
    KNOWLEDGE_BASE: '知识库',
    USER_INPUT: '用户输入'
  }
  return map[type] || type
}

const getSourceTypeTagType = (type) => {
  const map = {
    MANUAL: 'primary',
    WORK_ORDER: 'success',
    AI_GENERATED: 'warning',
    KNOWLEDGE_BASE: 'info',
    USER_INPUT: ''
  }
  return map[type] || 'info'
}

const getVerificationText = (status) => {
  const map = { PENDING: '待确认', CONFIRMED: '已确认', REJECTED: '已驳回' }
  return map[status] || status
}

const getVerificationTagType = (status) => {
  const map = { PENDING: 'warning', CONFIRMED: 'success', REJECTED: 'danger' }
  return map[status] || 'info'
}

const getConfidenceColor = (confidence) => {
  if (confidence >= 0.8) return '#67c23a'
  if (confidence >= 0.6) return '#e6a23c'
  return '#f56c6c'
}

const fetchOriginalContent = async () => {
  if (!props.nodeData?.sourceDetail?.paragraphId) {
    ElMessage.warning('无可用段落ID')
    return
  }

  loadingContent.value = true
  try {
    const response = await ragAPI.getEvidence(props.nodeData.sourceDetail.paragraphId)
    if (response) {
      originalContent.value = response.content || response.paragraphContent || '暂无原始内容'
    } else {
      originalContent.value = '暂无原始内容'
    }
  } catch (error) {
    console.error('获取原始依据失败:', error)
    ElMessage.error('获取原始依据失败')
    originalContent.value = '获取失败'
  } finally {
    loadingContent.value = false
  }
}

const fetchContext = async () => {
  if (!props.nodeData?.sourceDetail?.paragraphId) {
    ElMessage.warning('无可用段落ID')
    return
  }

  loadingContext.value = true
  try {
    const userId = localStorage.getItem('userId') || 'default'
    const response = await ragAPI.getParagraphWithContext(
      props.nodeData.sourceDetail.paragraphId,
      userId,
      contextBefore.value,
      contextAfter.value
    )
    
    if (response) {
      contextData.value = response
    } else {
      contextData.value = { found: false, error: '未获取到数据' }
    }
  } catch (error) {
    console.error('获取段落上下文失败:', error)
    ElMessage.error('获取段落上下文失败')
    contextData.value = { found: false, error: '获取失败' }
  } finally {
    loadingContext.value = false
  }
}
</script>

<style scoped>
.source-detail {
  padding: 0 10px;
}

.original-content {
  margin-top: 10px;
}

.content-text {
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}

.content-text :deep(.highlight) {
  background-color: #fff3cd;
  color: #856404;
  padding: 2px 4px;
  border-radius: 3px;
  font-weight: 500;
}

.context-controls {
  display: flex;
  align-items: center;
  margin: 16px 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.context-label {
  font-size: 13px;
  color: #606266;
  margin-right: 12px;
}

.context-separator {
  margin: 0 8px;
  color: #909399;
  font-weight: 500;
}

.context-display {
  margin-top: 16px;
}

.context-paragraph {
  margin-bottom: 16px;
  padding: 16px;
  border-radius: 8px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  transition: all 0.3s ease;
}

.context-paragraph:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.context-paragraph.target-paragraph {
  background: linear-gradient(135deg, #fff7e6 0%, #ffe7ba 100%);
  border-color: #ffc069;
  box-shadow: 0 0 0 2px rgba(255, 192, 105, 0.2);
}

.paragraph-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.paragraph-position {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e6f7ff;
  color: #1890ff;
  font-weight: 600;
  font-size: 12px;
}

.target-paragraph .paragraph-position {
  background: #fff7e6;
  color: #fa8c16;
}

.target-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  background: #ffc069;
  color: #874d00;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
}

.chunk-index {
  margin-left: auto;
  font-size: 11px;
  color: #909399;
  font-family: monospace;
}

.paragraph-content {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.paragraph-content :deep(.highlight) {
  background-color: #fff3cd;
  color: #856404;
  padding: 2px 4px;
  border-radius: 3px;
  font-weight: 500;
}

.context-info {
  text-align: center;
  margin-top: 16px;
  padding: 12px;
  background: #f0f9ff;
  border-radius: 6px;
}

.context-error {
  margin-top: 16px;
}

.context-empty {
  margin-top: 24px;
}
</style>
