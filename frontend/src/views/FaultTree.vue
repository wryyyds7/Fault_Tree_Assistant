<template>
  <div class="fault-tree-container" aria-label="故障树管理页面">
    <el-card class="fault-tree-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon" aria-label="故障树"><Share /></el-icon>
            <span>故障树管理</span>
          </div>
          <el-button type="primary" class="create-btn" @click="createFaultTree" aria-label="创建故障树">
            <el-icon><Plus /></el-icon>
            创建故障树
          </el-button>
        </div>
      </template>

      <div class="toolbar-section">
        <div class="search-wrapper">
          <el-input
            v-model="searchQuery"
            placeholder="搜索故障树名称或设备类型"
            style="width: 260px"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
            aria-label="搜索故障树"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" plain @click="handleSearch" aria-label="搜索">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </div>
        <div class="action-wrapper">
          <el-button type="info" @click="loadFaultTrees" aria-label="刷新列表">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </div>

      <el-table :data="faultTrees" style="width: 100%" v-loading="loading" stripe :border="false" class="fault-tree-table" :row-class-name="()=>'ft-row'">
        <el-table-column prop="treeId" label="故障树ID" width="180" show-overflow-tooltip />
        <el-table-column prop="name" label="故障树名称" min-width="200">
          <template #default="scope">
            <div class="tree-name-cell">
              <el-icon class="tree-icon" aria-label="树"><Connection /></el-icon>
              <span>{{ scope.row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="equipmentType" label="设备类型" width="120" align="center">
          <template #default="scope">
            <el-tag size="small" :type="getEquipmentTypeTag(scope.row.equipmentType)">
              {{ getEquipmentTypeText(scope.row.equipmentType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="scope">
            <span class="time-text">{{ formatDate(scope.row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="scope">
            <span class="time-text">{{ formatDate(scope.row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)" size="small">
              <el-icon v-if="scope.row.status === 'DRAFT'" class="is-loading"><Loading /></el-icon>
              {{ getStatusText(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="editFaultTree(scope.row)" aria-label="编辑">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button size="small" type="success" @click="viewFaultTree(scope.row)" aria-label="查看">
              <el-icon><View /></el-icon>
              查看
            </el-button>
            <el-button size="small" type="danger" @click="deleteFaultTree(scope.row)" aria-label="删除">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailDialogVisible"
      title="故障树详情"
      width="800px"
      :destroy-on-close="true"
    >
      <div v-if="currentTree" class="tree-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="故障树ID">
            <span class="detail-id">{{ currentTree.treeId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="故障树名称">
            <span class="detail-name">{{ currentTree.name }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="设备类型">
            <el-tag size="small">{{ getEquipmentTypeText(currentTree.equipmentType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(currentTree.status)">
              {{ getStatusText(currentTree.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">
            {{ currentTree.createdBy || '系统' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatDate(currentTree.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">
            <span class="description-text">{{ currentTree.description || '暂无描述' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">
          <el-icon><DataLine /></el-icon>
          故障树预览
        </el-divider>
        <div class="tree-preview">
          <FaultTreeChart
            v-if="currentTree.treeData"
            :treeData="currentTree.treeData"
            :highlightLowConfidence="true"
          />
          <el-empty v-else description="暂无故障树数据" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search, Refresh, Share, Plus, Connection, Edit, View, Delete, Loading, DataLine } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { faultTreeAPI } from '@/api'
import FaultTreeChart from '@/components/FaultTreeChart.vue'

const router = useRouter()

const loading = ref(false)
const searchQuery = ref('')
const faultTrees = ref([])

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const detailDialogVisible = ref(false)
const currentTree = ref(null)

const createFaultTree = () => {
  router.push('/fault-tree/edit/new')
}

const editFaultTree = (faultTree) => {
  router.push(`/fault-tree/edit/${faultTree.treeId}`)
}

const viewFaultTree = async (faultTree) => {
  try {
    const response = await faultTreeAPI.getById(faultTree.treeId)
    currentTree.value = response
    detailDialogVisible.value = true
  } catch (error) {
    console.error('获取故障树详情失败:', error)
    ElMessage.error('获取详情失败')
  }
}

const deleteFaultTree = async (faultTree) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除故障树 "${faultTree.name}" 吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await faultTreeAPI.delete(faultTree.treeId)
    faultTrees.value = faultTrees.value.filter(tree => tree.treeId !== faultTree.treeId)
    total.value--
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadFaultTrees()
}

const loadFaultTrees = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      pageSize: pageSize.value,
      search: searchQuery.value
    }

    const response = await faultTreeAPI.getAll(params)

    if (response && Array.isArray(response)) {
      faultTrees.value = response
      total.value = response.length
    } else if (response && response.data) {
      faultTrees.value = response.data
      total.value = response.total || response.data.length
    } else {
      faultTrees.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('加载故障树列表失败:', error)
    loadMockData()
  } finally {
    loading.value = false
  }
}

const loadMockData = () => {
  faultTrees.value = [
    {
      treeId: 'tree_001',
      name: '电机过热故障树',
      equipmentType: 'motor',
      description: '针对电机过热故障的完整故障树分析，包含电源问题、轴承故障等多个分支',
      status: 'DRAFT',
      createdBy: 'admin',
      createdAt: '2026-03-15T10:30:00',
      updatedAt: '2026-03-20T14:20:00',
      treeData: {
        eventId: 'evt_001',
        eventName: '电机过热停机',
        eventType: 'TOP',
        gateType: 'OR',
        confidence: 0.95,
        children: [
          {
            eventId: 'evt_002',
            eventName: '电源问题',
            eventType: 'INTERMEDIATE',
            gateType: 'OR',
            confidence: 0.9,
            children: [
              {
                eventId: 'evt_003',
                eventName: '电压不稳定',
                eventType: 'BASIC',
                confidence: 0.85,
                children: []
              },
              {
                eventId: 'evt_004',
                eventName: '缺相',
                eventType: 'BASIC',
                confidence: 0.88,
                children: []
              }
            ]
          },
          {
            eventId: 'evt_005',
            eventName: '轴承故障',
            eventType: 'INTERMEDIATE',
            gateType: 'AND',
            confidence: 0.75,
            children: [
              {
                eventId: 'evt_006',
                eventName: '润滑不足',
                eventType: 'BASIC',
                confidence: 0.7,
                children: []
              },
              {
                eventId: 'evt_007',
                eventName: '磨损',
                eventType: 'BASIC',
                confidence: 0.65,
                children: []
              }
            ]
          }
        ]
      }
    },
    {
      treeId: 'tree_002',
      name: '液压泵压力不足',
      equipmentType: 'hydraulic_pump',
      description: '液压泵压力不足故障分析',
      status: 'PUBLISHED',
      createdBy: 'admin',
      createdAt: '2026-03-16T09:00:00',
      updatedAt: '2026-03-21T11:15:00',
      treeData: null
    },
    {
      treeId: 'tree_003',
      name: '传感器读数异常',
      equipmentType: 'sensor',
      description: '传感器读数异常故障诊断',
      status: 'DRAFT',
      createdBy: 'admin',
      createdAt: '2026-03-18T08:00:00',
      updatedAt: '2026-03-22T16:30:00',
      treeData: null
    }
  ]
  total.value = faultTrees.value.length
}

const getEquipmentTypeText = (type) => {
  const map = {
    motor: '电机',
    hydraulic_pump: '液压泵',
    sensor: '传感器',
    valve: '阀门',
    other: '其他'
  }
  return map[type] || type
}

const getEquipmentTypeTag = (type) => {
  const map = {
    motor: 'primary',
    hydraulic_pump: 'success',
    sensor: 'warning',
    valve: 'danger',
    other: 'info'
  }
  return map[type] || 'info'
}

const getStatusText = (status) => {
  const map = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档'
  }
  return map[status] || status
}

const getStatusTagType = (status) => {
  const map = {
    DRAFT: 'info',
    PUBLISHED: 'success',
    ARCHIVED: 'warning'
  }
  return map[status] || 'info'
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

const handleSizeChange = (size) => {
  pageSize.value = size
  loadFaultTrees()
}

const handleCurrentChange = (current) => {
  currentPage.value = current
  loadFaultTrees()
}

onMounted(() => {
  loadFaultTrees()
})
</script>

<style scoped>

.fault-tree-container {
  padding: 20px;
  background-color: #f1f5f9;
  min-height: calc(100vh - 120px);
}

.fault-tree-card {
  border-radius: 13px;
  box-shadow: 0 6px 32px rgba(30, 41, 59, 0.10);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0 2px 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.header-icon {
  font-size: 22px;
  color: #2563eb;
}

.create-btn {
  border-radius: 8px;
  font-size: 15px;
}

.toolbar-section {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  padding: 14px 10px;
  background: #f8fafc;
  border-radius: 9px;
  border: 1px solid #e5e7eb;
  gap: 10px;
}

.search-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}

.action-wrapper {
  display: flex;
  gap: 8px;
}

.tree-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tree-icon {
  font-size: 16px;
  color: #2563eb;
}

.time-text {
  font-size: 13px;
  color: #64748b;
}


.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}


.tree-detail {
  max-height: 60vh;
  overflow-y: auto;
}


.detail-id {
  font-family: monospace;
  font-size: 12px;
  color: #64748b;
}


.detail-name {
  font-weight: 600;
  color: #1e293b;
}


.description-text {
  color: #334155;
  line-height: 1.6;
}


.tree-preview {
  height: 400px;
  background-color: #f8fafc;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
}


:deep(.el-table) {
  border-radius: 8px;
  overflow: auto;
  background: #fff;
}
:deep(.el-table th) {
  background-color: #f1f5f9 !important;
  color: #334155;
  font-weight: 600;
}
:deep(.el-table .ft-row:hover) {
  background: #e0e7ef !important;
}
:deep(.el-divider__text) {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #334155;
  font-size: 14px;
}

@media (max-width: 900px) {
  .fault-tree-card {
    padding: 0;
  }
  .toolbar-section {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  .search-wrapper {
    width: 100%;
  }
  .action-wrapper {
    width: 100%;
    justify-content: flex-end;
  }
  :deep(.el-table) {
    font-size: 13px;
    min-width: 600px;
  }
  .tree-preview {
    height: 260px;
    padding: 8px;
  }
}
</style>
