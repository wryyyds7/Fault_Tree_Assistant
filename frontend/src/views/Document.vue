<template>
  <div class="document-container">
    <el-card class="document-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><UploadFilled /></el-icon>
            <span>文档管理</span>
          </div>
          <el-button type="primary" @click="handleUpload">
            <el-icon><Plus /></el-icon>
            上传文档
          </el-button>
        </div>
      </template>

      <div class="upload-section">
        <el-upload
          class="upload-demo"
          ref="uploadRef"
          :auto-upload="false"
          :on-change="handleFileChange"
          :file-list="fileList"
          :limit="10"
          :on-exceed="handleExceed"
          accept=".pdf,.doc,.docx,.txt"
          drag
          multiple
        >
          <div class="upload-content">
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="upload-text">拖拽文件到此处，或<span class="upload-link">点击上传</span></div>
            <div class="upload-tip">
              支持 PDF、DOC、DOCX、TXT 格式，单个文件不超过 50MB
            </div>
          </div>
        </el-upload>

        <div v-if="fileList.length > 0" class="file-queue">
          <div class="queue-header">
            <span>待上传文件 ({{ fileList.length }})</span>
            <el-button type="text" @click="clearFileList">清空</el-button>
          </div>
          <div class="file-list">
            <div v-for="(file, index) in fileList" :key="index" class="file-item">
              <el-icon class="file-icon"><Document /></el-icon>
              <span class="file-name">{{ file.name }}</span>
              <span class="file-size">{{ formatFileSize(file.size) }}</span>
              <el-icon class="remove-icon" @click="removeFile(index)"><Close /></el-icon>
            </div>
          </div>
          
          <!-- 添加设备类型选择 -->
          <div class="upload-options">
            <el-form label-width="100px" size="default">
              <el-form-item label="文档来源类型">
                <el-select
                  v-model="selectedSourceType"
                  placeholder="选择文档来源类型"
                  style="width: 100%"
                >
                  <el-option label="自动分类（推荐）" value="auto" />
                  <el-option label="设备手册/说明书" value="equipment_manual" />
                  <el-option label="维修记录/故障报告" value="maintenance_record" />
                  <el-option label="行业标准/规范文件" value="industry_standard" />
                  <el-option label="理论文献/学术论文" value="theory_paper" />
                  <el-option label="用户反馈/调查报告" value="user_feedback" />
                  <el-option label="其他/未知" value="unknown" />
                </el-select>
                <div class="form-tip" v-if="selectedSourceType === 'auto'">
                  系统将根据文档内容自动判断来源类型
                </div>
              </el-form-item>
              <el-form-item label="设备类型">
                <el-input
                  v-model="selectedEquipmentType"
                  placeholder="请输入设备类型，或留空默认使用通用型"
                  clearable
                  style="width: 100%"
                >
                  <template #append>
                    <el-button @click="selectedEquipmentType = ''">通用型</el-button>
                  </template>
                </el-input>
                <div class="form-tip">建议使用通用型（留空即可）</div>
              </el-form-item>
            </el-form>
          </div>
          
          <div class="upload-actions">
            <el-button type="success" size="large" @click="submitUpload" :loading="uploading">
              <el-icon><Upload /></el-icon>
              开始上传
            </el-button>
          </div>
        </div>
      </div>

      <el-divider>
        <el-icon><Coin /></el-icon>
        已上传文档
      </el-divider>

      <div class="document-toolbar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索文档名称"
          style="width: 250px"
          clearable
          @clear="loadDocuments"
          @keyup.enter="searchDocuments"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" plain @click="loadDocuments">
          <el-icon><Refresh /></el-icon>
          刷新列表
        </el-button>
      </div>

      <el-table :data="documents" style="width: 100%" v-loading="loading" stripe>
        <el-table-column prop="documentId" label="文档ID" width="180" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column prop="fileType" label="文件类型" width="80" align="center">
          <template #default="scope">
            <el-tag size="small" :type="getFileTypeTag(scope.row.fileType)">
              {{ scope.row.fileType?.toUpperCase() }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="分类" width="130" align="center">
          <template #default="scope">
            <el-tag size="small" :type="getSourceTypeTag(scope.row.sourceType)">
              {{ getSourceTypeLabel(scope.row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="90" align="center">
          <template #default="scope">
            {{ formatFileSize(scope.row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="uploadTime" label="上传时间" width="170" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)" size="small">
              <el-icon v-if="scope.row.status === '解析中'" class="is-loading"><Loading /></el-icon>
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="viewDocument(scope.row)">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button size="small" type="danger" @click="deleteDocument(scope.row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        style="margin-top: 20px"
      />
    </el-card>

    <el-dialog v-model="previewDialogVisible" title="文档预览" width="800px">
      <div v-if="currentDocument" class="document-preview">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="文档ID">
            {{ currentDocument.documentId }}
          </el-descriptions-item>
          <el-descriptions-item label="文件名">
            {{ currentDocument.fileName }}
          </el-descriptions-item>
          <el-descriptions-item label="文件类型">
            {{ currentDocument.fileType }}
          </el-descriptions-item>
          <el-descriptions-item label="大小">
            {{ formatFileSize(currentDocument.size) }}
          </el-descriptions-item>
          <el-descriptions-item label="上传时间" :span="2">
            {{ currentDocument.uploadTime }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">解析结果</el-divider>

        <div v-if="documentContent" class="content-preview">
          <el-card shadow="never">
            <div class="preview-content">{{ documentContent }}</div>
          </el-card>
        </div>
        <el-empty v-else description="暂无解析内容" />

        <div v-if="parsedChunks && parsedChunks.length > 0" class="parsed-chunks">
          <el-divider content-position="left">结构化知识片段</el-divider>
          <el-collapse v-model="activeChunks">
            <el-collapse-item
              v-for="(chunk, index) in parsedChunks"
              :key="index"
              :title="chunk.title || `片段 ${index + 1}`"
              :name="index"
            >
              <div class="chunk-content">{{ chunk.content }}</div>
              <div v-if="chunk.metadata" class="chunk-metadata">
                <el-tag size="small" v-for="(value, key) in chunk.metadata" :key="key" style="margin-right: 5px">
                  {{ key }}: {{ value }}
                </el-tag>
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { UploadFilled, Plus, Document, Close, Upload, Coin, Search, Refresh, View, Delete, Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { documentAPI } from '@/api'

const uploadRef = ref(null)
const fileList = ref([])
const documents = ref([])
const loading = ref(false)
const uploading = ref(false)
const searchQuery = ref('')
const selectedEquipmentType = ref('') // 设备类型选择
const selectedSourceType = ref('auto') // 文档来源类型，默认为自动分类

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const pollingInterval = ref(null)

const previewDialogVisible = ref(false)
const currentDocument = ref(null)
const documentContent = ref('')
const parsedChunks = ref([])
const activeChunks = ref([])

const handleFileChange = (file, fileListParam) => {
  const isValidSize = file.size / 1024 / 1024 < 50
  const isValidType = ['pdf', 'doc', 'docx', 'txt'].includes(
    file.name.split('.').pop().toLowerCase()
  )

  if (!isValidType) {
    ElMessage.error('不支持的文件格式，仅支持 PDF、DOC、DOCX、TXT 格式')
    return
  }
  if (!isValidSize) {
    ElMessage.error('文件大小不能超过 50MB')
    return
  }

  fileList.value = fileListParam
}

const handleExceed = () => {
  ElMessage.warning(`最多只能上传 10 个文件`)
}

const clearFileList = () => {
  fileList.value = []
}

const removeFile = (index) => {
  fileList.value.splice(index, 1)
}

const submitUpload = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  uploading.value = true
  let successCount = 0
  let failCount = 0

  for (const fileItem of fileList.value) {
    const formData = new FormData()
    formData.append('file', fileItem.raw)
    formData.append('sourceType', selectedSourceType.value)
    if (selectedEquipmentType.value) {
      formData.append('equipmentType', selectedEquipmentType.value)
    }
    formData.append('persistToKnowledgeBase', 'true')

    try {
      const response = await documentAPI.upload(formData)
      if (response && response.documentId) {
        documents.value.unshift({
          documentId: response.documentId,
          fileName: fileItem.name,
          fileType: fileItem.name.split('.').pop(),
          size: fileItem.size,
          uploadTime: new Date().toISOString(),
          status: response.status || '解析中',
          sourceType: response.sourceType || 'unknown'
        })
        successCount++
      }
    } catch (error) {
      console.error('上传失败:', error)
      failCount++
    }
  }

  uploading.value = false
  fileList.value = []
  selectedEquipmentType.value = '' // 重置设备类型选择
  selectedSourceType.value = 'auto' // 重置文档来源类型

  if (successCount > 0) {
    ElMessage.success(`成功上传 ${successCount} 个文件`)
  }
  if (failCount > 0) {
    ElMessage.error(`${failCount} 个文件上传失败`)
  }

  total.value = documents.value.length
}

const handleUpload = () => {
  uploadRef.value.$refs.input.click()
}

const searchDocuments = () => {
  if (!searchQuery.value.trim()) {
    loadDocuments()
    return
  }
  loading.value = true
  setTimeout(() => {
    const filtered = documents.value.filter(doc =>
      doc.fileName.toLowerCase().includes(searchQuery.value.toLowerCase())
    )
    documents.value = filtered
    total.value = filtered.length
    loading.value = false
  }, 300)
}

const viewDocument = async (document) => {
  currentDocument.value = document
  previewDialogVisible.value = true

  try {
    const contentResponse = await documentAPI.getDocumentContent(document.documentId)
    if (contentResponse) {
      documentContent.value = contentResponse.content || JSON.stringify(contentResponse, null, 2)
      parsedChunks.value = contentResponse.chunks || []
    }
  } catch (error) {
    console.error('获取文档内容失败:', error)
    documentContent.value = '获取文档内容失败'
  }
}

const deleteDocument = async (document) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文档 "${document.fileName}" 吗？删除后将无法恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await documentAPI.deleteDocument(document.documentId)
    documents.value = documents.value.filter(doc => doc.documentId !== document.documentId)
    total.value--
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const getFileTypeTag = (fileType) => {
  const map = {
    'pdf': 'danger',
    'doc': 'primary',
    'docx': 'primary',
    'txt': 'info',
    'xlsx': 'success',
    'xls': 'success',
    'csv': 'warning'
  }
  return map[fileType?.toLowerCase()] || 'info'
}

const getStatusTagType = (status) => {
  const map = {
    '已解析': 'success',
    '解析中': 'warning',
    '待解析': 'info',
    '解析失败': 'danger'
  }
  return map[status] || 'info'
}

const getSourceTypeLabel = (sourceType) => {
  const map = {
    'equipment_manual': '设备手册',
    'maintenance_record': '维修记录',
    'industry_standard': '行业标准',
    'theory_paper': '理论文献',
    'user_feedback': '用户反馈',
    'mixed_collection': '杂文集',
    'unknown': '未知'
  }
  return map[sourceType] || '未知'
}

const getSourceTypeTag = (sourceType) => {
  const map = {
    'equipment_manual': 'primary',
    'maintenance_record': 'warning',
    'industry_standard': 'success',
    'theory_paper': 'info',
    'user_feedback': '',
    'mixed_collection': 'danger',
    'unknown': 'info'
  }
  return map[sourceType] || 'info'
}


const loadDocuments = async () => {
  loading.value = true
  searchQuery.value = ''
  try {
    const response = await documentAPI.getDocumentList()
    if (response && Array.isArray(response)) {
      documents.value = response
    } else if (response && response.data) {
      documents.value = response.data
    } else {
      documents.value = []
    }
    total.value = documents.value.length
  } catch (error) {
    console.error('加载文档列表失败:', error)
  } finally {
    loading.value = false
  }
}

const pollDocumentStatus = async () => {
  try {
    const response = await documentAPI.getDocumentList()
    if (response && Array.isArray(response)) {
      const newDocs = response
      let hasChanges = false
      for (const newDoc of newDocs) {
        const existingDoc = documents.value.find(d => d.documentId === newDoc.documentId)
        if (existingDoc) {
          if (existingDoc.status !== newDoc.status) {
            existingDoc.status = newDoc.status
            existingDoc.sourceType = newDoc.sourceType
            hasChanges = true
          }
        }
      }
      if (hasChanges) {
        documents.value = [...documents.value]
      }
    }
  } catch (error) {
    console.error('轮询文档状态失败:', error)
  }
}

const handleSizeChange = (size) => {
  pageSize.value = size
  loadDocuments()
}

const handleCurrentChange = (current) => {
  currentPage.value = current
  loadDocuments()
}

onMounted(() => {
  loadDocuments()
  pollingInterval.value = setInterval(() => {
    const hasProcessing = documents.value.some(doc => doc.status === '解析中')
    if (hasProcessing) {
      pollDocumentStatus()
    }
  }, 5000)
})

onUnmounted(() => {
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value)
  }
})
</script>

<style scoped>
.document-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 120px);
}

.document-card {
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.card-header {
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

.upload-section {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  padding: 28px;
  margin-bottom: 24px;
  border: 2px dashed #c7d2fe;
  transition: border-color 0.25s ease, box-shadow 0.25s ease;
}

.upload-section:hover {
  border-color: #818cf8;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.1);
}

.upload-demo {
  width: 100%;
}

.upload-content {
  padding: 40px 20px;
  text-align: center;
}

.upload-icon {
  font-size: 48px;
  color: #6366f1;
  margin-bottom: 16px;
  transition: transform 0.25s ease;
}

.upload-section:hover .upload-icon {
  transform: scale(1.1);
}

.upload-text {
  font-size: 16px;
  color: #475569;
  margin-bottom: 8px;
}

.upload-link {
  color: #6366f1;
  font-weight: 600;
}

.upload-tip {
  font-size: 13px;
  color: #94a3b8;
}

.file-queue {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #dcdfe6;
}

.upload-options {
  margin: 16px 0;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.form-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.queue-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
  color: #606266;
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: all 0.2s ease;
}

.file-item:hover {
  border-color: #c7d2fe;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.1);
}

.file-icon {
  font-size: 18px;
  color: #6366f1;
}

.file-name {
  flex: 1;
  font-size: 14px;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.file-size {
  font-size: 12px;
  color: #94a3b8;
}

.remove-icon {
  cursor: pointer;
  color: #94a3b8;
  transition: color 0.2s ease;
  padding: 4px;
  border-radius: 4px;
}

.remove-icon:hover {
  color: #ef4444;
  background: #fef2f2;
}

.upload-actions {
  display: flex;
  justify-content: center;
}

.document-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.document-preview {
  max-height: 60vh;
  overflow-y: auto;
}

.content-preview {
  margin-top: 10px;
}

.preview-content {
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
  max-height: 300px;
  overflow-y: auto;
  background: #f5f7fa;
  padding: 16px;
  border-radius: 6px;
}

.parsed-chunks {
  margin-top: 20px;
}

.chunk-content {
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  white-space: pre-wrap;
  padding: 10px 0;
}

.chunk-metadata {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #eee;
}

:deep(.el-divider__text) {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #909399;
  font-size: 14px;
}

:deep(.el-table) {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th) {
  background-color: #f5f7fa !important;
  color: #606266;
  font-weight: 600;
}
</style>
