<template>
  <div class="knowledge-graph-container">
    <el-card class="knowledge-graph-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon class="header-icon"><Connection /></el-icon>
            <span class="header-title">知识图谱</span>
            <el-tag type="info" size="small" effect="plain" class="stats-tag">
              <el-icon><DataLine /></el-icon>
              {{ stats.nodeCount }} 节点 | {{ stats.relationCount }} 关系
            </el-tag>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="loadGraphData" class="action-btn">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <div class="graph-wrapper">
        <div ref="graphContainer" class="graph-container"></div>
        
        <div class="graph-legend">
          <div class="legend-title">
            <el-icon><Menu /></el-icon>
            图例说明
          </div>
          <div class="legend-item" v-for="(item, index) in legendItems" :key="index">
            <div 
              v-if="item.type === 'dot'" 
              class="legend-dot" 
              :style="{ backgroundColor: item.color }"
            ></div>
            <div 
              v-if="item.type === 'line'" 
              class="legend-line" 
              :style="{ borderColor: item.color }"
            ></div>
            <span class="legend-text">{{ item.label }}</span>
          </div>
        </div>

        <div v-if="loading" class="loading-overlay">
          <el-icon class="is-loading loading-icon"><Loading /></el-icon>
          <span class="loading-text">正在加载知识图谱...</span>
        </div>
      </div>

      <div class="graph-controls">
        <el-button-group class="zoom-controls">
          <el-button type="primary" @click="zoomIn" class="control-btn">
            <el-icon><ZoomIn /></el-icon>
            放大
          </el-button>
          <el-button type="primary" @click="zoomOut" class="control-btn">
            <el-icon><ZoomOut /></el-icon>
            缩小
          </el-button>
          <el-button type="primary" @click="resetView" class="control-btn">
            <el-icon><FullScreen /></el-icon>
            重置
          </el-button>
        </el-button-group>
      </div>
    </el-card>

    <!-- 节点详情对话框 -->
    <el-dialog
      v-model="showNodeDetail"
      title="节点详情"
      width="500px"
      :close-on-click-modal="false"
    >
      <div v-if="selectedNode" class="node-detail-content">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="节点名称">
            <span class="node-name">{{ selectedNode.name }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="节点类型">
            <el-tag :type="selectedNode.type === 'GLOBAL' ? 'danger' : 'success'">
              {{ selectedNode.type === 'GLOBAL' ? '全局事件' : '用户事件' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="事件类型">
            <el-tag :type="getEventTypeTagType(selectedNode.eventType)">
              {{ selectedNode.eventType || '未知' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="设备类型">
            {{ selectedNode.equipmentType || '通用' }}
          </el-descriptions-item>
          <el-descriptions-item label="严重程度">
            <el-tag v-if="selectedNode.severity" :type="getSeverityTagType(selectedNode.severity)">
              {{ selectedNode.severity }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="描述">
            {{ selectedNode.description || '暂无描述' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import {
  Search,
  Refresh,
  ZoomIn,
  ZoomOut,
  FullScreen,
  Connection,
  Menu,
  Loading,
  DataLine,
  Document,
  Close
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { knowledgeGraphAPI } from '@/api'

const graphContainer = ref(null)
let graphInstance = null
const loading = ref(false)
const showNodeDetail = ref(false)
const selectedNode = ref(null)

const stats = ref({
  nodeCount: 0,
  relationCount: 0
})

const originalNodesData = ref([])

const legendItems = [
  { type: 'dot', color: '#3B82F6', label: '全局事件' },
  { type: 'dot', color: '#22C55E', label: '用户事件' },
  { type: 'dot', color: '#EF4444', label: '顶事件' },
  { type: 'dot', color: '#F97316', label: '中间事件' },
  { type: 'dot', color: '#6366F1', label: '底事件' },
  { type: 'line', color: '#475569', label: '因果关系' }
]

const getNodeColor = (node) => {
  // 优先按 eventType 判断颜色
  if (node.eventType === '顶事件') {
    return '#EF4444'
  } else if (node.eventType === '中间事件') {
    return '#F97316'
  } else if (node.eventType === '底事件') {
    return '#6366F1'
  } else if (node.type === 'GLOBAL') {
    return '#3B82F6'
  } else if (node.type === 'USER') {
    return '#22C55E'
  } else {
    return '#22C55E' // 默认用户事件
  }
}

const getNodeSize = (node) => {
  // 优先按 eventType 判断大小
  if (node.eventType === '顶事件') {
    return 60
  } else if (node.eventType === '中间事件') {
    return 52
  } else if (node.eventType === '底事件') {
    return 46
  } else if (node.type === 'GLOBAL') {
    return 50
  }
  return 44
}

const getEventTypeTagType = (eventType) => {
  switch (eventType) {
    case '顶事件': return 'danger'
    case '中间事件': return 'warning'
    case '底事件': return 'info'
    default: return ''
  }
}

const getSeverityTagType = (severity) => {
  switch (severity) {
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'warning'
    case 'MEDIUM': return 'info'
    case 'LOW': return 'success'
    default: return ''
  }
}

const initChart = () => {
  if (!graphContainer.value) return

  graphInstance = echarts.init(graphContainer.value, null, { renderer: 'canvas' })

  const option = {
    title: {
      text: '知识图谱',
      subtext: '提示：点击节点查看详情，空白区域拖动可以拖动画布，滚轮可以缩放',
      left: 'center',
      textStyle: {
        fontSize: 18,
        fontWeight: 600,
        color: '#334155'
      },
      subtextStyle: {
        fontSize: 13,
        color: '#64748B'
      }
    },
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.95)',
      borderColor: '#64748B',
      borderWidth: 1,
      textStyle: { color: '#F8FAFC' },
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `<div style="font-weight:600;margin-bottom:4px">${params.data.name}</div>
                  <div>类型: ${params.data.type || '用户事件'}</div>
                  <div>事件类型: ${params.data.eventType || '未知'}</div>`
        } else if (params.dataType === 'edge') {
          return `<div style="font-weight:600">因果关系</div>
                  <div>${params.data.name || '关联'}</div>`
        }
        return params.name
      }
    },
    categories: [
      { name: '全局事件', itemStyle: { color: '#3B82F6' } },
      { name: '用户事件', itemStyle: { color: '#22C55E' } },
      { name: '顶事件', itemStyle: { color: '#EF4444' } },
      { name: '中间事件', itemStyle: { color: '#F97316' } },
      { name: '底事件', itemStyle: { color: '#6366F1' } }
    ],
    series: [{
      type: 'graph',
      layout: 'force',
      name: '知识图谱',
      draggable: true,
      roam: true,
      symbol: 'circle',
      symbolSize: (value, params) => {
        if (!params.data) return 40
        return getNodeSize(params.data)
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [8, 14],
      force: {
        repulsion: 800,
        gravity: 0.08,
        edgeLength: 250,
        layoutAnimation: true
      },
      lineStyle: {
        color: '#475569',
        width: 3,
        curveness: 0.1
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          width: 5,
          color: '#F97316'
        }
      },
      label: {
        show: true,
        position: 'right',
        formatter: '{b}',
        fontSize: 13,
        color: '#1E293B',
        fontWeight: 600,
        backgroundColor: 'rgba(255,255,255,0.9)',
        padding: [3, 6],
        borderRadius: 4,
        borderColor: '#E2E8F0',
        borderWidth: 1
      },
      itemStyle: {
        borderColor: '#FFFFFF',
        borderWidth: 3,
        shadowColor: 'rgba(0,0,0,0.25)',
        shadowBlur: 15,
        shadowOffsetY: 4
      },
      data: [],
      links: []
    }],
    animation: true,
    animationDuration: 1000,
    animationEasing: 'cubicOut'
  }

  graphInstance.setOption(option)

  // 添加节点点击事件监听
  graphInstance.on('click', (params) => {
    console.log('Node clicked:', params)
    if (params.dataType === 'node') {
      handleNodeClick(params.data)
    }
  })

  window.addEventListener('resize', handleResize)
}

const handleNodeClick = (nodeData) => {
  console.log('=== Node clicked, data:', nodeData)
  console.log('=== Looking in originalNodes:', originalNodesData.value)
  
  // 在原始节点数据中查找完整信息
  const originalNode = originalNodesData.value.find(n => n.id === nodeData.id)
  console.log('=== Found original node:', originalNode)
  
  if (originalNode) {
    selectedNode.value = originalNode
  } else {
    selectedNode.value = nodeData
  }
  showNodeDetail.value = true
}

const transformBackendData = (backendData) => {
  if (!backendData || !backendData.nodes) {
    return { nodes: [], links: [] }
  }

  console.log('=== Transforming backend data:', backendData)

  // 保存完整的原始节点数据，用于详情展示
  originalNodesData.value = [...backendData.nodes]

  const nodes = backendData.nodes.map(node => {
    // 确定 category 索引 - 优先按 eventType 分类
    let categoryIndex = 0
    if (node.eventType === '顶事件') categoryIndex = 2
    else if (node.eventType === '中间事件') categoryIndex = 3
    else if (node.eventType === '底事件') categoryIndex = 4
    else if (node.type === 'USER') categoryIndex = 1
    else categoryIndex = 0 // 默认全局事件

    console.log('=== Node:', node, '→ category:', categoryIndex)

    return {
      id: node.id,
      name: node.name || node.id,
      type: node.type,
      eventType: node.eventType,
      equipmentType: node.equipmentType,
      severity: node.severity,
      description: node.description,
      userId: node.userId,
      docId: node.docId,
      category: categoryIndex,
      itemStyle: {
        color: getNodeColor(node)
      },
      symbolSize: getNodeSize(node),
      value: 1
    }
  })

  const links = backendData.relationships.map((rel, index) => ({
    id: `link_${index}`,
    source: rel.source,
    target: rel.target,
    name: rel.description || '因果关系',
    description: rel.description,
    gateType: rel.gateType,
    confidence: rel.confidence
  }))

  console.log('=== Transformed nodes complete:', nodes)
  console.log('=== Transformed links complete:', links)

  return { nodes, links }
}

const updateChart = (nodes, links) => {
  if (!graphInstance) return

  console.log('=== Updating chart ===')
  console.log('=== Input nodes:', nodes)
  console.log('=== Input links:', links)

  // 完整更新配置，确保 categories 和颜色都正确应用
  const option = {
    categories: [
      { name: '全局事件', itemStyle: { color: '#3B82F6' } },
      { name: '用户事件', itemStyle: { color: '#22C55E' } },
      { name: '顶事件', itemStyle: { color: '#EF4444' } },
      { name: '中间事件', itemStyle: { color: '#F97316' } },
      { name: '底事件', itemStyle: { color: '#6366F1' } }
    ],
    series: [{
      data: nodes.map(node => ({
        id: node.id,
        name: node.name || node.id,
        type: node.type,
        eventType: node.eventType,
        equipmentType: node.equipmentType,
        severity: node.severity,
        description: node.description,
        userId: node.userId,
        docId: node.docId,
        category: node.category,
        itemStyle: node.itemStyle,
        symbolSize: node.symbolSize,
        value: 1
      })),
      links: links.map(link => ({
        id: link.id,
        source: link.source,
        target: link.target,
        name: link.name || link.description,
        description: link.description,
        gateType: link.gateType,
        confidence: link.confidence
      })),
      lineStyle: {
        color: '#475569',
        width: 3,
        curveness: 0.1
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          width: 5,
          color: '#F97316'
        }
      },
      label: {
        show: true,
        position: 'right',
        formatter: '{b}',
        fontSize: 13,
        color: '#1E293B',
        fontWeight: 600,
        backgroundColor: 'rgba(255,255,255,0.9)',
        padding: [3, 6],
        borderRadius: 4,
        borderColor: '#E2E8F0',
        borderWidth: 1
      }
    }]
  }

  // 使用 notMerge: true 来确保完全更新配置
  graphInstance.setOption(option, { notMerge: false })
  
  // 强制重新渲染
  setTimeout(() => {
    if (graphInstance) {
      graphInstance.resize()
      graphInstance.getZr().refresh()
    }
  }, 100)
  
  console.log('=== Chart update complete ===')
}

const loadGraphData = async () => {
  loading.value = true
  try {
    const userId = localStorage.getItem('userId') || 'anonymous'
    console.log('===== Calling API with userId:', userId, ' =====')
    const response = await knowledgeGraphAPI.getData(userId)

    console.log('===== API Response =====')
    console.log('Full response:', response)
    console.log('Response type:', typeof response)
    console.log('Has nodes:', response && response.nodes)
    console.log('Nodes type:', response && response.nodes && typeof response.nodes)
    console.log('Nodes length:', response && response.nodes && response.nodes.length)
    console.log('Has relationships:', response && response.relationships)
    console.log('Relationships type:', response && response.relationships && typeof response.relationships)
    console.log('Relationships length:', response && response.relationships && response.relationships.length)
    console.log('All response keys:', Object.keys(response || {}))
    console.log('=======================')

    if (response && response.nodes) {
      stats.value.nodeCount = response.nodes.length
      stats.value.relationCount = response.relationships?.length || 0
      const { nodes, links } = transformBackendData(response)
      updateChart(nodes, links)
    } else {
      console.log('No nodes in response, loading mock data')
      loadMockData()
    }
  } catch (error) {
    console.error('===== ERROR =====')
    console.error('Failed to load knowledge graph:', error)
    console.error('=================')
    loadMockData()
  } finally {
    loading.value = false
  }
}

const loadMockData = () => {
  const mockBackendData = {
    nodes: [
      { id: 'global_1', name: '电机过热', type: 'GLOBAL', eventType: '顶事件', equipmentType: '电机', severity: 'HIGH', description: '电机过热导致设备停机' },
      { id: 'global_2', name: '轴承磨损', type: 'GLOBAL', eventType: '底事件', equipmentType: '通用', severity: 'MEDIUM', description: '机械设备轴承磨损导致的故障' },
      { id: 'global_3', name: '润滑不良', type: 'GLOBAL', eventType: '底事件', equipmentType: '通用', severity: 'MEDIUM', description: '设备润滑系统工作不良' },
      { id: 'global_4', name: '过载', type: 'GLOBAL', eventType: '底事件', equipmentType: '通用', severity: 'HIGH', description: '设备超出额定负载运行' },
      { id: 'global_5', name: '冷却系统故障', type: 'GLOBAL', eventType: '中间事件', equipmentType: '通用', severity: 'HIGH', description: '设备冷却系统失效' },
      { id: 'global_6', name: '振动异常', type: 'GLOBAL', eventType: '中间事件', equipmentType: '通用', severity: 'MEDIUM', description: '设备运行时振动异常' }
    ],
    relationships: [
      { source: 'global_2', target: 'global_6', description: '轴承磨损导致振动异常' },
      { source: 'global_3', target: 'global_5', description: '过载导致冷却系统负荷过大' },
      { source: 'global_4', target: 'global_2', description: '润滑不良导致轴承磨损' },
      { source: 'global_5', target: 'global_1', description: '冷却系统故障导致电机过热' },
      { source: 'global_6', target: 'global_1', description: '振动异常导致电机过热' }
    ]
  }

  stats.value.nodeCount = mockBackendData.nodes.length
  stats.value.relationCount = mockBackendData.relationships.length

  const { nodes, links } = transformBackendData(mockBackendData)
  updateChart(nodes, links)
}

const zoomIn = () => {
  if (graphInstance) {
    graphInstance.dispatchAction({ type: 'zoom', scale: 1.2 })
  }
}

const zoomOut = () => {
  if (graphInstance) {
    graphInstance.dispatchAction({ type: 'zoom', scale: 0.8 })
  }
}

const resetView = () => {
  if (graphInstance) {
    graphInstance.dispatchAction({ type: 'restore' })
  }
}

const handleResize = () => {
  if (graphInstance) {
    graphInstance.resize()
  }
}

onMounted(() => {
  initChart()
  loadGraphData()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (graphInstance) {
    graphInstance.dispose()
    graphInstance = null
  }
})
</script>

<style scoped>
.knowledge-graph-container {
  padding: 24px;
  background-color: #F8FAFC;
  min-height: calc(100vh - 120px);
}

.knowledge-graph-card {
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0,0,0,0.08);
  border: 1px solid #E2E8F0;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  padding: 4px 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-icon {
  font-size: 24px;
  color: #64748B;
}

.header-title {
  font-size: 20px;
  font-weight: 600;
  color: #334155;
}

.stats-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.action-btn {
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.graph-wrapper {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
}

.graph-container {
  width: 100%;
  height: 750px;
  border-radius: 12px;
  background: linear-gradient(135deg, #F8FAFC 0%, #F1F5F9 100%);
  border: 1px solid #E2E8F0;
  position: relative;
  overflow: hidden;
  cursor: move;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(248, 250, 252, 0.95);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #334155;
  font-size: 15px;
  font-weight: 500;
  z-index: 10;
  backdrop-filter: blur(4px);
}

.loading-icon {
  font-size: 40px;
  color: #F97316;
}

.graph-legend {
  position: absolute;
  top: 20px;
  right: 20px;
  background: rgba(248, 250, 252, 0.98);
  border: 1px solid #E2E8F0;
  border-radius: 12px;
  padding: 16px;
  z-index: 5;
  min-width: 160px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  backdrop-filter: blur(8px);
}

.legend-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 12px;
  font-size: 14px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #334155;
}

.legend-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 2px solid #F8FAFC;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.legend-line {
  width: 24px;
  height: 3px;
  border-radius: 2px;
  flex-shrink: 0;
  background: #475569;
}

.graph-controls {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  justify-content: center;
  align-items: center;
}

.zoom-controls {
  border-radius: 10px;
  overflow: hidden;
}

.control-btn {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.2s ease;
}
</style>
