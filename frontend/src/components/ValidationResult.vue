<template>
  <el-dialog
    v-model="visible"
    title="故障树校验结果"
    width="650px"
    :before-close="handleClose"
  >
    <div v-if="validationResult" class="validation-result">
      <el-alert
        :title="validationResult.valid ? '校验通过' : '校验发现问题'"
        :type="validationResult.valid ? 'success' : 'warning'"
        :description="validationResult.valid ? '故障树结构符合FTA规范' : '请根据提示信息修复问题'"
        show-icon
        :closable="false"
        style="margin-bottom: 20px"
      />

      <!-- AI智能分析区域 -->
      <div v-if="validationResult.aiAnalysisCompleted || validationResult.aiSuggestion" class="ai-suggestion-section">
        <el-divider content-position="left">
          <span class="divider-title">
            <el-icon><MagicStick /></el-icon>
            AI智能分析
          </span>
        </el-divider>
        
        <div v-if="validationResult.aiSuggestion" class="ai-suggestion-card">
          <div class="ai-suggestion-header">
            <el-icon color="#409eff"><ChatDotRound /></el-icon>
            <span>专业建议</span>
          </div>
          <div class="ai-suggestion-content">
            <p>{{ formatAiSuggestion(validationResult.aiSuggestion) }}</p>
          </div>
        </div>
        
        <div v-else-if="validationResult.aiAnalysisCompleted === false" class="ai-suggestion-empty">
          <el-alert
            title="AI分析暂不可用"
            type="info"
            :closable="false"
            description="AI分析服务暂时不可用，请稍后重试或检查服务配置"
          />
        </div>
      </div>

      <div v-if="!validationResult.valid && validationResult.errors" class="error-list">
        <el-divider content-position="left">问题列表</el-divider>

        <el-collapse v-model="activeNames" class="error-collapse">
          <el-collapse-item
            v-for="(error, index) in validationResult.errors"
            :key="index"
            :name="index"
          >
            <template #title>
              <div class="error-header">
                <el-tag :type="getErrorTypeTagType(error.errorType)" size="small">
                  {{ getErrorTypeText(error.errorType) }}
                </el-tag>
                <span class="error-message">{{ error.message }}</span>
              </div>
            </template>

            <el-card shadow="never" class="error-detail-card">
              <el-descriptions :column="1" size="small" border>
                <el-descriptions-item label="错误码">
                  <el-tag size="small">{{ error.code }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="节点ID">
                  <span class="node-id">{{ error.nodeId }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="错误类型">
                  <el-tag size="small" type="danger">{{ error.errorType }}</el-tag>
                </el-descriptions-item>
              </el-descriptions>

              <div class="suggestion-box">
                <el-divider content-position="left">修复建议</el-divider>
                <div class="suggestion-content">
                  <el-icon color="#409eff"><InfoFilled /></el-icon>
                  <span>{{ error.suggestion }}</span>
                </div>
              </div>

              <div class="error-actions">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  @click="locateNode(error.nodeId)"
                >
                  定位节点
                </el-button>
                <el-button
                  type="success"
                  size="small"
                  plain
                  @click="applySuggestion(error)"
                >
                  应用建议
                </el-button>
              </div>
            </el-card>
          </el-collapse-item>
        </el-collapse>
      </div>

      <div v-if="validationResult.valid" class="success-info">
        <el-result
          icon="success"
          title="故障树校验通过"
          sub-title="当前故障树结构符合FTA分析规范"
        />
      </div>

      <div v-if="!validationResult.errors || validationResult.errors.length === 0" class="no-errors">
        <el-empty description="未发现结构性问题" />
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">关闭</el-button>
        <el-button type="primary" @click="revalidate" :loading="loading">
          重新校验
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { InfoFilled, MagicStick, ChatDotRound } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  validationResult: {
    type: Object,
    default: null
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'revalidate', 'locate-node', 'apply-suggestion'])

const visible = ref(false)
const activeNames = ref([])

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
  if (val) {
    activeNames.value = props.validationResult?.errors?.map((_, i) => i) || []
  }
})

const handleClose = () => {
  emit('update:modelValue', false)
}

const revalidate = () => {
  emit('revalidate')
}

const locateNode = (nodeId) => {
  emit('locate-node', nodeId)
}

const applySuggestion = (error) => {
  emit('apply-suggestion', error)
}

const getErrorTypeText = (errorType) => {
  const map = {
    CIRCULAR_DEPENDENCY: '循环依赖',
    INVALID_BASIC_NODE: '底事件类型错误',
    INSUFFICIENT_INPUTS: '逻辑门输入不足',
    ONTOLOGY_INCONSISTENCY: '本体不一致',
    MISSING_EVENT_TYPE: '缺少事件类型',
    REDUNDANT_PATH: '冗余路径',
    INVALID_GATE: '无效逻辑门'
  }
  return map[errorType] || errorType
}

const getErrorTypeTagType = (errorType) => {
  const map = {
    CIRCULAR_DEPENDENCY: 'danger',
    INVALID_BASIC_NODE: 'warning',
    INSUFFICIENT_INPUTS: 'warning',
    ONTOLOGY_INCONSISTENCY: 'info',
    MISSING_EVENT_TYPE: 'info',
    REDUNDANT_PATH: 'warning',
    INVALID_GATE: 'danger'
  }
  return map[errorType] || 'info'
}

const formatAiSuggestion = (suggestion) => {
  if (!suggestion) return ''
  // 简单的格式化，移除可能的Markdown
  return suggestion
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/#+\s*/g, '')
    .trim()
}
</script>

<style scoped>
.validation-result {
  max-height: 65vh;
  overflow-y: auto;
}

.ai-suggestion-section {
  margin-bottom: 20px;
}

.divider-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #303133;
}

.ai-suggestion-card {
  background: linear-gradient(135deg, #f0f9ff 0%, #e8f4fd 100%);
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #b3d8ff;
}

.ai-suggestion-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 12px;
  font-size: 14px;
}

.ai-suggestion-content {
  color: #303133;
  line-height: 1.8;
  font-size: 14px;
}

.ai-suggestion-content p {
  margin: 0;
  white-space: pre-wrap;
}

.ai-suggestion-empty {
  margin-top: 10px;
}

.error-list {
  margin-top: 10px;
}

.error-collapse {
  border: none;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.error-message {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-detail-card {
  margin-top: 10px;
  background-color: #fafafa;
}

.suggestion-box {
  margin-top: 15px;
}

.suggestion-content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px;
  background-color: #ecf5ff;
  border-radius: 4px;
  color: #409eff;
  font-size: 13px;
  line-height: 1.5;
}

.suggestion-content .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.error-actions {
  display: flex;
  gap: 10px;
  margin-top: 15px;
  justify-content: flex-end;
}

.node-id {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
}

.success-info {
  padding: 20px 0;
}

.no-errors {
  padding: 20px 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
