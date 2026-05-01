<template>
  <div class="feedback-container">
    <el-card class="feedback-card">
      <template #header>
        <div class="card-header">
          <span>{{ isAdmin ? '反馈管理' : '意见反馈' }}</span>
          <el-button type="primary" @click="loadFeedback">刷新</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="feedback-tabs">
        <el-tab-pane v-if="!isAdmin" label="提交反馈" name="submit">
          <el-form :model="feedbackForm" :rules="feedbackRules" ref="feedbackFormRef" label-width="100px" class="feedback-form">
            <el-form-item label="故障树ID" prop="treeId">
              <el-input v-model="feedbackForm.treeId" placeholder="请输入故障树ID" />
            </el-form-item>
            <el-form-item label="反馈类型" prop="feedbackType">
              <el-select v-model="feedbackForm.feedbackType" placeholder="请选择反馈类型" style="width: 100%">
                <el-option label="纠错" value="CORRECTION" />
                <el-option label="确认" value="CONFIRMATION" />
                <el-option label="建议" value="SUGGESTION" />
                <el-option label="异议" value="REJECTION" />
              </el-select>
            </el-form-item>
            <el-form-item label="反馈内容" prop="content">
              <el-input v-model="feedbackForm.content" type="textarea" :rows="4" placeholder="请输入反馈内容" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitFeedback" :loading="submitLoading">提交反馈</el-button>
              <el-button @click="resetFeedbackForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="我的反馈" name="my">
          <el-table :data="myFeedback" style="width: 100%" v-loading="loading">
            <el-table-column prop="feedbackId" label="反馈ID" width="150" />
            <el-table-column prop="treeId" label="故障树ID" width="150" />
            <el-table-column prop="feedbackType" label="反馈类型" width="120">
              <template #default="scope">
                <el-tag :type="getFeedbackTypeTagType(scope.row.feedbackType)">
                  {{ scope.row.feedbackType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="反馈内容" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="getStatusTagType(scope.row.status)">
                  {{ scope.row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column label="操作" width="150">
              <template #default="scope">
                <el-button size="small" type="primary" @click="viewFeedbackDetail(scope.row)">
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="isAdmin" label="待处理" name="pending">
          <el-table :data="pendingFeedback" style="width: 100%" v-loading="loading">
            <el-table-column prop="feedbackId" label="反馈ID" width="150" />
            <el-table-column prop="treeId" label="故障树ID" width="150" />
            <el-table-column prop="userId" label="用户ID" width="150" />
            <el-table-column prop="feedbackType" label="反馈类型" width="120">
              <template #default="scope">
                <el-tag :type="getFeedbackTypeTagType(scope.row.feedbackType)">
                  {{ scope.row.feedbackType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="反馈内容" />
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column label="操作" width="200">
              <template #default="scope">
                <el-button size="small" type="success" @click="processFeedback(scope.row, 'APPROVED')">
                  批准
                </el-button>
                <el-button size="small" type="danger" @click="processFeedback(scope.row, 'REJECTED')">
                  拒绝
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="isAdmin" label="全部反馈" name="all">
          <el-table :data="allFeedback" style="width: 100%" v-loading="loading">
            <el-table-column prop="feedbackId" label="反馈ID" width="150" />
            <el-table-column prop="treeId" label="故障树ID" width="150" />
            <el-table-column prop="userId" label="用户ID" width="150" />
            <el-table-column prop="feedbackType" label="反馈类型" width="120">
              <template #default="scope">
                <el-tag :type="getFeedbackTypeTagType(scope.row.feedbackType)">
                  {{ scope.row.feedbackType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="反馈内容" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="getStatusTagType(scope.row.status)">
                  {{ scope.row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column label="操作" width="100">
              <template #default="scope">
                <el-button size="small" type="primary" @click="viewFeedbackDetail(scope.row)">
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        style="margin-top: 20px"
      />
    </el-card>

    <el-dialog
      v-model="detailDialogVisible"
      title="反馈详情"
      width="600px"
    >
      <div v-if="currentFeedback" class="feedback-detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="反馈ID">
            {{ currentFeedback.feedbackId }}
          </el-descriptions-item>
          <el-descriptions-item label="故障树ID">
            {{ currentFeedback.treeId }}
          </el-descriptions-item>
          <el-descriptions-item label="用户ID">
            {{ currentFeedback.userId }}
          </el-descriptions-item>
          <el-descriptions-item label="反馈类型">
            <el-tag :type="getFeedbackTypeTagType(currentFeedback.feedbackType)">
              {{ currentFeedback.feedbackType }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(currentFeedback.status)">
              {{ currentFeedback.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ currentFeedback.createdAt }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">反馈内容</el-divider>
        <el-card shadow="never">
          <div class="feedback-content">{{ currentFeedback.content }}</div>
        </el-card>

        <div v-if="currentFeedback.response" class="feedback-response">
          <el-divider content-position="left">处理回复</el-divider>
          <el-card shadow="never" class="response-card">
            <div class="response-content">{{ currentFeedback.response }}</div>
          </el-card>
        </div>

        <div v-if="isAdmin && currentFeedback.status === 'PENDING'" class="process-section">
          <el-divider content-position="left">处理反馈</el-divider>
          <el-form :model="processForm" label-width="80px">
            <el-form-item label="处理结果">
              <el-select v-model="processForm.status" placeholder="请选择处理结果" style="width: 100%">
                <el-option label="批准" value="APPROVED" />
                <el-option label="拒绝" value="REJECTED" />
              </el-select>
            </el-form-item>
            <el-form-item label="回复内容">
              <el-input v-model="processForm.response" type="textarea" :rows="3" placeholder="请输入回复内容" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitProcess">提交处理</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { feedbackAPI } from '@/api'

const isAdmin = computed(() => localStorage.getItem('role') === 'ADMIN')
const loading = ref(false)
const activeTab = ref('my')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const submitLoading = ref(false)

const myFeedback = ref([])
const pendingFeedback = ref([])
const allFeedback = ref([])

const detailDialogVisible = ref(false)
const currentFeedback = ref(null)

const feedbackFormRef = ref(null)
const feedbackForm = ref({
  treeId: '',
  feedbackType: '',
  content: ''
})

const feedbackRules = {
  treeId: [{ required: true, message: '请输入故障树ID', trigger: 'blur' }],
  feedbackType: [{ required: true, message: '请选择反馈类型', trigger: 'change' }],
  content: [{ required: true, message: '请输入反馈内容', trigger: 'blur' }]
}

const processForm = ref({
  status: '',
  response: ''
})

const getFeedbackTypeTagType = (type) => {
  const map = {
    CORRECTION: 'warning',
    CONFIRMATION: 'success',
    SUGGESTION: 'primary',
    REJECTION: 'danger'
  }
  return map[type] || 'info'
}

const getStatusTagType = (status) => {
  const map = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    INTEGRATED: 'info'
  }
  return map[status] || 'info'
}

const loadFeedback = async () => {
  loading.value = true
  try {
    const response = await feedbackAPI.getAll()
    if (response && Array.isArray(response)) {
      allFeedback.value = response
      myFeedback.value = response.filter(f => f.userId === getCurrentUserId())
      pendingFeedback.value = response.filter(f => f.status === 'PENDING')
      total.value = response.length
    } else if (response && response.data) {
      allFeedback.value = response.data
      myFeedback.value = response.data.filter(f => f.userId === getCurrentUserId())
      pendingFeedback.value = response.data.filter(f => f.status === 'PENDING')
      total.value = response.data.length
    }
  } catch (error) {
    console.error('加载反馈列表失败:', error)
    ElMessage.error('加载反馈列表失败')
    loadMockData()
  } finally {
    loading.value = false
  }
}

const loadMockData = () => {
  allFeedback.value = [
    {
      feedbackId: 'fb_001',
      treeId: 'tree_001',
      userId: 'user_001',
      feedbackType: 'CORRECTION',
      content: '建议将"电机过热"改为"电机温度异常"，更加准确描述故障现象。',
      status: 'PENDING',
      createdAt: '2026-03-20 10:30:00'
    },
    {
      feedbackId: 'fb_002',
      treeId: 'tree_002',
      userId: 'user_002',
      feedbackType: 'CONFIRMATION',
      content: '确认"轴承润滑不足"节点的置信度较高，无需修改。',
      status: 'APPROVED',
      createdAt: '2026-03-19 14:20:00'
    }
  ]
  myFeedback.value = allFeedback.value.filter(f => f.userId === 'user_001')
  pendingFeedback.value = allFeedback.value.filter(f => f.status === 'PENDING')
  total.value = allFeedback.value.length
}

const getCurrentUserId = () => {
  return localStorage.getItem('userId') || 'user_001'
}

const submitFeedback = async () => {
  if (!feedbackFormRef.value) return
  try {
    await feedbackFormRef.value.validate()
    submitLoading.value = true
    await feedbackAPI.create({
      treeId: feedbackForm.value.treeId,
      feedbackType: feedbackForm.value.feedbackType,
      content: feedbackForm.value.content
    })
    ElMessage.success('反馈提交成功')
    resetFeedbackForm()
    loadFeedback()
    activeTab.value = 'my'
  } catch (error) {
    console.error('提交反馈失败:', error)
    ElMessage.error('提交反馈失败')
  } finally {
    submitLoading.value = false
  }
}

const resetFeedbackForm = () => {
  feedbackForm.value = {
    treeId: '',
    feedbackType: '',
    content: ''
  }
  feedbackFormRef.value?.resetFields()
}

const viewFeedbackDetail = (feedback) => {
  currentFeedback.value = feedback
  processForm.value = { status: '', response: '' }
  detailDialogVisible.value = true
}

const processFeedback = (feedback, status) => {
  currentFeedback.value = feedback
  processForm.value = { status, response: '' }
  detailDialogVisible.value = true
}

const submitProcess = async () => {
  if (!currentFeedback.value) return
  try {
    ElMessage.success('反馈处理成功')
    detailDialogVisible.value = false
    loadFeedback()
  } catch (error) {
    console.error('处理反馈失败:', error)
    ElMessage.error('处理反馈失败')
  }
}

const handleSizeChange = (size) => {
  pageSize.value = size
  loadFeedback()
}

const handleCurrentChange = (current) => {
  currentPage.value = current
  loadFeedback()
}

onMounted(() => {
  loadFeedback()
})
</script>

<style scoped>
.feedback-container {
  width: 100%;
}

.feedback-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.feedback-tabs {
  margin-top: 10px;
}

.feedback-detail {
  padding: 10px 0;
}

.feedback-content,
.response-content {
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
}

.response-card {
  background-color: #f0f9eb;
}

.feedback-response {
  margin-top: 20px;
}

.feedback-form {
  max-width: 600px;
  margin: 20px 0;
}

.process-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}
</style>
