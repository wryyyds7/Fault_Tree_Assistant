<template>
  <div class="fault-tree-edit-container">
    <el-card class="fault-tree-edit-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon">
              <Edit v-if="!isNew" />
              <Plus v-else />
            </el-icon>
            <span>{{ isNew ? '创建故障树' : '编辑故障树' }}</span>
            <el-select
              v-if="!isNew && versions.length > 0"
              v-model="selectedVersion"
              placeholder="选择版本"
              size="small"
              style="width: 150px; margin-left: 16px"
              @change="onVersionChange"
            >
              <el-option
                v-for="ver in displayedVersions"
                :key="ver.versionId"
                :label="`版本 ${ver.versionNumber}`"
                :value="ver.versionNumber"
              >
                <span>版本 {{ ver.versionNumber }}</span>
                <span style="color: #999; font-size: 12px; margin-left: 8px">
                  {{ formatDate(ver.createdAt) }}
                </span>
              </el-option>
            </el-select>
          </div>
          <div class="header-buttons">
            <el-button type="success" @click="saveFaultTree" :loading="saving">
              <el-icon><Check /></el-icon>
              保存
            </el-button>
            <el-button type="warning" @click="validateFaultTree" :loading="validating">
              <el-icon><CircleCheck /></el-icon>
              校验
            </el-button>
            <el-button type="primary" @click="handleAIGenerate">
              <el-icon><MagicStick /></el-icon>
              {{ hasExistingTree ? '智能调整' : '智能生成' }}
            </el-button>
            <el-dropdown @command="handleExport" trigger="click">
              <el-button type="info">
                <el-icon><Download /></el-icon>
                导出
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="png">
                    <el-icon><Picture /></el-icon> 导出为 PNG
                  </el-dropdown-item>
                  <el-dropdown-item command="jpg">
                    <el-icon><PictureFilled /></el-icon> 导出为 JPG
                  </el-dropdown-item>
                  <el-dropdown-item command="json">
                    <el-icon><Document /></el-icon> 导出为 JSON
                  </el-dropdown-item>
                  <el-dropdown-item command="md">
                    <el-icon><DocumentChecked /></el-icon> 导出为 Markdown
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <div class="form-section">
        <el-form :model="faultTreeForm" inline>
          <el-form-item label="故障树名称">
            <el-input
              v-model="faultTreeForm.name"
              placeholder="请输入故障树名称"
              style="width: 220px"
              clearable
            />
          </el-form-item>
          <el-form-item label="设备类型">
            <el-select
              v-model="faultTreeForm.equipmentType"
              placeholder="请选择或输入"
              style="width: 180px"
              filterable
              allow-create
              default-first-option
            >
              <el-option label="电机" value="motor" />
              <el-option label="液压泵" value="hydraulic_pump" />
              <el-option label="传感器" value="sensor" />
              <el-option label="阀门" value="valve" />
              <el-option label="压缩机" value="compressor" />
              <el-option label="减速机" value="gearbox" />
              <el-option label="发电机" value="generator" />
              <el-option label="变压器" value="transformer" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述" class="description-item">
            <el-input
              type="textarea"
              v-model="faultTreeForm.description"
              placeholder="请输入故障树描述"
              :rows="1"
              style="width: 300px"
            />
          </el-form-item>
        </el-form>
        <div class="shortcut-hint">
          <el-tag size="small" type="info">
            <el-icon><Edit /></el-icon>
            Ctrl+Z 撤销 | Ctrl+Shift+Z 重做
          </el-tag>
        </div>
      </div>

      <div class="editor-container">
        <div class="node-palette">
          <div class="palette-header">
            <el-icon><Collection /></el-icon>
            <span>节点库</span>
          </div>
          <div class="palette-section">
            <div class="palette-title">事件节点</div>
            <div class="palette-items">
              <div class="palette-item" draggable="true" @dragstart="onDragNode($event, 'TOP')">
                <div class="node-icon top-event">
                  <el-icon><Top /></el-icon>
                </div>
                <span>顶事件</span>
              </div>
              <div class="palette-item" draggable="true" @dragstart="onDragNode($event, 'INTERMEDIATE')">
                <div class="node-icon intermediate-event">
                  <el-icon><Grid /></el-icon>
                </div>
                <span>中间事件</span>
              </div>
              <div class="palette-item" draggable="true" @dragstart="onDragNode($event, 'BASIC')">
                <div class="node-icon basic-event">
                  <el-icon><Bottom /></el-icon>
                </div>
                <span>底事件</span>
              </div>
            </div>
          </div>
          <div class="palette-section">
            <div class="palette-title">逻辑门</div>
            <div class="palette-items">
              <div class="palette-item" draggable="true" @dragstart="onDragGate($event, 'AND')">
                <div class="gate-icon and-gate">∧</div>
                <span>与门</span>
              </div>
              <div class="palette-item" draggable="true" @dragstart="onDragGate($event, 'OR')">
                <div class="gate-icon or-gate">∨</div>
                <span>或门</span>
              </div>
              <div class="palette-item" draggable="true" @dragstart="onDragGate($event, 'XOR')">
                <div class="gate-icon xor-gate">⊕</div>
                <span>异或门</span>
              </div>
            </div>
          </div>
          <div class="palette-actions">
            <el-button type="primary" size="small" @click="addNewNodeTo(selectedNode || faultTreeData)" :disabled="selectedNode && selectedNode.eventType === 'BASIC'">
              <el-icon><Plus /></el-icon>
              插入节点
            </el-button>
            <el-button type="success" size="small" @click="updateTreeToBackend" :disabled="isNew">
              <el-icon><Refresh /></el-icon>
              更新到后端
            </el-button>
          </div>
          <div class="pending-changes" v-if="hasChanges">
            <el-badge value="待保存" class="pending-badge" />
            <span>有未保存的更改</span>
          </div>
        </div>

        <div class="canvas-wrapper">
          <!-- 布局切换按钮组 -->
          <div class="layout-controls" v-if="faultTreeData">
            <el-tooltip content="上下布局" placement="top">
              <el-button
                :type="layoutDirection === 'TB' ? 'primary' : 'default'"
                size="small"
                @click="handleLayoutChange('TB')"
              >
                <el-icon><Rank /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip content="左右布局" placement="top">
              <el-button
                :type="layoutDirection === 'LR' ? 'primary' : 'default'"
                size="small"
                @click="handleLayoutChange('LR')"
              >
                <el-icon><Operation /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
          
          <div class="tree-canvas" ref="treeCanvas" @dragover="onDragOver" @drop="onDrop" @contextmenu.prevent="handleRightClick">
            <!-- 全新的SVG故障树组件 -->
            <SVGFaultTree
              ref="svgFaultTreeRef"
              v-if="faultTreeData"
              :treeData="faultTreeData"
              :selectedNodeId="selectedNode?.eventId"
              :layoutDirection="layoutDirection"
              @node-click="handleNodeClick"
              @node-dblclick="handleNodeDoubleClick"
              @node-edit="handleNodeEdit"
              @node-select="handleNodeSelect"
              @layout-change="handleLayoutChange"
            />
            <div v-else class="empty-state">
              <div class="empty-content" :class="{ 'is-creating': isNew }">
                <div class="empty-icon-wrapper">
                  <el-icon class="empty-icon"><Share /></el-icon>
                  <div class="empty-badge" v-if="isNew">
                    <span>创建模式</span>
                  </div>
                </div>
                <h3 class="empty-title">{{ isNew ? '开始创建故障树' : '暂无故障树数据' }}</h3>
                <p class="empty-description" v-if="isNew">
                  请先填写故障树基本信息，然后点击下方按钮创建树结构
                </p>
                <p class="empty-description" v-else>
                  点击按钮加载或创建一个新的故障树
                </p>

                <div class="empty-steps" v-if="isNew">
                  <div class="step-item" :class="{ active: faultTreeForm.name }">
                    <div class="step-number">1</div>
                    <div class="step-text">填写故障树名称</div>
                  </div>
                  <div class="step-connector"></div>
                  <div class="step-item" :class="{ active: faultTreeForm.equipmentType }">
                    <div class="step-number">2</div>
                    <div class="step-text">选择设备类型</div>
                  </div>
                  <div class="step-connector"></div>
                  <div class="step-item">
                    <div class="step-number">3</div>
                    <div class="step-text">创建故障树</div>
                  </div>
                </div>

                <el-button
                  v-if="isNew"
                  type="primary"
                  size="large"
                  @click="createInitialTree"
                  :disabled="!faultTreeForm.name"
                  class="create-btn-large"
                >
                  <el-icon><Plus /></el-icon>
                  创建故障树结构
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  size="large"
                  @click="loadFaultTree"
                >
                  <el-icon><Refresh /></el-icon>
                  加载故障树
                </el-button>

                <div class="quick-tips" v-if="isNew && !faultTreeForm.name">
                  <el-icon><InfoFilled /></el-icon>
                  <span>提示：填写故障树名称后即可创建</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="node-editor" v-if="selectedNode">
          <div class="node-editor-header">
            <el-icon class="node-icon"><Edit /></el-icon>
            <span>节点编辑</span>
            <el-button type="text" @click="selectedNode = null" class="close-btn">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
          <el-form :model="selectedNode" label-width="85px" class="node-form">
            <el-form-item label="事件名称">
              <el-input v-model="selectedNode.eventName" @change="handleNodeUpdate" />
            </el-form-item>
            <el-form-item label="事件类型">
              <el-select v-model="selectedNode.eventType" @change="handleNodeUpdate">
                <el-option label="顶事件" value="TOP">
                  <span><el-tag size="small" type="primary">顶</el-tag> 顶事件</span>
                </el-option>
                <el-option label="中间事件" value="INTERMEDIATE">
                  <span><el-tag size="small" type="success">中</el-tag> 中间事件</span>
                </el-option>
                <el-option label="底事件" value="BASIC">
                  <span><el-tag size="small" type="warning">底</el-tag> 底事件</span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="逻辑门类型" v-if="selectedNode.eventType !== 'BASIC'">
              <el-select v-model="selectedNode.gateType" @change="handleNodeUpdate">
                <el-option label="与门 (∧)" value="AND" />
                <el-option label="或门 (∨)" value="OR" />
                <el-option label="异或门 (⊕)" value="XOR" />
                <el-option label="非门 (¬)" value="NOT" />
              </el-select>
            </el-form-item>
            <el-form-item label="置信度" v-if="selectedNode.confidence !== undefined">
              <div class="confidence-display">
                <el-slider
                  v-model="selectedNode.confidence"
                  :min="0"
                  :max="1"
                  :step="0.01"
                  :format-tooltip="(val) => (val * 100).toFixed(0) + '%'"
                  @change="handleConfidenceUpdate"
                />
                <span class="confidence-value">{{ (selectedNode.confidence * 100).toFixed(0) }}%</span>
              </div>
            </el-form-item>
            <el-form-item label="验证状态">
              <el-select v-model="selectedNode.verificationStatus" @change="handleVerificationUpdate">
                <el-option label="待确认" value="PENDING">
                  <el-tag size="small" type="info">待确认</el-tag>
                </el-option>
                <el-option label="已确认" value="CONFIRMED">
                  <el-tag size="small" type="success">已确认</el-tag>
                </el-option>
                <el-option label="已驳回" value="REJECTED">
                  <el-tag size="small" type="danger">已驳回</el-tag>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="来源依据">
              <el-button type="primary" plain size="small" @click="showSourceDetail = true">
                <el-icon><Connection /></el-icon>
                查看溯源信息
              </el-button>
            </el-form-item>
            <el-form-item label="背景颜色">
              <el-color-picker v-model="selectedNode.bgColor" @change="handleNodeUpdate" show-alpha size="small" />
            </el-form-item>
            <el-form-item label="字体颜色">
              <el-color-picker v-model="selectedNode.textColor" @change="handleNodeUpdate" size="small" />
            </el-form-item>
            <div class="node-actions">
              <el-button
                type="primary"
                @click="addChildNode"
                :disabled="selectedNode.eventType === 'BASIC'"
                class="action-btn"
              >
                <el-icon><Plus /></el-icon>
                添加子节点
              </el-button>
              <el-button
                type="danger"
                @click="deleteNode"
                :disabled="selectedNode.eventType === 'TOP'"
                class="action-btn"
              >
                <el-icon><Delete /></el-icon>
                删除节点
              </el-button>
            </div>
          </el-form>
        </div>
      </div>
    </el-card>

    <el-dialog
      v-model="showGenerateDialog"
      :title="hasExistingTree ? '智能调整故障树' : '智能生成故障树'"
      width="550px"
      :close-on-click-modal="false"
    >
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="顶事件" required>
          <el-input
            v-model="generateForm.topEvent"
            placeholder="请输入故障现象，如：电机过热停机"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="关联文档">
          <el-select
            v-model="generateForm.docIds"
            multiple
            placeholder="请选择关联文档（可选）"
            style="width: 100%"
            clearable
          >
            <el-option
              v-for="doc in availableDocuments"
              :key="doc.documentId"
              :label="doc.fileName"
              :value="doc.documentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="生成模式">
          <el-radio-group v-model="generateForm.generationMode">
            <el-radio label="single_pass">
              <span class="radio-label">单次生成</span>
              <span class="radio-desc">直接生成完整的故障树</span>
            </el-radio>
            <el-radio label="recursive">
              <span class="radio-label">递归生成</span>
              <span class="radio-desc">先生成顶层，再逐个子节点展开</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生成偏好">
          <el-input
            v-model="generateForm.userPreferences"
            type="textarea"
            :rows="4"
            placeholder="请输入生成偏好（可选），如：优先考虑轴承故障原因、希望分析深度为5层等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" @click="triggerGeneration" :loading="generating">
          <el-icon><MagicStick /></el-icon>
          开始生成
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showAdjustDialog"
      title="智能调整故障树"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="adjustForm" label-width="100px">
        <el-form-item label="调整模式">
          <el-radio-group v-model="adjustForm.adjustScope">
            <el-radio label="tree">调整整个故障树</el-radio>
            <el-radio label="node">调整选中节点</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="关联故障树" v-if="adjustForm.adjustScope === 'tree'">
          <el-switch
            v-model="adjustForm.attachExistingTree"
            active-text="附加现有故障树"
            inactive-text="不附加"
          />
          <div v-if="adjustForm.attachExistingTree && faultTreeData" class="attach-tree-info">
            <el-tag type="info">将附加当前故障树结构作为参考</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="选中节点" v-if="adjustForm.adjustScope === 'node'">
          <div class="node-select-wrapper">
            <el-select
              v-model="adjustForm.selectedNodeName"
              placeholder="请选择要调整的节点"
              style="width: 100%"
              clearable
            >
              <el-option
                v-for="node in allNodesList"
                :key="node.eventId"
                :label="`${node.eventName} (${node.eventType})`"
                :value="node.eventId"
              />
            </el-select>
            <div class="node-hint">或直接在画布上点击选择节点</div>
            <div v-if="adjustForm.attachNodeData && selectedNode" class="attach-node-info">
              <el-tag type="info">将附加选中节点: {{ selectedNode.eventName || selectedNode.name }}</el-tag>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="调整要求" v-if="adjustForm.adjustScope === 'node'">
          <el-switch
            v-model="adjustForm.attachNodeData"
            active-text="附加选中节点信息"
            inactive-text="不附加"
          />
        </el-form-item>
        <el-form-item label="顶事件">
          <el-input
            v-model="adjustForm.topEvent"
            disabled
            placeholder="顶事件自动填入"
          />
        </el-form-item>
        <el-form-item label="关联文档">
          <el-select
            v-model="adjustForm.docIds"
            multiple
            placeholder="请选择关联文档（可选）"
            style="width: 100%"
            clearable
          >
            <el-option
              v-for="doc in availableDocuments"
              :key="doc.documentId"
              :label="doc.fileName"
              :value="doc.documentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="调整偏好">
          <el-input
            v-model="adjustForm.adjustPreferences"
            type="textarea"
            :rows="4"
            placeholder="请输入调整要求（可选），如：补充缺失的故障路径、修正逻辑门类型、增加节点置信度等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdjustDialog = false">取消</el-button>
        <el-button type="primary" @click="triggerAdjustment" :loading="adjusting">
          <el-icon><MagicStick /></el-icon>
          开始调整
        </el-button>
      </template>
    </el-dialog>

    <SourceDetail
      v-model="showSourceDetail"
      :nodeData="selectedNode"
    />

    <ValidationResult
      v-model="showValidationDialog"
      :validationResult="validationResult"
      :loading="validating"
      @revalidate="validateFaultTree"
      @locate-node="locateNode"
      @apply-suggestion="applySuggestion"
    />

    <!-- 任务进度对话框 -->
    <el-dialog
      v-model="showProgressDialog"
      :title="progressTitle"
      width="550px"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <div class="progress-content">
        <div class="progress-info">
          <el-icon class="progress-icon" :class="{ 'is-loading': isPolling }">
            <Loading v-if="isPolling" />
            <SuccessFilled v-else-if="progressStatus === 'success'" />
            <CircleCloseFilled v-else-if="progressStatus === 'error'" />
          </el-icon>
          <span class="progress-message">{{ progressMessage }}</span>
        </div>
        <el-progress
          :percentage="progressPercentage"
          :status="progressStatus"
          :stroke-width="12"
          :show-text="true"
        />
        <div class="progress-detail" v-if="progressDetail">
          {{ progressDetail }}
        </div>
        
        <!-- 递归生成步骤展示 -->
        <div v-if="currentGenMode === 'recursive'" class="recursive-steps">
          <div class="steps-title">
            <el-icon><Collection /></el-icon>
            生成进度
          </div>
          <div class="steps-list">
            <div 
              v-for="(step, index) in recursiveSteps" 
              :key="index" 
              class="step-item"
              :class="{ 
                'step-completed': step.status === 'completed',
                'step-processing': step.status === 'processing',
                'step-pending': step.status === 'pending'
              }"
            >
              <div class="step-icon">
                <el-icon v-if="step.status === 'completed'"><SuccessFilled /></el-icon>
                <el-icon v-else-if="step.status === 'processing'"><Loading /></el-icon>
                <span v-else>{{ index + 1 }}</span>
              </div>
              <div class="step-content">
                <div class="step-name">{{ step.name }}</div>
                <div class="step-desc" v-if="step.desc">{{ step.desc }}</div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 实时预览开关 -->
        <div v-if="currentGenMode === 'recursive'" class="preview-option">
          <el-switch 
            v-model="isRealTimeUpdate" 
            active-text="实时预览"
            @change="toggleRealTimePreview"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="cancelPolling" :disabled="!isPolling">
          取消
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Edit, Plus, Check, CircleCheck, MagicStick, Download, ArrowDown,
  Picture, PictureFilled, Document, DocumentChecked, Collection,
  Top, Grid, Bottom, Close, Delete, Connection, Share, InfoFilled,
  Loading, SuccessFilled, CircleCloseFilled, Refresh, Rank, Operation
} from '@element-plus/icons-vue'

// 导入新的SVG故障树组件
import SVGFaultTree from '../components/SVGFaultTree.vue'
import SourceDetail from '../components/SourceDetail.vue'
import ValidationResult from '../components/ValidationResult.vue'

// 导入API
import { ragAPI, faultTreeAPI, validationAPI, documentAPI, knowledgeGraphAPI } from '../api'

// 获取路由对象
const route = useRoute()

// 响应式数据
const isNew = ref(true)
const saving = ref(false)
const validating = ref(false)
const generating = ref(false)
const adjusting = ref(false)
const selectedNode = ref(null)
const layoutDirection = ref('TB')
const showGenerateDialog = ref(false)
const showAdjustDialog = ref(false)
const showSourceDetail = ref(false)
const showValidationDialog = ref(false)
const hasChanges = ref(false)
const selectedVersion = ref('')
const versions = ref([])
const displayedVersions = ref([])
const availableDocuments = ref([])
const currentTreeId = ref(null) // 当前故障树ID

// 任务进度对话框相关变量
const showProgressDialog = ref(false)
const progressTitle = ref('')
const progressMessage = ref('')
const progressDetail = ref('')
const progressPercentage = ref(0)
const progressStatus = ref('')
const isPolling = ref(false)
let pollingController = null // 轮询取消控制器
let taskCancelled = false // 任务取消标志：true 表示用户已取消，不再处理任何数据

// 递归生成相关变量
const currentGenMode = ref('') // 当前生成模式
const recursiveSteps = ref([]) // 递归生成步骤记录
const isRealTimeUpdate = ref(false) // 是否实时更新画布显示

// 故障树表单数据
const faultTreeForm = reactive({
  name: '',
  equipmentType: '',
  description: ''
})

// 故障树数据
const faultTreeData = ref(null)

// 生成表单数据
const generateForm = reactive({
  topEvent: '',
  docIds: [],
  userPreferences: '',
  generationMode: 'single_pass' // single_pass 或 recursive
})

// 调整表单数据
const adjustForm = reactive({
  adjustScope: 'tree',
  attachExistingTree: false,
  selectedNodeName: '',
  attachNodeData: false,
  topEvent: '',
  docIds: [],
  adjustPreferences: ''
})

// 验证结果
const validationResult = ref({})

// 计算属性
const hasExistingTree = computed(() => !isNew.value && faultTreeData.value)
const allNodesList = computed(() => {
  if (!faultTreeData.value) return []
  return getAllNodes(faultTreeData.value)
})

// 组件事件
const emit = defineEmits(['node-click', 'node-dblclick', 'node-edit', 'node-select'])

// 方法定义
const saveFaultTree = async () => {
  if (!faultTreeForm.name) {
    ElMessage.warning('请先填写故障树名称')
    return
  }

  if (!faultTreeData.value) {
    ElMessage.warning('没有可保存的故障树数据')
    return
  }

  saving.value = true
  try {
    console.log('保存故障树 - faultTreeData:', faultTreeData.value)
    console.log('保存故障树 - faultTreeForm:', faultTreeForm)

    // 后端 DTO 期望的字段名是 treeData，不是 faultTree
    const saveData = {
      name: faultTreeForm.name,
      equipmentType: faultTreeForm.equipmentType,
      description: faultTreeForm.description,
      treeData: faultTreeData.value
    }
    console.log('保存故障树 - saveData:', saveData)

    if (isNew.value) {
      // 创建新的故障树
      const response = await faultTreeAPI.create(saveData)
      console.log('create API返回:', response)
      
      // 兼容不同的响应数据结构
      let newTreeId = null
      if (response.treeId) {
        newTreeId = response.treeId
      } else if (response.data && response.data.treeId) {
        newTreeId = response.data.treeId
      } else if (response.id) {
        newTreeId = response.id
      }
      
      if (newTreeId) {
        currentTreeId.value = newTreeId
        isNew.value = false
        hasChanges.value = false
        // 加载版本列表
        await loadVersions()
        // 保存节点到知识图谱
        try {
          await saveNodesToKnowledgeGraph()
          ElMessage.success('故障树和知识图谱保存成功！')
        } catch (kgError) {
          console.error('保存知识图谱失败:', kgError)
          ElMessage.success('故障树保存成功！（知识图谱保存失败）')
        }
      } else {
        throw new Error(response.message || '保存失败')
      }
    } else {
      // 更新现有故障树
      const response = await faultTreeAPI.update(currentTreeId.value, saveData)
      console.log('update API返回:', response)
      
      if (response.treeId || response.success || response.data) {
        hasChanges.value = false
        // 重新加载版本列表（因为更新时自动创建了新版本）
        await loadVersions()
        // 保存节点到知识图谱
        try {
          await saveNodesToKnowledgeGraph()
          ElMessage.success('故障树和知识图谱更新成功！')
        } catch (kgError) {
          console.error('保存知识图谱失败:', kgError)
          ElMessage.success('故障树更新成功！（知识图谱保存失败）')
        }
      } else {
        throw new Error(response.message || '更新失败')
      }
    }
  } catch (error) {
    console.error('保存故障树失败:', error)
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 保存故障树节点到知识图谱
const saveNodesToKnowledgeGraph = async () => {
  if (!faultTreeData.value) return

  const userId = localStorage.getItem('userId') || 'anonymous'
  const allNodes = getAllNodes(faultTreeData.value)
  
  console.log('保存节点到知识图谱 - 节点总数:', allNodes.length)

  // 递归处理每个节点及其关系
  for (let i = 0; i < allNodes.length; i++) {
    const node = allNodes[i]
    
    // 先保存节点本身
    const eventTypeMap = { 'TOP': '顶事件', 'INTERMEDIATE': '中间事件', 'BASIC': '底事件' }
    
    // 如果有子节点，建立因果关系
    if (node.children && node.children.length > 0) {
      for (let j = 0; j < node.children.length; j++) {
        const childNode = node.children[j]
        const childEventName = childNode.eventName || childNode.name
        
        await knowledgeGraphAPI.enrich({
          userId: userId,
          docId: currentTreeId.value || 'fault_tree',
          cause: childEventName,
          effect: node.eventName || node.name,
          gateType: node.gateType || 'OR',
          confidence: node.confidence || 0.9,
          equipmentType: faultTreeForm.equipmentType || '通用',
          causeEventType: eventTypeMap[childNode.eventType] || '底事件',
          effectEventType: eventTypeMap[node.eventType] || '中间事件',
          causeDescription: childNode.description || '',
          effectDescription: node.description || ''
        })
        
        console.log(`保存节点关系: ${childEventName} → ${node.eventName || node.name}`)
      }
    } else {
      // 如果没有子节点，只保存这个节点
      await knowledgeGraphAPI.enrich({
        userId: userId,
        docId: currentTreeId.value || 'fault_tree',
        cause: (node.eventName || node.name),
        effect: '根节点事件',
        gateType: 'OR',
        confidence: 0.9,
        equipmentType: faultTreeForm.equipmentType || '通用',
        causeEventType: eventTypeMap[node.eventType] || '底事件',
        effectEventType: '中间事件',
        causeDescription: node.description || ''
      })
    }
  }
  
  console.log('知识图谱保存完成！')
}

const validateFaultTree = async () => {
  if (!faultTreeData.value) {
    ElMessage.warning('没有可验证的故障树数据')
    return
  }

  validating.value = true
  try {
    const response = await validationAPI.validate(faultTreeData.value)
    validationResult.value = response
    showValidationDialog.value = true
  } catch (error) {
    console.error('故障树验证失败:', error)
    ElMessage.error(error.message || '校验失败')
  } finally {
    validating.value = false
  }
}

const handleAIGenerate = () => {
  if (hasExistingTree.value) {
    const rootNode = findRootNode(faultTreeData.value)
    adjustForm.topEvent = rootNode ? (rootNode.eventName || rootNode.name) : (faultTreeForm.name || '顶事件')
    adjustForm.selectedNodeName = ''
    adjustForm.adjustScope = 'tree'
    adjustForm.attachExistingTree = false
    adjustForm.attachNodeData = false
    adjustForm.docIds = []
    adjustForm.adjustPreferences = ''
    showAdjustDialog.value = true
  } else {
    // 如果用户已填写了故障树名称，预填充到顶事件
    if (faultTreeForm.name && !generateForm.topEvent) {
      generateForm.topEvent = faultTreeForm.name
    }
    showGenerateDialog.value = true
  }
}

const handleExport = async (command) => {
  if (!faultTreeData.value) {
    ElMessage.warning('没有可导出的故障树数据')
    return
  }

  try {
    switch (command.toLowerCase()) {
      case 'png':
      case 'jpg':
        await exportAsImage(command.toLowerCase())
        break
      case 'json':
        exportAsJSON()
        break
      case 'markdown':
        exportAsMarkdown()
        break
      default:
        ElMessage.warning('不支持的导出格式')
    }
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error(error.message || '导出失败')
  }
}

// 导出为图片
const svgFaultTreeRef = ref(null)

const exportAsImage = async (format) => {
  const svgElement = document.querySelector('.fault-tree-svg')
  if (!svgElement) {
    throw new Error('未找到SVG元素')
  }

  // 第一步：从组件获取所有节点来计算完整边界
  let exportWidth = 1920
  let exportHeight = 1080
  let offsetX = 0
  let offsetY = 0
  let useNodeCalculation = false
  
  if (svgFaultTreeRef.value && svgFaultTreeRef.value.layoutNodes) {
    const nodes = svgFaultTreeRef.value.layoutNodes
    if (nodes.length > 0) {
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
      
      nodes.forEach(node => {
        minX = Math.min(minX, node.x)
        minY = Math.min(minY, node.y)
        maxX = Math.max(maxX, node.x + node.width)
        maxY = Math.max(maxY, node.y + node.height)
      })
      
      // 添加padding
      const padding = 60
      exportWidth = Math.ceil(maxX - minX + padding * 2)
      exportHeight = Math.ceil(maxY - minY + padding * 2)
      offsetX = padding - minX
      offsetY = padding - minY
      useNodeCalculation = true
    }
  }
  
  // 如果组件没有节点数据，回退到原来的方案
  if (!useNodeCalculation || exportWidth <= 0 || exportHeight <= 0) {
    if (svgFaultTreeRef.value) {
      exportWidth = svgFaultTreeRef.value.svgWidth || 1920
      exportHeight = svgFaultTreeRef.value.svgHeight || 1080
    } else {
      exportWidth = parseInt(svgElement.getAttribute('width') || '1920')
      exportHeight = parseInt(svgElement.getAttribute('height') || '1080')
    }
    offsetX = 0
    offsetY = 0
  }

  // 第二步：创建一个全新的SVG用于导出（不仅仅是克隆）
  const xmlns = 'http://www.w3.org/2000/svg'
  const exportSvg = document.createElementNS(xmlns, 'svg')
  
  exportSvg.setAttribute('width', exportWidth)
  exportSvg.setAttribute('height', exportHeight)
  exportSvg.setAttribute('viewBox', `0 0 ${exportWidth} ${exportHeight}`)
  exportSvg.setAttribute('xmlns', xmlns)
  
  // 添加样式
  const style = document.createElementNS(xmlns, 'style')
  style.textContent = `
    .connection-path {
      stroke: #666;
      stroke-width: 1.5;
      fill: none;
    }
    .connection-path.low-confidence {
      stroke: #ff4d4f;
      stroke-dasharray: 5,5;
    }
    .connection-path.selected {
      stroke: #1890ff;
      stroke-width: 2;
    }
    .node-rect {
      filter: url(#node-shadow);
    }
    .node-group.selected .node-rect {
      stroke: #1890ff;
      stroke-width: 2;
      filter: url(#selected-glow);
    }
  `
  exportSvg.appendChild(style)
  
  // 第三步：从原SVG克隆defs
  const originalDefs = svgElement.querySelector('defs')
  if (originalDefs) {
    const defsClone = originalDefs.cloneNode(true)
    exportSvg.appendChild(defsClone)
  }
  
  // 创建内容容器（带正确的平移）
  const contentGroup = document.createElementNS(xmlns, 'g')
  if (useNodeCalculation && (offsetX !== 0 || offsetY !== 0)) {
    contentGroup.setAttribute('transform', `translate(${offsetX}, ${offsetY})`)
  }
  
  // 获取原始的内容组
  const originalContentGroup = svgElement.querySelector('g[transform*="translate"]')
  if (originalContentGroup) {
    // 深度克隆内容组，但移除原有的transform
    const clonedContent = originalContentGroup.cloneNode(true)
    clonedContent.removeAttribute('transform')
    
    // 将所有子元素加入到我们的contentGroup
    while (clonedContent.firstChild) {
      contentGroup.appendChild(clonedContent.firstChild)
    }
  } else {
    // 如果找不到，就把svg的所有子元素都加进去（除了defs和第一个矩形背景）
    const children = Array.from(svgElement.children)
    children.forEach(child => {
      if (child.tagName.toLowerCase() !== 'defs' && 
          !(child.tagName.toLowerCase() === 'rect' && child.getAttribute('x') === '0')) {
        contentGroup.appendChild(child.cloneNode(true))
      }
    })
  }
  
  exportSvg.appendChild(contentGroup)
  
  // 第四步：添加白色背景
  const bgRect = document.createElementNS(xmlns, 'rect')
  bgRect.setAttribute('x', '0')
  bgRect.setAttribute('y', '0')
  bgRect.setAttribute('width', exportWidth)
  bgRect.setAttribute('height', exportHeight)
  bgRect.setAttribute('fill', 'white')
  exportSvg.insertBefore(bgRect, exportSvg.firstChild)
  
  // 第五步：序列化并导出
  const svgData = new XMLSerializer().serializeToString(exportSvg)
  const svgBlob = new Blob([svgData], { type: 'image/svg+xml;charset=utf-8' })
  const svgUrl = URL.createObjectURL(svgBlob)

  // 第六步：绘制到canvas并下载
  const img = new Image()
  img.onload = () => {
    const canvas = document.createElement('canvas')
    const canvasWidth = exportWidth * 2
    const canvasHeight = exportHeight * 2
    canvas.width = canvasWidth
    canvas.height = canvasHeight

    const ctx = canvas.getContext('2d')
    ctx.fillStyle = 'white'
    ctx.fillRect(0, 0, canvasWidth, canvasHeight)
    ctx.drawImage(img, 0, 0, canvasWidth, canvasHeight)

    const mimeType = format === 'png' ? 'image/png' : 'image/jpeg'
    const link = document.createElement('a')
    link.download = `${faultTreeForm.name || 'fault-tree'}.${format}`
    link.href = canvas.toDataURL(mimeType, 0.9)
    link.click()

    URL.revokeObjectURL(svgUrl)
    ElMessage.success(`导出为${format.toUpperCase()}成功，尺寸：${exportWidth}x${exportHeight}`)
  }
  img.onerror = () => {
    URL.revokeObjectURL(svgUrl)
    ElMessage.error('图片导出失败，请重试')
  }
  img.src = svgUrl
}

// 导出为JSON
const exportAsJSON = () => {
  const exportData = {
    version: '1.0',
    name: faultTreeForm.name,
    equipmentType: faultTreeForm.equipmentType,
    description: faultTreeForm.description,
    treeData: faultTreeData.value,
    exportTime: new Date().toISOString()
  }

  const jsonStr = JSON.stringify(exportData, null, 2)
  const blob = new Blob([jsonStr], { type: 'application/json' })
  const link = document.createElement('a')
  link.download = `${faultTreeForm.name || 'fault-tree'}.json`
  link.href = URL.createObjectURL(blob)
  link.click()
  URL.revokeObjectURL(link.href)

  ElMessage.success('导出为JSON成功')
}

// 导出为Markdown
const exportAsMarkdown = () => {
  const mdContent = generateMarkdown()

  const blob = new Blob([mdContent], { type: 'text/markdown' })
  const link = document.createElement('a')
  link.download = `${faultTreeForm.name || 'fault-tree'}.md`
  link.href = URL.createObjectURL(blob)
  link.click()
  URL.revokeObjectURL(link.href)

  ElMessage.success('导出为Markdown成功')
}

// 生成Markdown内容
const generateMarkdown = () => {
  let md = `# ${faultTreeForm.name || '故障树分析报告'}\n\n`
  md += `## 基本信息\n\n`
  md += `- **设备类型**: ${faultTreeForm.equipmentType || '未指定'}\n`
  md += `- **描述**: ${faultTreeForm.description || '无'}\n`
  md += `- **导出时间**: ${new Date().toLocaleString('zh-CN')}\n\n`

  md += `## 故障树结构\n\n`
  md += generateTreeMarkdown(faultTreeData.value, 0)

  return md
}

// 递归生成故障树的Markdown表示
const generateTreeMarkdown = (node, level) => {
  if (!node) return ''

  const indent = '  '.repeat(level)
  const prefix = level === 0 ? '##' : '###'

  let md = `${prefix} ${node.eventName || node.name}\n\n`
  md += `${indent}- **类型**: ${node.eventType}\n`
  if (node.gateType) {
    md += `${indent}- **逻辑门**: ${node.gateType}\n`
  }
  if (node.confidence !== undefined) {
    md += `${indent}- **置信度**: ${(node.confidence * 100).toFixed(1)}%\n`
  }
  md += '\n'

  if (node.children && node.children.length > 0) {
    md += `${indent}**子事件**:\n\n`
    node.children.forEach(child => {
      md += generateTreeMarkdown(child, level + 1)
    })
  }

  return md
}

const onDragNode = (event, type) => {
  event.dataTransfer.setData('text/plain', JSON.stringify({ type, action: 'addNode' }))
  event.dataTransfer.effectAllowed = 'copy'
}

const onDragGate = (event, type) => {
  event.dataTransfer.setData('text/plain', JSON.stringify({ type, action: 'addGate' }))
  event.dataTransfer.effectAllowed = 'copy'
}

const onDragOver = (event) => {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'copy'
}

const onDrop = (event) => {
  event.preventDefault()
  try {
    const data = JSON.parse(event.dataTransfer.getData('text/plain'))
    console.log('拖拽数据:', data)

    if (!faultTreeData.value) {
      ElMessage.warning('请先创建故障树或加载故障树')
      return
    }

    if (data.action === 'addNode') {
      if (selectedNode.value) {
        if (selectedNode.value.eventType === 'BASIC') {
          ElMessage.warning('底事件不能再添加子节点')
          return
        }
        addChildNodeToTree(selectedNode.value, data.type)
      } else {
        ElMessage.warning('请先在画布上选择一个节点作为父节点')
      }
    } else if (data.action === 'addGate') {
      if (selectedNode.value) {
        if (selectedNode.value.eventType === 'BASIC') {
          ElMessage.warning('底事件不能添加逻辑门')
          return
        }
        selectedNode.value.gateType = data.type
        hasChanges.value = true
        ElMessage.success(`已为 ${selectedNode.value.eventName || selectedNode.value.name} 设置 ${data.type} 逻辑门`)
      } else {
        ElMessage.warning('请先在画布上选择一个节点')
      }
    }
  } catch (error) {
    console.error('处理拖拽失败:', error)
  }
}

const handleRightClick = (event) => {
  // 右键菜单逻辑
}

// 节点点击事件处理
const handleNodeClick = (node) => {
  console.log('handleNodeClick called with:', node?.id, node?.eventName)

  const originalNode = findNodeById(faultTreeData.value, node.id)
  if (originalNode) {
    selectedNode.value = originalNode
  } else {
    selectedNode.value = node
  }

  emit('node-click', selectedNode.value)
  emit('node-select', selectedNode.value)
}

// 节点双击事件处理
const handleNodeDoubleClick = (node) => {
  // 双击编辑逻辑
  console.log('双击节点:', node)
}

// 节点编辑事件处理
const handleNodeEdit = (node, newName) => {
  if (!node || !node.id) return

  updateTreeDataNode(faultTreeData.value, node.id, (targetNode) => {
    targetNode.eventName = newName
    targetNode.name = newName
  })

  hasChanges.value = true
  emit('node-edit', node, newName)
}

const updateTreeDataNode = (treeNode, nodeId, updateFn) => {
  if (!treeNode) return false

  if (treeNode.eventId === nodeId || treeNode.id === nodeId) {
    updateFn(treeNode)
    return true
  }

  if (treeNode.children && treeNode.children.length > 0) {
    for (const child of treeNode.children) {
      if (updateTreeDataNode(child, nodeId, updateFn)) {
        return true
      }
    }
  }

  return false
}

// 节点选择事件处理
const handleNodeSelect = (node) => {
  console.log('handleNodeSelect called with:', node?.id, node?.eventName)

  const originalNode = findNodeById(faultTreeData.value, node.id)
  if (originalNode) {
    selectedNode.value = originalNode
  } else {
    selectedNode.value = node
  }

  emit('node-select', selectedNode.value)
}

const handleLayoutChange = (direction) => {
  layoutDirection.value = direction
}

const handleNodeUpdate = () => {
  if (!selectedNode.value || !selectedNode.value.eventId) return

  updateTreeDataNode(faultTreeData.value, selectedNode.value.eventId, (targetNode) => {
    Object.assign(targetNode, {
      eventName: selectedNode.value.eventName,
      name: selectedNode.value.eventName,
      eventType: selectedNode.value.eventType,
      gateType: selectedNode.value.gateType,
      bgColor: selectedNode.value.bgColor,
      textColor: selectedNode.value.textColor
    })
  })

  hasChanges.value = true
}

const handleConfidenceUpdate = () => {
  if (!selectedNode.value || !selectedNode.value.eventId) return

  updateTreeDataNode(faultTreeData.value, selectedNode.value.eventId, (targetNode) => {
    targetNode.confidence = selectedNode.value.confidence
  })

  hasChanges.value = true
}

const handleVerificationUpdate = () => {
  if (!selectedNode.value || !selectedNode.value.eventId) return

  updateTreeDataNode(faultTreeData.value, selectedNode.value.eventId, (targetNode) => {
    targetNode.verificationStatus = selectedNode.value.verificationStatus
  })

  hasChanges.value = true
}

const addChildNodeToTree = (parentNode, eventType) => {
  if (!parentNode) {
    ElMessage.warning('请先选择父节点')
    return
  }

  if (parentNode.eventType === 'BASIC') {
    ElMessage.warning('底事件不能再添加子节点')
    return
  }

  const newNodeId = `evt_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  const newNode = {
    eventId: newNodeId,
    eventName: eventType === 'TOP' ? '新顶事件' : eventType === 'INTERMEDIATE' ? '新中间事件' : '新底事件',
    eventType: eventType,
    gateType: eventType === 'BASIC' ? undefined : 'OR',
    confidence: 0.9,
    verificationStatus: 'PENDING',
    children: []
  }

  if (!parentNode.children) {
    parentNode.children = []
  }

  parentNode.children.push(newNode)

  if (!parentNode.gateType) {
    parentNode.gateType = 'OR'
  }

  hasChanges.value = true
  selectedNode.value = newNode

  ElMessage.success(`已添加${eventType === 'TOP' ? '顶' : eventType === 'INTERMEDIATE' ? '中间' : '底'}事件子节点`)
}

const addNewNodeTo = (parentNode) => {
  if (!parentNode) {
    if (faultTreeData.value) {
      addChildNodeToTree(faultTreeData.value, 'INTERMEDIATE')
    } else {
      ElMessage.warning('请先创建或加载故障树')
    }
    return
  }

  if (parentNode.eventType === 'BASIC') {
    ElMessage.warning('底事件不能再添加子节点')
    return
  }

  addChildNodeToTree(parentNode, 'INTERMEDIATE')
}

const updateTreeToBackend = async () => {
  if (isNew.value) {
    // 如果是新故障树，先保存
    await saveFaultTree()
  } else if (currentTreeId.value) {
    // 如果是已有故障树，直接更新
    saving.value = true
    try {
      const updateData = {
        name: faultTreeForm.name,
        equipmentType: faultTreeForm.equipmentType,
        description: faultTreeForm.description,
        treeData: faultTreeData.value
      }

      const response = await faultTreeAPI.update(currentTreeId.value, updateData)
      console.log('updateTreeToBackend API返回:', response)
      
      if (response.treeId || response.success || response.data) {
        hasChanges.value = false
        // 重新加载版本列表（因为更新时自动创建了新版本）
        await loadVersions()
        ElMessage.success('故障树更新成功')
      } else {
        throw new Error(response.message || '更新失败')
      }
    } catch (error) {
      console.error('更新故障树失败:', error)
      ElMessage.error(error.message || '更新失败')
    } finally {
      saving.value = false
    }
  } else {
    ElMessage.warning('没有可更新的故障树')
  }
}

const createInitialTree = () => {
  if (!faultTreeForm.name) {
    ElMessage.warning('请先填写故障树名称')
    return
  }

  // 创建初始故障树结构
  faultTreeData.value = {
    eventId: 'root',
    eventName: faultTreeForm.name,
    eventType: 'TOP',
    gateType: 'OR',
    confidence: 0.9,
    verificationStatus: 'PENDING',
    children: []
  }

  // 标记为新故障树，需要保存
  isNew.value = true
  currentTreeId.value = null
  hasChanges.value = true
  ElMessage.success('故障树创建成功，请保存')
}

const loadFaultTree = async () => {
  if (!currentTreeId.value) {
    ElMessage.warning('没有可加载的故障树')
    return
  }

  console.log('loadFaultTree 被调用，currentTreeId:', currentTreeId.value)

  try {
    const response = await faultTreeAPI.getById(currentTreeId.value)
    console.log('loadFaultTree API返回:', response)

    if (response) {
      // 兼容不同的响应数据结构
      const data = response.data || response

      // 更新表单数据
      faultTreeForm.name = data.name || ''
      faultTreeForm.equipmentType = data.equipmentType || ''
      faultTreeForm.description = data.description || ''

      // 更新故障树数据 - 后端返回的是 treeData 字段
      faultTreeData.value = data.treeData || data

      console.log('加载后的故障树数据:', faultTreeData.value)

      // 标记为已有故障树
      isNew.value = false

      // 加载版本列表
      await loadVersions()

      ElMessage.success('故障树加载成功')
    } else {
      throw new Error(response?.message || '加载失败')
    }
  } catch (error) {
    console.error('加载故障树失败:', error)
    ElMessage.error(error.message || '加载失败')
  }
}

const onVersionChange = async (version) => {
  if (!currentTreeId.value) {
    ElMessage.warning('没有可切换版本的故障树')
    return
  }

  // 如果当前有未保存的变更，先保存当前版本
  if (hasChanges.value && faultTreeData.value) {
    try {
      console.log('切换版本前保存当前状态...')
      await updateTreeToBackend() // 保存当前状态，会自动创建新版本
      // 重新加载版本列表（因为保存后创建了新版本）
      await loadVersions()
      // 重置选中版本（因为列表可能变了）
      selectedVersion.value = ''
      ElMessage.success('当前状态已保存')
    } catch (error) {
      console.error('保存当前状态失败:', error)
      // 用户可以选择是否继续切换
      const { value } = await ElMessageBox.confirm(
        '保存当前状态失败，是否继续切换版本？',
        '警告',
        {
          confirmButtonText: '继续',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
      if (!value) {
        return // 用户取消，不切换
      }
    }
  }

  try {
    const response = await faultTreeAPI.getVersion(currentTreeId.value, version)
    console.log('getVersion API返回:', response)
    
    let versionData = null
    // 兼容不同的响应数据结构
    if (response.success && response.data) {
      versionData = response.data
    } else if (response.data) {
      versionData = response.data
    } else if (response) {
      versionData = response
    }
    
    if (versionData) {
      // 更新故障树数据为选中版本
      if (versionData.treeData) {
        faultTreeData.value = versionData.treeData
      } else {
        faultTreeData.value = versionData
      }

      // 更新选中节点
      if (faultTreeData.value) {
        selectedNode.value = findRootNode(faultTreeData.value)
        emit('node-select', selectedNode.value)
      }
      
      // 更新表单数据
      if (versionData.changeSummary) {
        faultTreeForm.name = versionData.changeSummary.replace('更新故障树: ', '').replace('创建故障树: ', '')
      }

      // 重置变更标记（因为切换到历史版本）
      hasChanges.value = false

      ElMessage.success(`已切换到版本 ${version}`)
    } else {
      throw new Error(response.message || '版本切换失败')
    }
  } catch (error) {
    console.error('版本切换失败:', error)
    ElMessage.error(error.message || '版本切换失败')
  }
}

const loadVersions = async () => {
  if (!currentTreeId.value) return

  try {
    const response = await faultTreeAPI.getVersions(currentTreeId.value)
    console.log('loadVersions API返回:', response)
    
    // 兼容不同的响应数据结构
    let versionData = null
    if (response.success && response.data) {
      versionData = response.data
    } else if (Array.isArray(response)) {
      versionData = response
    } else if (response.data && Array.isArray(response.data)) {
      versionData = response.data
    }
    
    if (versionData && Array.isArray(versionData)) {
      versions.value = versionData
      displayedVersions.value = versionData.slice(0, 10) // 最多显示10个版本
      console.log('版本列表加载成功:', versions.value)
    }
  } catch (error) {
    console.error('加载版本列表失败:', error)
  }
}

const createVersion = async () => {
  if (!currentTreeId.value) {
    ElMessage.warning('请先保存故障树')
    return
  }

  try {
    const response = await faultTreeAPI.createVersion(currentTreeId.value, {
      description: `版本 ${versions.value.length + 1}`
    })

    if (response.success) {
      ElMessage.success('版本创建成功')
      await loadVersions() // 刷新版本列表
    } else {
      throw new Error(response.message || '版本创建失败')
    }
  } catch (error) {
    console.error('创建版本失败:', error)
    ElMessage.error(error.message || '创建版本失败')
  }
}

const formatDate = (date) => {
  return new Date(date).toLocaleDateString('zh-CN')
}

// 查找根节点
const findRootNode = (treeData) => {
  if (!treeData) return null
  return {
    eventId: treeData.eventId,
    eventName: treeData.eventName,
    eventType: treeData.eventType,
    gateType: treeData.gateType,
    confidence: treeData.confidence,
    verificationStatus: treeData.verificationStatus
  }
}

// 根据ID查找节点
const findNodeById = (node, targetId) => {
  if (!node) return null
  if (node.eventId === targetId) return node

  if (node.children) {
    for (const child of node.children) {
      const found = findNodeById(child, targetId)
      if (found) return found
    }
  }
  return null
}

// 获取所有节点列表（扁平化）
const getAllNodes = (node, result = []) => {
  if (!node) return result
  result.push({
    eventId: node.eventId,
    eventName: node.eventName || node.name,
    eventType: node.eventType
  })
  if (node.children) {
    for (const child of node.children) {
      getAllNodes(child, result)
    }
  }
  return result
}

// 任务轮询配置
const TASK_POLLING_CONFIG = {
  interval: 2000,      // 轮询间隔（毫秒）
  maxAttempts: 60,     // 最大轮询次数
  timeout: 120000      // 总超时时间（毫秒）
}

// 轮询任务状态
const pollTaskStatus = async (taskId, onProgress) => {
  const startTime = Date.now()
  let attempts = 0
  
  while (attempts < TASK_POLLING_CONFIG.maxAttempts) {
    // 检查是否已全局取消
    if (taskCancelled) {
      throw new Error('用户取消了任务')
    }
    
    // 检查是否超时
    if (Date.now() - startTime > TASK_POLLING_CONFIG.timeout) {
      throw new Error('任务处理超时，请稍后重试')
    }
    
    // 调用API查询任务状态
    const response = await ragAPI.getTaskStatus(taskId)
    
    if (response.status === 'completed') {
      // 任务完成，返回结果
      return response
    } else if (response.status === 'failed') {
      // 任务失败
      throw new Error(response.error || '任务处理失败')
    } else if (response.status === 'processing') {
      // 任务处理中，继续轮询
      attempts++
      
      // 再次检查是否已全局取消
      if (taskCancelled) {
        throw new Error('用户取消了任务')
      }
      
      // 调用进度回调（如果提供）
      if (onProgress && typeof onProgress === 'function') {
        const progress = Math.min((attempts / TASK_POLLING_CONFIG.maxAttempts) * 100, 90)
        onProgress(progress, response)
      }
      
      // 等待一段时间后继续轮询
      await new Promise((resolve, reject) => {
        const timeoutId = setTimeout(() => {
          if (taskCancelled) {
            reject(new Error('用户取消了任务'))
          } else {
            resolve()
          }
        }, TASK_POLLING_CONFIG.interval)
        
        // 保存timeout ID以便取消
        pollingController = timeoutId
      })
    } else {
      // 未知状态
      throw new Error('任务状态异常')
    }
  }
  
  throw new Error('任务处理超时，请稍后重试')
}

// 取消轮询
const cancelPolling = () => {
  // 设置全局取消标志，确保后续即使数据到达也不会渲染
  taskCancelled = true
  
  if (pollingController) {
    clearTimeout(pollingController)
    pollingController = null
  }
  isPolling.value = false
  showProgressDialog.value = false
  ElMessage.info('已取消任务')
}

// 切换实时预览
const toggleRealTimePreview = () => {
  if (isRealTimeUpdate.value) {
    ElMessage.info('已开启实时预览，生成过程将在画布上同步显示')
  } else {
    ElMessage.info('已关闭实时预览，完整生成后才会显示')
  }
}

const triggerGeneration = async () => {
  if (!generateForm.topEvent.trim()) {
    ElMessage.warning('请输入顶事件')
    return
  }

  // 重置取消标志
  taskCancelled = false
  currentGenMode.value = generateForm.generationMode
  recursiveSteps.value = []

  // 初始化进度对话框
  progressTitle.value = generateForm.generationMode === 'recursive' 
    ? '递归生成故障树' 
    : '智能生成故障树'
  progressMessage.value = generateForm.generationMode === 'recursive' 
    ? '正在生成顶层故障树...'
    : '正在生成故障树，请稍候...'
  progressDetail.value = ''
  progressPercentage.value = 0
  progressStatus.value = ''
  isPolling.value = true
  showProgressDialog.value = true
  showGenerateDialog.value = false

  // 如果是递归模式，初始化步骤显示
  if (generateForm.generationMode === 'recursive') {
    recursiveSteps.value = [
      { name: '生成顶层故障树', status: 'processing', desc: generateForm.topEvent },
      { name: '展开中间节点', status: 'pending', desc: '' },
      { name: '完善底事件', status: 'pending', desc: '' },
      { name: '合并生成结果', status: 'pending', desc: '' }
    ]
  }

  try {
    // 调用后端RAG API进行智能生成
    const userId = localStorage.getItem('userId')
    const response = await ragAPI.generate({
      topEvent: generateForm.topEvent,
      docIds: generateForm.docIds,
      userPreferences: generateForm.userPreferences,
      equipmentType: faultTreeForm.equipmentType,
      adjustMode: false,
      userId: userId,
      generationMode: generateForm.generationMode // 传递生成模式
    })

    // 检查是否已经被取消
    if (taskCancelled) {
      console.log('任务已在开始前取消，不处理数据')
      return
    }

    // 处理后端返回的异步任务机制
    if (response.taskId && response.status === 'processing') {
      // 更新进度消息
      progressDetail.value = `任务ID: ${response.taskId}`

      // 轮询任务状态
      const taskResult = await pollTaskStatus(response.taskId, (progress) => {
        progressPercentage.value = Math.round(progress)
      })

      // 再次检查是否被取消，即使数据返回也不渲染
      if (taskCancelled) {
        console.log('任务已取消，不渲染数据')
        return
      }

      // 任务完成，处理返回的故障树数据
      console.log('轮询任务完成，返回数据:', taskResult)
      
      // 兼容不同的响应数据结构
      // 可能的后端返回格式1: {success: true, data: {treeData: ...}}
      // 可能的后端返回格式2: {status: 'completed', treeData: ...}
      // 可能的后端返回格式3: {data: {treeData: ...}} (无success字段)
      // 也可能返回 faultTree 字段（旧版本兼容）
      let faultTree = null
      if (taskResult.data?.treeData) {
        faultTree = taskResult.data.treeData
      } else if (taskResult.treeData) {
        faultTree = taskResult.treeData
      } else if (taskResult.data?.data?.treeData) {
        faultTree = taskResult.data.data.treeData
      } else if (taskResult.data?.faultTree) {
        faultTree = taskResult.data.faultTree
      } else if (taskResult.faultTree) {
        faultTree = taskResult.faultTree
      }

      console.log('提取的故障树数据:', faultTree)
      
      if (faultTree) {
        progressPercentage.value = 100
         progressStatus.value = 'success'
         progressMessage.value = '故障树生成成功！'

         // 更新故障树数据
         faultTreeData.value = faultTree

        // 自动将顶事件名称填入故障树名称表单
        if (faultTree.eventName || faultTree.name) {
          faultTreeForm.name = faultTree.eventName || faultTree.name
        }

        // 触发SVG组件重新渲染
        nextTick(() => {
          if (faultTreeData.value) {
            selectedNode.value = findRootNode(faultTreeData.value)
            emit('node-select', selectedNode.value)
            // 标记为新故障树，需要保存后才能获取treeId
            isNew.value = true
            currentTreeId.value = null
            hasChanges.value = true
          }
        })

        // 延迟关闭对话框
        setTimeout(() => {
          showProgressDialog.value = false
          ElMessage.success('故障树生成成功，请点击保存')
        }, 1500)
      } else {
        progressStatus.value = 'exception'
        progressMessage.value = '生成失败'
        progressDetail.value = taskResult.message || '未返回有效数据'
        ElMessage.error(taskResult.message || '生成失败，未返回有效数据')
      }
    } else if (response.success && response.data) {
      // 同步返回数据（兼容旧接口）
      progressPercentage.value = 100
      progressStatus.value = 'success'
      progressMessage.value = '故障树生成成功！'

      // 兼容 treeData 和 faultTree 字段
      const generatedTree = response.data?.treeData || response.data?.faultTree || response.data
      faultTreeData.value = generatedTree

      // 自动将顶事件名称填入故障树名称表单
      if (generatedTree?.eventName || generatedTree?.name) {
        faultTreeForm.name = generatedTree.eventName || generatedTree.name
      }

      nextTick(() => {
        if (faultTreeData.value) {
          selectedNode.value = findRootNode(faultTreeData.value)
          emit('node-select', selectedNode.value)
          // 标记为新故障树，需要保存
          isNew.value = true
          currentTreeId.value = null
          hasChanges.value = true
        }
      })

      setTimeout(() => {
        showProgressDialog.value = false
        ElMessage.success('故障树生成成功，请保存')
      }, 1500)
    } else {
      progressStatus.value = 'exception'
      progressMessage.value = '生成失败'
      progressDetail.value = response.message || '未知错误'
      ElMessage.error(response.message || '生成失败')
    }
  } catch (error) {
    console.error('智能生成失败:', error)
    // 如果是用户取消的错误，不显示错误提示
    if (error.message !== '用户取消了任务') {
      progressStatus.value = 'exception'
      progressMessage.value = '生成失败'
      progressDetail.value = error.message || '请稍后重试'
      ElMessage.error(error.message || '生成失败，请稍后重试')
    }
  } finally {
    isPolling.value = false
  }
}

const triggerAdjustment = async () => {
  if (!adjustForm.topEvent.trim()) {
    ElMessage.warning('请输入顶事件')
    return
  }

  // 重置取消标志
  taskCancelled = false

  // 初始化进度对话框
  progressTitle.value = '智能调整故障树'
  progressMessage.value = '正在调整故障树，请稍候...'
  progressDetail.value = ''
  progressPercentage.value = 0
  progressStatus.value = ''
  isPolling.value = true
  showProgressDialog.value = true
  showAdjustDialog.value = false

  try {
    const userId = localStorage.getItem('userId')
    const adjustData = {
      topEvent: adjustForm.topEvent,
      docIds: adjustForm.docIds,
      userPreferences: adjustForm.adjustPreferences,
      equipmentType: faultTreeForm.equipmentType,
      adjustMode: true,
      userId: userId
    }

    // 根据调整模式附加不同数据
    if (adjustForm.adjustScope === 'tree' && adjustForm.attachExistingTree) {
      adjustData.existingTree = faultTreeData.value
    } else if (adjustForm.adjustScope === 'node' && adjustForm.attachNodeData && selectedNode.value) {
      adjustData.selectedNode = selectedNode.value
      adjustData.selectedNodeName = adjustForm.selectedNodeName
    }

    // 调用后端RAG API进行智能调整
    const response = await ragAPI.generate(adjustData)

    // 检查是否已经被取消
    if (taskCancelled) {
      console.log('任务已在开始前取消，不处理数据')
      return
    }

    // 处理后端返回的异步任务机制
    if (response.taskId && response.status === 'processing') {
      // 更新进度消息
      progressDetail.value = `任务ID: ${response.taskId}`

      // 轮询任务状态
      const taskResult = await pollTaskStatus(response.taskId, (progress) => {
        progressPercentage.value = Math.round(progress)
      })

      // 再次检查是否被取消，即使数据返回也不渲染
      if (taskCancelled) {
        console.log('任务已取消，不渲染数据')
        return
      }

      // 任务完成，处理返回的故障树数据
      console.log('调整轮询任务完成，返回数据:', taskResult)
      
      // 兼容不同的响应数据结构
      let adjustFaultTree = null
      if (taskResult.data?.treeData) {
        adjustFaultTree = taskResult.data.treeData
      } else if (taskResult.treeData) {
        adjustFaultTree = taskResult.treeData
      } else if (taskResult.data?.data?.treeData) {
        adjustFaultTree = taskResult.data.data.treeData
      } else if (taskResult.data?.faultTree) {
        adjustFaultTree = taskResult.data.faultTree
      } else if (taskResult.faultTree) {
        adjustFaultTree = taskResult.faultTree
      }

      console.log('提取的adjustFaultTree:', adjustFaultTree)
      
      if (adjustFaultTree) {
        progressPercentage.value = 100
        progressStatus.value = 'success'
        progressMessage.value = '故障树调整成功！'

        // 更新故障树数据
        faultTreeData.value = adjustFaultTree

        // 触发重新渲染
        nextTick(() => {
          if (faultTreeData.value) {
            // 保持当前选中状态或重置
            if (adjustForm.adjustScope === 'node' && selectedNode.value) {
              // 尝试重新找到对应的节点
              const adjustedNode = findNodeById(faultTreeData.value, selectedNode.value.eventId)
              selectedNode.value = adjustedNode || findRootNode(faultTreeData.value)
            } else {
              selectedNode.value = findRootNode(faultTreeData.value)
            }
            emit('node-select', selectedNode.value)

            // 如果是树级别调整，标记为需要保存
            if (adjustForm.adjustScope === 'tree') {
              if (currentTreeId.value) {
                // 已有树，标记为有变更
                hasChanges.value = true
                ElMessage.info('故障树已修改，请保存')
              } else {
                // 新树，需要创建
                isNew.value = true
                hasChanges.value = true
                ElMessage.info('故障树已修改，请保存')
              }
            } else {
              // 节点级别调整，也标记为有变更
              hasChanges.value = true
            }
          }
        })

        // 延迟关闭对话框
        setTimeout(() => {
          showProgressDialog.value = false
        }, 1500)
      } else {
        progressStatus.value = 'exception'
        progressMessage.value = '调整失败'
        progressDetail.value = taskResult.message || '未返回有效数据'
        ElMessage.error(taskResult.message || '调整失败，未返回有效数据')
      }
    } else if (response.success && response.data) {
      // 同步返回数据（兼容旧接口）
      progressPercentage.value = 100
      progressStatus.value = 'success'
      progressMessage.value = '故障树调整成功！'

      faultTreeData.value = response.data.faultTree

      nextTick(() => {
        if (faultTreeData.value) {
          // 保持当前选中状态或重置
          if (adjustForm.adjustScope === 'node' && selectedNode.value) {
            // 尝试重新找到对应的节点
            const adjustedNode = findNodeById(faultTreeData.value, selectedNode.value.eventId)
            selectedNode.value = adjustedNode || findRootNode(faultTreeData.value)
          } else {
            selectedNode.value = findRootNode(faultTreeData.value)
          }
          emit('node-select', selectedNode.value)

          // 标记为有变更
          hasChanges.value = true
        }
      })

      setTimeout(() => {
        showProgressDialog.value = false
        ElMessage.success('故障树调整成功，请保存')
      }, 1500)
    } else {
      progressStatus.value = 'exception'
      progressMessage.value = '调整失败'
      progressDetail.value = response.message || '未知错误'
      ElMessage.error(response.message || '调整失败')
    }
  } catch (error) {
    console.error('智能调整失败:', error)
    // 如果是用户取消的错误，不显示错误提示
    if (error.message !== '用户取消了任务') {
      progressStatus.value = 'exception'
      progressMessage.value = '调整失败'
      progressDetail.value = error.message || '请稍后重试'
      ElMessage.error(error.message || '调整失败，请稍后重试')
    }
  } finally {
    isPolling.value = false
  }
}

const addChildNode = () => {
  if (!selectedNode.value) {
    ElMessage.warning('请先选择一个节点')
    return
  }

  if (selectedNode.value.eventType === 'BASIC') {
    ElMessage.warning('底事件不能再添加子节点')
    return
  }

  addChildNodeToTree(selectedNode.value, 'INTERMEDIATE')
}

const deleteNodeFromTree = (parentNode, targetNodeId) => {
  if (!parentNode || !parentNode.children) return false

  const index = parentNode.children.findIndex(child =>
    child.eventId === targetNodeId || child.id === targetNodeId
  )

  if (index !== -1) {
    parentNode.children.splice(index, 1)
    return true
  }

  for (const child of parentNode.children) {
    if (deleteNodeFromTree(child, targetNodeId)) {
      return true
    }
  }

  return false
}

const deleteNode = () => {
  if (!selectedNode.value) {
    ElMessage.warning('请先选择一个节点')
    return
  }

  if (selectedNode.value.eventType === 'TOP') {
    ElMessage.warning('不能删除顶事件')
    return
  }

  const nodeName = selectedNode.value.eventName || selectedNode.value.name

  ElMessageBox.confirm(
    `确定要删除节点"${nodeName}"吗？此操作不可恢复。`,
    '删除确认',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const deleted = deleteNodeFromTree(faultTreeData.value, selectedNode.value.eventId)

    if (deleted) {
      selectedNode.value = null
      hasChanges.value = true
      ElMessage.success('节点删除成功')
    } else {
      ElMessage.error('删除失败，未找到节点')
    }
  }).catch(() => {
    // 用户取消
  })
}

const locateNode = (nodeId) => {
  // 定位节点逻辑
}

const applySuggestion = (suggestion) => {
  // 应用建议逻辑
}

// 监听数据变化
watch(faultTreeData, (newVal) => {
  hasChanges.value = true
}, { deep: true })

watch(() => adjustForm.selectedNodeName, (newNodeId) => {
  if (newNodeId && faultTreeData.value) {
    selectedNode.value = findNodeById(faultTreeData.value, newNodeId)
  } else {
    selectedNode.value = null
  }
})

// 加载可用文档列表
const loadAvailableDocuments = async () => {
  try {
    const response = await documentAPI.getDocumentList()
    if (response && Array.isArray(response)) {
      availableDocuments.value = response.map(doc => ({
        documentId: doc.documentId || doc.id,
        fileName: doc.fileName || doc.name
      }))
      console.log('加载可用文档:', availableDocuments.value)
    } else if (response && Array.isArray(response.data)) {
      availableDocuments.value = response.data.map(doc => ({
        documentId: doc.documentId || doc.id,
        fileName: doc.fileName || doc.name
      }))
    }
  } catch (error) {
    console.error('加载文档列表失败:', error)
  }
}

// 组件挂载时初始化
onMounted(() => {
  // 检查路由参数，判断是新建还是编辑
  // 路由配置为 /fault-tree/edit/:treeId
  const treeIdParam = route.params.treeId
  if (treeIdParam && treeIdParam !== 'new') {
    currentTreeId.value = treeIdParam
    isNew.value = false
    console.log('正在加载故障树，ID:', treeIdParam)
    // 加载故障树数据
    loadFaultTree()
  } else {
    // 新建故障树，设置默认值
    isNew.value = true
    currentTreeId.value = null
  }

  // 加载可用文档列表
  loadAvailableDocuments()
})

watch(selectedNode, (newVal) => {
  // 选中节点变化逻辑
})
</script>

<style scoped>
.fault-tree-edit-container {
  height: 100vh;
  padding: 20px;
  background: #f5f7fa;
}

.fault-tree-edit-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-icon {
  font-size: 18px;
  color: #409eff;
}

.header-buttons {
  display: flex;
  gap: 8px;
}

.form-section {
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.description-item {
  margin-bottom: 0 !important;
}

.shortcut-hint {
  margin-top: 12px;
  text-align: center;
}

.editor-container {
  flex: 1;
  display: flex;
  height: calc(100% - 120px);
  min-height: 500px;
}

.node-palette {
  width: 200px;
  background: #f8f9fa;
  border-right: 1px solid #e4e7ed;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.palette-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  font-weight: 600;
  color: #303133;
}

.palette-section {
  margin-bottom: 24px;
}

.palette-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.palette-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: white;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: grab;
  transition: all 0.2s;
}

.palette-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 4px rgba(64, 158, 255, 0.1);
}

.node-icon {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 12px;
}

.top-event {
  background: #409eff;
}

.intermediate-event {
  background: #67c23a;
}

.basic-event {
  background: #e6a23c;
}

.gate-icon {
  width: 24px;
  height: 24px;
  border: 1px solid #909399;
  border-radius: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
  color: #303133;
}

.and-gate {
  background: #f0f9ff;
  border-color: #409eff;
}

.or-gate {
  background: #f6ffed;
  border-color: #67c23a;
}

.xor-gate {
  background: #fff7e6;
  border-color: #e6a23c;
}

.palette-actions {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pending-changes {
  margin-top: 12px;
  padding: 8px;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #d46b08;
}

.pending-badge {
  :deep(.el-badge__content) {
    background: #fa541c;
  }
}

.canvas-wrapper {
  flex: 1;
  display: flex;
  position: relative;
  background: white;
  z-index: 1;
  overflow: hidden;
}

.layout-controls {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 10;
  display: flex;
  gap: 8px;
  background: rgba(255, 255, 255, 0.95);
  padding: 8px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: 1px solid #e4e7ed;
}

.tree-canvas {
  flex: 1;
  height: 100%;
  position: relative;
  min-width: 0;
  overflow: hidden;
}

.empty-state {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-content {
  text-align: center;
  max-width: 400px;
}

.empty-content.is-creating {
  max-width: 500px;
}

.empty-icon-wrapper {
  position: relative;
  margin-bottom: 20px;
}

.empty-icon {
  font-size: 64px;
  color: #c0c4cc;
}

.empty-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  background: #409eff;
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.empty-title {
  font-size: 20px;
  color: #303133;
  margin-bottom: 12px;
}

.empty-description {
  color: #606266;
  margin-bottom: 24px;
  line-height: 1.6;
}

.empty-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.step-item.active .step-number {
  background: #409eff;
  color: white;
}

.step-number {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: #909399;
  transition: all 0.3s;
}

.step-text {
  font-size: 12px;
  color: #909399;
}

.step-connector {
  width: 40px;
  height: 2px;
  background: #f0f2f5;
  margin: 0 8px;
}

.create-btn-large {
  padding: 12px 32px;
  font-size: 16px;
}

.quick-tips {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #909399;
  font-size: 14px;
}

.node-editor {
  width: 280px;
  background: #f8f9fa;
  border-left: 1px solid #e4e7ed;
  padding: 16px;
  overflow-y: auto;
  position: relative;
  z-index: 100;
  flex-shrink: 0;
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.1);
  max-height: 100%;
}

.node-editor-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}

.node-icon {
  color: #409eff;
}

.close-btn {
  margin-left: auto;
}

.node-form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

.confidence-display {
  display: flex;
  align-items: center;
  gap: 12px;
}

.confidence-value {
  min-width: 40px;
  text-align: center;
  font-weight: 600;
  color: #409eff;
}

.node-actions {
  margin-top: 20px;
  display: flex;
  gap: 8px;
}

.action-btn {
  flex: 1;
}

.attach-tree-info,
.attach-node-info {
  margin-top: 8px;
}

.node-select-wrapper {
  width: 100%;
}

.node-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* 任务进度对话框样式 */
.progress-content {
  padding: 10px 0;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.progress-icon {
  font-size: 24px;
  color: #409eff;
}

.progress-icon.is-loading {
  animation: rotating 2s linear infinite;
}

.progress-icon:not(.is-loading) {
  animation: none;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.progress-message {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.progress-detail {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
  text-align: center;
  word-break: break-all;
}

/* 递归生成步骤样式 */
.recursive-steps {
  margin-top: 24px;
  border-top: 1px solid #ebeef5;
  padding-top: 20px;
}

.steps-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.steps-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recursive-steps .step-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: 6px;
  transition: all 0.3s;
}

.recursive-steps .step-item.step-completed {
  background: #f0f9ff;
  border: 1px solid #b3e19d;
}

.recursive-steps .step-item.step-processing {
  background: #f0f9ff;
  border: 1px solid #91d5ff;
  animation: pulse 2s ease-in-out infinite;
}

.recursive-steps .step-item.step-pending {
  background: #f5f7fa;
  border: 1px dashed #dcdfe6;
  opacity: 0.7;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.recursive-steps .step-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.recursive-steps .step-item.step-completed .step-icon {
  background: #67c23a;
  color: white;
}

.recursive-steps .step-item.step-processing .step-icon {
  background: #409eff;
  color: white;
}

.recursive-steps .step-item.step-pending .step-icon {
  background: #f0f2f5;
  color: #909399;
}

.recursive-steps .step-content {
  flex: 1;
  min-width: 0;
}

.recursive-steps .step-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.recursive-steps .step-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 实时预览开关样式 */
.preview-option {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: flex-end;
}

/* 单选按钮样式优化 */
:deep(.el-radio-group) {
  width: 100%;
}

:deep(.el-radio) {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-right: 0;
  padding: 12px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  transition: all 0.3s;
  margin-bottom: 12px;
}

:deep(.el-radio:last-child) {
  margin-bottom: 0;
}

:deep(.el-radio:hover) {
  border-color: #409eff;
  background: #f0f9ff;
}

:deep(.el-radio.is-checked) {
  border-color: #409eff;
  background: #ecf5ff;
}

.radio-label {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.radio-desc {
  font-size: 12px;
  color: #909399;
}
</style>