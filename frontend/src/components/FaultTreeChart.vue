<template>
  <div ref="chartContainer" class="fault-tree-chart"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  treeData: {
    type: Object,
    default: null
  },
  highlightLowConfidence: {
    type: Boolean,
    default: true
  },
  confidenceThreshold: {
    type: Number,
    default: 0.8
  }
})

const emit = defineEmits(['node-click', 'node-expand', 'node-collapse', 'gate-click', 'gate-right-click'])

const chartContainer = ref(null)
let chartInstance = null

const eventTypeColor = {
  TOP: '#409eff',
  INTERMEDIATE: '#67c23a',
  BASIC: '#e6a23c'
}

const getEventTypeColor = (eventType) => {
  return eventTypeColor[eventType] || '#909399'
}

const getNodeStyle = (node) => {
  const baseColor = getEventTypeColor(node.eventType)
  let borderColor = '#67c23a'

  if (props.highlightLowConfidence && node.confidence !== undefined && node.confidence !== null) {
    if (node.confidence < 0.6) {
      borderColor = '#f56c6c'
    } else if (node.confidence < 0.8) {
      borderColor = '#e6a23c'
    }
  }

  let backgroundColor = baseColor
  if (node.verificationStatus === 'PENDING') {
    backgroundColor = baseColor + '80'
  } else if (node.verificationStatus === 'REJECTED') {
    backgroundColor = '#f56c6c'
  }

  return {
    color: '#fff',
    backgroundColor: backgroundColor,
    borderColor: borderColor,
    borderWidth: node.aiGenerated ? 2 : 1
  }
}

const getGateSymbol = (gateType) => {
  const symbols = {
    'AND': 'M-15,-15 L15,-15 L15,15 L-15,15 Z M-10,-5 L10,-5 M-10,5 L10,5',
    'OR': 'M-15,-15 Q0,0 15,15 M15,-15 Q0,0 -15,15',
    'XOR': 'M-15,-15 L15,15 M15,-15 L-15,15',
    'NOT': 'M-15,-15 L15,-15 L15,15 L-15,15 Z M5,-5 L15,0 L5,5'
  }
  return symbols[gateType] || symbols['OR']
}

const convertNodeToEcharts = (node, isRoot = false) => {
  if (!node) {
    console.warn('FaultTreeChart: convertNodeToEcharts 收到空节点')
    return null
  }

  const nodeStyle = getNodeStyle(node)
  const hasChildren = node.children && node.children.length > 0
  const isGateNode = hasChildren && node.eventType !== 'BASIC'

  const echartsNode = {
    name: node.eventName || node.name || '',
    eventId: node.eventId,
    eventName: node.eventName,
    eventType: node.eventType,
    gateType: node.gateType,
    confidence: node.confidence,
    verificationStatus: node.verificationStatus,
    aiGenerated: node.aiGenerated,
    sourceDetail: node.sourceDetail,
    bgColor: node.bgColor,
    textColor: node.textColor,
    isGateNode: isGateNode,
    value: isRoot ? 3 : (hasChildren ? 2 : 1),
    symbol: isGateNode ? 'path://' + getGateSymbol(node.gateType) : 'roundRect',
    symbolSize: isGateNode ? [50, 50] : (node.eventType === 'TOP' ? 80 : node.eventType === 'INTERMEDIATE' ? 60 : 50),
    itemStyle: {
      color: isGateNode ? '#8B5CF6' : (node.bgColor || nodeStyle.backgroundColor),
      borderColor: isGateNode ? '#7C3AED' : nodeStyle.borderColor,
      borderWidth: isGateNode ? 3 : nodeStyle.borderWidth
    },
    children: node.children ? node.children.map(child => convertNodeToEcharts(child, false)).filter(Boolean) : []
  }

  return echartsNode
}

const initChart = () => {
  if (!chartContainer.value) return

  chartInstance = echarts.init(chartContainer.value)

  const option = {
    title: {
      text: '故障树结构图',
      left: 'center',
      textStyle: {
        fontSize: 16,
        fontWeight: 'bold'
      }
    },
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(30, 41, 59, 0.95)',
      borderColor: '#475569',
      borderWidth: 1,
      borderRadius: 8,
      padding: [12, 16],
      textStyle: {
        color: '#f1f5f9',
        fontSize: 13
      },
      formatter: (params) => {
        const node = params.data
        if (!node) return ''
        if (node.isGateNode) {
          return `<div style="font-weight: 600; color: #a78bfa;">逻辑门: ${node.gateType || 'OR'}</div>
                  <div style="color: #94a3b8; font-size: 12px;">点击节点可编辑此逻辑门</div>`
        }
        const typeMap = { TOP: { text: '顶事件', color: '#60a5fa' }, INTERMEDIATE: { text: '中间事件', color: '#34d399' }, BASIC: { text: '底事件', color: '#fbbf24' } }
        const statusMap = { PENDING: '待确认', CONFIRMED: '已确认', REJECTED: '已驳回' }
        const statusColors = { PENDING: '#fbbf14', CONFIRMED: '#34d399', REJECTED: '#f87171' }
        let html = `<div style="font-weight: 600; margin-bottom: 8px; font-size: 14px; color: #fff; border-bottom: 1px solid #475569; padding-bottom: 8px;">${node.eventName || node.name}</div>`
        html += `<div style="margin-bottom: 4px;"><span style="color: #94a3b8;">类型：</span><span style="color: ${typeMap[node.eventType]?.color}; font-weight: 500;">${typeMap[node.eventType]?.text || node.eventType || ''}</span></div>`
        if (node.gateType) {
          html += `<div style="margin-bottom: 4px;"><span style="color: #94a3b8;">逻辑门：</span><span style="color: #c4b5fd;">${node.gateType}</span></div>`
        }
        if (node.confidence !== undefined && node.confidence !== null) {
          const confPercent = (node.confidence * 100).toFixed(1)
          const confColor = node.confidence < 0.6 ? '#f87171' : node.confidence < 0.8 ? '#fbbf14' : '#34d399'
          html += `<div style="margin-bottom: 4px;"><span style="color: #94a3b8;">置信度：</span><span style="color: ${confColor}; font-weight: 600;">${confPercent}%</span></div>`
        }
        if (node.verificationStatus) {
          html += `<div style="margin-bottom: 4px;"><span style="color: #94a3b8;">状态：</span><span style="color: ${statusColors[node.verificationStatus]};">${statusMap[node.verificationStatus] || node.verificationStatus}</span></div>`
        }
        if (node.aiGenerated) {
          html += `<div style="margin-top: 6px; padding-top: 6px; border-top: 1px solid #475569;"><span style="background: linear-gradient(135deg, #3b82f6, #8b5cf6); padding: 2px 8px; border-radius: 4px; font-size: 11px;">AI生成</span></div>`
        }
        return html
      }
    },
    legend: {
      data: ['顶事件', '中间事件', '底事件', '逻辑门'],
      selected: {
        '顶事件': true,
        '中间事件': true,
        '底事件': true,
        '逻辑门': true
      }
    },
    series: [{
      type: 'tree',
      name: '故障树',
      symbol: 'roundRect',
      symbolKeepAspect: true,
      orient: 'TB',
      levelGap: 80,
      nodeGap: 40,
      roam: true,
      draggable: true,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [6, 10],
      initialTreeDepth: -1,
      lineStyle: {
        color: '#909399',
        width: 1.5,
        curveness: 0
      },
      emphasis: {
        focus: 'descendant',
        itemStyle: {
          shadowBlur: 20,
          shadowColor: '#409eff',
          borderColor: '#409eff',
          borderWidth: 3
        }
      },
      select: {
        itemStyle: {
          borderColor: '#409eff',
          borderWidth: 3
        }
      },
      label: {
        show: true,
        position: 'inside',
        formatter: (params) => {
          const node = params.data
          const name = node.eventName || node.name || ''

          if (node.isGateNode) {
            return `{gateType|${node.gateType || 'OR'}}\n{name|${name}}`
          }

          let text = `{name|${name}}`
          if (node.confidence !== undefined && node.confidence !== null) {
            text += `\n{conf|${(node.confidence * 100).toFixed(0)}%}`
          }
          return text
        },
        rich: {
          name: {
            color: '#fff',
            fontSize: 13,
            fontWeight: 'bold',
            lineHeight: 18
          },
          gateType: {
            color: '#ffd700',
            fontSize: 12,
            fontWeight: 'bold',
            backgroundColor: 'rgba(139, 92, 246, 0.8)',
            borderRadius: 4,
            padding: [2, 6, 2, 6]
          },
          conf: {
            color: '#fff',
            fontSize: 10,
            backgroundColor: 'rgba(0,0,0,0.3)',
            borderRadius: 2,
            padding: [1, 3, 1, 3]
          }
        }
      },
      data: []
    }],
    animation: true,
    animationDuration: 500,
    animationEasing: 'cubicOut'
  }

  chartInstance.setOption(option)

  chartInstance.on('click', (params) => {
    const nodeData = params.data || params
    if (nodeData.isGateNode) {
      emit('gate-click', {
        nodeId: nodeData.eventId,
        gateType: nodeData.gateType,
        node: nodeData
      })
    } else {
      emit('node-click', nodeData)
    }
  })

  chartInstance.on('contextmenu', (params) => {
    const nodeData = params.data || params
    if (nodeData.isGateNode) {
      emit('gate-right-click', {
        nodeId: nodeData.eventId,
        gateType: nodeData.gateType,
        node: nodeData,
        event: params.event
      })
    }
  })

  window.addEventListener('resize', handleResize)
}

const updateChart = () => {
  if (!chartInstance || !props.treeData) {
    console.warn('FaultTreeChart: 无法更新图表')
    return
  }

  console.log('FaultTreeChart: 开始更新图表')

  const treeDataForEcharts = convertNodeToEcharts(props.treeData, true)

  if (!treeDataForEcharts) {
    console.warn('FaultTreeChart: 转换后数据为空')
    return
  }

  console.log('FaultTreeChart: 转换后的树形数据:', treeDataForEcharts)

  chartInstance.setOption({
    series: [{
      data: [treeDataForEcharts]
    }]
  }, { notMerge: false })

  setTimeout(() => {
    chartInstance.resize()
  }, 100)
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

watch(() => props.treeData, () => {
  if (chartInstance) {
    updateChart()
  }
}, { deep: true })

onMounted(() => {
  initChart()
  if (props.treeData) {
    updateChart()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})

defineExpose({
  updateChart,
  getChartInstance: () => chartInstance
})
</script>

<style scoped>
.fault-tree-chart {
  width: 100%;
  height: 100%;
  min-height: 400px;
}
</style>
