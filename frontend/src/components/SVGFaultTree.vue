<template>
  <div class="svg-fault-tree-container">
    <div class="svg-canvas" ref="svgContainer">
      <svg
        ref="svgElement"
        class="fault-tree-svg"
        :width="svgWidth"
        :height="svgHeight"
        @click="handleCanvasClick"
        @mousedown="handleMouseDown"
        @mousemove="handleMouseMove"
        @mouseup="handleMouseUp"
        @wheel="handleWheel"
      >
        <rect x="0" y="0" :width="svgWidth" :height="svgHeight" fill="white" stroke="#d9d9d9" stroke-width="2"/>
        <defs>
          <filter id="node-shadow" x="-50%" y="-50%" width="200%" height="200%">
            <feDropShadow dx="0" dy="2" stdDeviation="4" flood-color="rgba(0,0,0,0.1)"/>
          </filter>
          <filter id="selected-glow" x="-50%" y="-50%" width="200%" height="200%">
            <feDropShadow dx="0" dy="0" stdDeviation="4" flood-color="#1890ff" flood-opacity="0.3"/>
          </filter>
        </defs>
        <g :transform="`translate(${translateX}, ${translateY}) scale(${scale})`">
          <g class="connections-layer">
            <path
              v-for="connection in connections"
              :key="connection.id"
              :d="connection.path"
              class="connection-path"
              :class="{
                'selected': connection.selected,
                'low-confidence': connection.confidence < 0.8
              }"
            />
          </g>
          <g class="nodes-layer">
            <g
              v-for="node in layoutNodes"
              :key="node.id"
              class="node-group"
              :class="{
                'selected': node.id === selectedNodeId,
                'editing': node.id === editingNodeId,
                'dragging': node.id === draggingNodeId,
                'is-gate': node.isGate
              }"
              @mousedown="node.isGate ? null : handleNodeMouseDown(node, $event)"
              @click="node.isGate ? null : handleNodeClick(node, $event)"
              @dblclick="node.isGate ? null : handleNodeDoubleClick(node, $event)"
            >
              <template v-if="!node.isGate">
                <rect
                  :x="node.x"
                  :y="node.y"
                  :width="node.width"
                  :height="node.height"
                  :rx="node.rx"
                  :ry="node.ry"
                  :fill="node.fillColor"
                  stroke="none"
                  filter="url(#node-shadow)"
                  class="node-rect"
                  :class="{ 'selected': node.id === selectedNodeId }"
                />
                <text
                  :x="node.x + node.width / 2"
                  :y="node.y + node.height / 2"
                  text-anchor="middle"
                  dominant-baseline="middle"
                  :fill="node.textColor"
                  font-size="14"
                  font-weight="500"
                  class="node-text"
                >
                  {{ node.eventName || node.name }}
                </text>
              </template>
              <template v-else>
                <g :transform="getGateTransform(node)">
                  <g v-if="node.gateType === 'AND'">
                    <path d="M 10,10 L 10,70 L 50,70 A 30,30 0 0,0 50,10 Z" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 80,40 L 90,40" fill="none" stroke="black" stroke-width="2"/>
                    <text x="40" y="45" text-anchor="middle" font-size="14" font-family="sans-serif" fill="black">AND</text>
                  </g>
                  <g v-else-if="node.gateType === 'OR'">
                    <path d="M 10,10 Q 30,40 10,70 Q 40,70 70,40 Q 40,10 10,10 Z" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 70,40 L 90,40" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 0,40 L 20,40" fill="none" stroke="black" stroke-width="2"/>
                    <text x="40" y="45" text-anchor="middle" font-size="14" font-family="sans-serif" fill="black">OR</text>
                  </g>
                  <g v-else-if="node.gateType === 'NOT'">
                    <path d="M 10,10 L 10,50 L 60,30 Z" fill="none" stroke="black" stroke-width="2"/>
                    <circle cx="65" cy="30" r="5" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 60,30 L 65,30" fill="none" stroke="black" stroke-width="2"/>
                    <text x="28" y="35" text-anchor="middle" font-size="14" font-family="sans-serif" fill="black">NOT</text>
                  </g>
                  <g v-else-if="node.gateType === 'NAND'">
                    <path d="M 10,10 L 10,70 L 50,70 A 30,30 0 0,0 50,10 Z" fill="none" stroke="black" stroke-width="2"/>
                    <circle cx="85" cy="40" r="5" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 85,40 L 90,40" fill="none" stroke="black" stroke-width="2"/>
                    <text x="45" y="45" text-anchor="middle" font-size="14" font-family="sans-serif" fill="black">NAND</text>
                  </g>
                  <g v-else-if="node.gateType === 'NOR'">
                    <path d="M 10,10 Q 30,40 10,70 Q 40,70 70,40 Q 40,10 10,10 Z" fill="none" stroke="black" stroke-width="2"/>
                    <circle cx="75" cy="40" r="5" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 75,40 L 80,40" fill="none" stroke="black" stroke-width="2"/>
                    <text x="40" y="45" text-anchor="middle" font-size="14" font-family="sans-serif" fill="black">NOR</text>
                  </g>
                  <g v-else-if="node.gateType === 'XOR'">
                    <path d="M 5,10 Q 25,40 5,70" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 15,10 Q 35,40 15,70 Q 45,70 75,40 Q 45,10 15,10" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 75,40 L 80,40" fill="none" stroke="black" stroke-width="2"/>
                    <text x="45" y="45" text-anchor="middle" font-size="14" font-family="sans-serif" fill="black">XOR</text>
                  </g>
                  <g v-else>
                    <path d="M 10,10 Q 30,40 10,70 Q 40,70 70,40 Q 40,10 10,10 Z" fill="none" stroke="black" stroke-width="2"/>
                    <path d="M 40,70 L 40,75" fill="none" stroke="black" stroke-width="2"/>
                    <text x="40" y="45" text-anchor="middle" font-size="14" font-family="sans-serif" fill="black">VOTING</text>
                  </g>
                </g>
              </template>
              <foreignObject
                v-if="!node.isGate && node.id === editingNodeId"
                :x="node.x + 5"
                :y="node.y + 5"
                :width="node.width - 10"
                :height="node.height - 10"
                @mousedown.stop
              >
                <div xmlns="http://www.w3.org/1999/xhtml" class="edit-input-container">
                  <input
                    ref="editInput"
                    v-model="editingText"
                    type="text"
                    class="edit-input"
                    @keydown="handleEditKeydown"
                    @blur="handleEditBlur"
                  />
                </div>
              </foreignObject>
            </g>
          </g>
        </g>
      </svg>
    </div>
    <div class="zoom-controls">
      <el-button-group>
        <el-button size="small" title="从上到下布局" :type="props.layoutDirection === 'TB' ? 'primary' : ''" @click="emit('layout-change', 'TB')">
          <el-icon><Operation /></el-icon>
        </el-button>
        <el-button size="small" title="从左到右布局" :type="props.layoutDirection === 'LR' ? 'primary' : ''" @click="emit('layout-change', 'LR')">
          <el-icon><Rank /></el-icon>
        </el-button>
      </el-button-group>
      <el-divider direction="vertical" />
      <el-button size="small" @click="zoomIn"><el-icon><Plus /></el-icon></el-button>
      <span class="zoom-level">{{ Math.round(scale * 100) }}%</span>
      <el-button size="small" @click="zoomOut"><el-icon><Minus /></el-icon></el-button>
      <el-button size="small" @click="resetView"><el-icon><Refresh /></el-icon></el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { Plus, Minus, Refresh, Rank, Operation } from '@element-plus/icons-vue'

const GATE_SIZES = {
  AND: { width: 100, height: 80 },
  OR: { width: 100, height: 80 },
  NOT: { width: 100, height: 60 },
  NAND: { width: 110, height: 80 },
  NOR: { width: 110, height: 80 },
  XOR: { width: 110, height: 80 },
  VOTING: { width: 100, height: 80 }
}

const props = defineProps({
  treeData: { type: Object, default: null },
  selectedNodeId: { type: String, default: '' },
  layoutDirection: { type: String, default: 'TB', validator: (value) => ['TB', 'LR'].includes(value) }
})

const emit = defineEmits(['node-click', 'node-dblclick', 'node-edit', 'node-select', 'layout-change'])

const svgContainer = ref(null)
const svgElement = ref(null)
const editInput = ref(null)
const svgWidth = ref(1200)
const svgHeight = ref(800)
const scale = ref(1)
const translateX = ref(0)
const translateY = ref(0)
const isDragging = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const editingNodeId = ref('')
const editingText = ref('')
const isNodeDragging = ref(false)
const draggingNodeId = ref('')
const dragStartNodeX = ref(0)
const dragStartNodeY = ref(0)
const dragCurrentNode = ref(null)
const pendingClickNode = ref(null)

const layoutNodes = ref([])
const connections = ref([])

const NODE_WIDTH = 160
const NODE_HEIGHT = 40
const NODE_RX = 6
const NODE_RY = 6

const LAYOUT_CONFIG = {
  verticalGap: 200,
  horizontalGap: 140,
  margin: 100,
  gateOffset: 50,
  convergenceOffset: 30
}

const NODE_COLORS = {
  TOP: '#e6f7ff',
  INTERMEDIATE: '#f6ffed',
  BASIC: '#fff7e6'
}

const getGateWidth = (gateType) => {
  return GATE_SIZES[gateType]?.width || 100
}

const getGateHeight = (gateType) => {
  return GATE_SIZES[gateType]?.height || 80
}

const findNodeById = (nodeId) => {
  return layoutNodes.value.find(n => n.id === nodeId)
}

const findGateNodeForParent = (parentId) => {
  return layoutNodes.value.find(n => n.isGate && n.parentId === parentId)
}

const getGateTransform = (node) => {
  if (props.layoutDirection === 'TB') {
    // 在TB布局中，门需要旋转270度并调整位置
    const centerX = node.x + node.width / 2
    const centerY = node.y + node.height / 2
    // 旋转270度（-90度），并调整位置使门居中
    return `translate(${centerX}, ${centerY}) rotate(270) translate(${-node.width / 2}, ${-node.height / 2})`
  } else {
    // LR布局中旋转180度
    const centerX = node.x + node.width / 2
    const centerY = node.y + node.height / 2
    return `translate(${centerX}, ${centerY}) rotate(180) translate(${-node.width / 2}, ${-node.height / 2})`
  }
}

const updateConnections = () => {
  connections.value.forEach(conn => {
    const parentNode = findNodeById(conn.parentId)
    const childNode = findNodeById(conn.childId)
    const gateNode = conn.gateNodeId ? findNodeById(conn.gateNodeId) : null

    if (parentNode && childNode) {
      let pathData = ''

      if (props.layoutDirection === 'TB') {
        if (gateNode) {
          const x1 = parentNode.x + NODE_WIDTH / 2
          const y1 = parentNode.y + NODE_HEIGHT
          // 调整路径，让它刚好在门的边界结束
          const gateTopY = gateNode.y
          const gateBottomY = gateNode.y + gateNode.height
          const gx = gateNode.x + gateNode.width / 2
          const cx = childNode.x + NODE_WIDTH / 2
          const cy = childNode.y
          const convX = conn.convergenceX !== undefined ? conn.convergenceX : gx
          const convY = conn.convergenceY !== undefined ? conn.convergenceY : gateNode.y + gateNode.height + LAYOUT_CONFIG.convergenceOffset

          pathData = `M ${x1},${y1} L ${gx},${y1} L ${gx},${gateTopY} M ${gx},${gateBottomY} L ${gx},${convY} L ${convX},${convY} L ${cx},${convY} L ${cx},${cy}`
        } else {
          console.warn(`Connection ${conn.id} has no gateNodeId, but should have one`)
          const x1 = parentNode.x + NODE_WIDTH / 2
          const y1 = parentNode.y + NODE_HEIGHT
          const cx = childNode.x + NODE_WIDTH / 2
          const cy = childNode.y
          const midY = y1 + LAYOUT_CONFIG.verticalGap / 4

          pathData = `M ${x1},${y1} L ${x1},${midY} L ${cx},${midY} L ${cx},${cy}`
        }
      } else {
        if (gateNode) {
          const x1 = parentNode.x + NODE_WIDTH
          const y1 = parentNode.y + NODE_HEIGHT / 2
          // 调整路径，让它刚好在门的边界结束
          const gateLeftX = gateNode.x
          const gateRightX = gateNode.x + gateNode.width
          const gx = gateNode.x + gateNode.width / 2
          const gy = gateNode.y + gateNode.height / 2
          const cx = childNode.x
          const cy = childNode.y + NODE_HEIGHT / 2
          const convX = conn.convergenceX !== undefined ? conn.convergenceX : gateNode.x + gateNode.width + LAYOUT_CONFIG.convergenceOffset
          const convY = conn.convergenceY !== undefined ? conn.convergenceY : gy

          pathData = `M ${x1},${y1} L ${x1},${gy} L ${gateLeftX},${gy} M ${gateRightX},${gy} L ${convX},${gy} L ${convX},${convY} L ${convX},${cy} L ${cx},${cy}`
        } else {
          console.warn(`Connection ${conn.id} has no gateNodeId, but should have one`)
          const x1 = parentNode.x + NODE_WIDTH
          const y1 = parentNode.y + NODE_HEIGHT / 2
          const cx = childNode.x
          const cy = childNode.y + NODE_HEIGHT / 2
          const midX = x1 + LAYOUT_CONFIG.verticalGap / 4

          pathData = `M ${x1},${y1} L ${midX},${y1} L ${midX},${cy} L ${cx},${cy}`
        }
      }

      conn.path = pathData
    }
  })
}

const calculateTreeLayout = () => {
  if (!props.treeData) {
    layoutNodes.value = []
    connections.value = []
    return
  }

  console.log('calculateTreeLayout 被调用, layoutDirection:', props.layoutDirection)

  const nodes = []
  const connectionsList = []
  let maxX = 0
  let maxY = 0
  let nodeIdCounter = 0

  const isTB = props.layoutDirection === 'TB'

  const calculateSubtreeSize = (node) => {
    if (!node.children || node.children.length === 0) {
      return isTB ? { width: NODE_WIDTH, height: NODE_HEIGHT } : { width: NODE_WIDTH, height: NODE_HEIGHT }
    }

    if (isTB) {
      let totalChildWidth = 0
      let maxChildHeight = 0
      node.children.forEach((child, index) => {
        const size = calculateSubtreeSize(child)
        if (index > 0) totalChildWidth += LAYOUT_CONFIG.horizontalGap
        totalChildWidth += size.width
        maxChildHeight = Math.max(maxChildHeight, size.height)
      })
      
      const gateHeight = node.gateType ? getGateHeight(node.gateType) + LAYOUT_CONFIG.gateOffset + 60 : 0
      const convergenceHeight = node.gateType ? LAYOUT_CONFIG.convergenceOffset + 30 : 0
      
      return {
        width: Math.max(totalChildWidth, NODE_WIDTH),
        height: NODE_HEIGHT + gateHeight + convergenceHeight + maxChildHeight
      }
    } else {
      let totalChildHeight = 0
      let maxChildWidth = 0
      node.children.forEach((child, index) => {
        const size = calculateSubtreeSize(child)
        if (index > 0) totalChildHeight += LAYOUT_CONFIG.verticalGap
        totalChildHeight += size.height
        maxChildWidth = Math.max(maxChildWidth, size.width)
      })
      
      const gateWidth = node.gateType ? getGateWidth(node.gateType) + LAYOUT_CONFIG.gateOffset + 60 : 0
      const convergenceWidth = node.gateType ? LAYOUT_CONFIG.convergenceOffset + 30 : 0
      
      return {
        width: NODE_WIDTH + gateWidth + convergenceWidth + maxChildWidth,
        height: Math.max(totalChildHeight, NODE_HEIGHT)
      }
    }
  }

  const getChildrenTotalWidth = (node) => {
    let totalWidth = 0
    if (node.children && node.children.length > 0) {
      node.children.forEach((child, index) => {
        const size = calculateSubtreeSize(child)
        if (index > 0) totalWidth += LAYOUT_CONFIG.horizontalGap
        totalWidth += size.width
      })
    }
    return totalWidth
  }

  const getChildrenTotalHeight = (node) => {
    let totalHeight = 0
    if (node.children && node.children.length > 0) {
      node.children.forEach((child, index) => {
        const size = calculateSubtreeSize(child)
        if (index > 0) totalHeight += LAYOUT_CONFIG.verticalGap
        totalHeight += size.height
      })
    }
    return totalHeight
  }

  const traverse = (node, level, parentX, parentY, parentId) => {
    let x = 0
    let y = 0

    if (isTB) {
      if (level === 0) {
        const treeWidth = calculateSubtreeSize(props.treeData).width
        x = LAYOUT_CONFIG.margin + (treeWidth - NODE_WIDTH) / 2
        y = LAYOUT_CONFIG.margin
      } else {
        x = parentX
        y = parentY
      }
    } else {
      if (level === 0) {
        const treeHeight = calculateSubtreeSize(props.treeData).height
        y = LAYOUT_CONFIG.margin + (treeHeight - NODE_HEIGHT) / 2
        x = LAYOUT_CONFIG.margin
      } else {
        x = parentX
        y = parentY
      }
    }

    const nodeId = node.eventId || node.id || `node_${++nodeIdCounter}`

    const nodeData = {
      id: nodeId,
      eventName: node.eventName || node.name,
      eventType: node.eventType,
      gateType: node.gateType,
      confidence: node.confidence,
      verificationStatus: node.verificationStatus,
      x: x,
      y: y,
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
      rx: NODE_RX,
      ry: NODE_RY,
      fillColor: node.bgColor || NODE_COLORS[node.eventType] || NODE_COLORS.BASIC,
      textColor: node.textColor || '#333',
      parentId: parentId,
      isGate: false
    }

    nodes.push(nodeData)
    maxX = Math.max(maxX, x + NODE_WIDTH)
    maxY = Math.max(maxY, y + NODE_HEIGHT)

    let gateNode = null
    let convergenceX = 0
    let convergenceY = 0

    if (node.children && node.children.length > 0) {
      if (node.gateType) {
        const gateNodeId = `gate_${nodeId}`
        const gateWidth = getGateWidth(node.gateType)
        const gateHeight = getGateHeight(node.gateType)
        let gateX, gateY

        if (isTB) {
          gateX = x + NODE_WIDTH / 2 - gateWidth / 2
          gateY = y + NODE_HEIGHT + LAYOUT_CONFIG.gateOffset
        } else {
          gateX = x + NODE_WIDTH + LAYOUT_CONFIG.gateOffset
          gateY = y + NODE_HEIGHT / 2 - gateHeight / 2
        }

        gateNode = {
          id: gateNodeId,
          eventName: '',
          eventType: '',
          gateType: node.gateType,
          confidence: undefined,
          verificationStatus: undefined,
          x: gateX,
          y: gateY,
          width: gateWidth,
          height: gateHeight,
          rx: 0,
          ry: 0,
          fillColor: 'transparent',
          textColor: 'transparent',
          parentId: nodeId,
          isGate: true
        }
        nodes.push(gateNode)

        maxX = Math.max(maxX, gateX + gateWidth)
        maxY = Math.max(maxY, gateY + gateHeight)
      }

      if (isTB) {
        const totalChildrenWidth = getChildrenTotalWidth(node)
        const childrenStartX = x + NODE_WIDTH / 2 - totalChildrenWidth / 2
        let currentChildX = childrenStartX
        
        if (node.gateType && node.children.length > 0) {
          const firstChildSize = calculateSubtreeSize(node.children[0])
          const lastChildSize = calculateSubtreeSize(node.children[node.children.length - 1])
          const leftX = childrenStartX + firstChildSize.width / 2
          const rightX = childrenStartX + totalChildrenWidth - lastChildSize.width / 2
          convergenceX = (leftX + rightX) / 2
            convergenceY = gateNode ? gateNode.y + gateNode.height + LAYOUT_CONFIG.convergenceOffset + 30 : y + NODE_HEIGHT + LAYOUT_CONFIG.gateOffset + LAYOUT_CONFIG.convergenceOffset + 30
          
          maxY = Math.max(maxY, convergenceY)
        }

        node.children.forEach((child) => {
          const childSize = calculateSubtreeSize(child)
          const childX = currentChildX
            const childY = convergenceY + LAYOUT_CONFIG.convergenceOffset + 30
          const childNode = traverse(child, level + 1, childX, childY, nodeId)

          if (childNode) {
            const cx = childNode.x + childSize.width / 2
            const cy = childNode.y

            if (gateNode) {
              const x1 = x + NODE_WIDTH / 2
              const y1 = y + NODE_HEIGHT
              const gateTopY = gateNode.y
              const gateBottomY = gateNode.y + gateNode.height
              const gx = gateNode.x + gateNode.width / 2
              const convX = convergenceX
              const convY = convergenceY

              const pathData = `M ${x1},${y1} L ${gx},${y1} L ${gx},${gateTopY} M ${gx},${gateBottomY} L ${gx},${convY} L ${convX},${convY} L ${cx},${convY} L ${cx},${cy}`

              connectionsList.push({
                id: `${nodeId}-${childNode.id}`,
                parentId: nodeId,
                childId: childNode.id,
                gateNodeId: gateNode.id,
                convergenceX: convX,
                convergenceY: convY,
                path: pathData,
                confidence: child.confidence,
                gateType: node.gateType,
                selected: nodeId === props.selectedNodeId || childNode.id === props.selectedNodeId
              })
            } else {
              const x1 = x + NODE_WIDTH / 2
              const y1 = y + NODE_HEIGHT
              const midY = y + NODE_HEIGHT + LAYOUT_CONFIG.verticalGap / 4

              const pathData = `M ${x1},${y1} L ${x1},${midY} L ${cx},${midY} L ${cx},${cy}`

              connectionsList.push({
                id: `${nodeId}-${childNode.id}`,
                parentId: nodeId,
                childId: childNode.id,
                gateNodeId: null,
                convergenceX: undefined,
                convergenceY: undefined,
                path: pathData,
                confidence: child.confidence,
                gateType: null,
                selected: nodeId === props.selectedNodeId || childNode.id === props.selectedNodeId
              })
            }

            currentChildX += childSize.width + LAYOUT_CONFIG.horizontalGap
          }
        })
      } else {
        const totalChildrenHeight = getChildrenTotalHeight(node)
        const childrenStartY = y + NODE_HEIGHT / 2 - totalChildrenHeight / 2
        let currentChildY = childrenStartY
        
        if (node.gateType && node.children.length > 0) {
          const firstChildSize = calculateSubtreeSize(node.children[0])
          const lastChildSize = calculateSubtreeSize(node.children[node.children.length - 1])
          const topY = childrenStartY + firstChildSize.height / 2
          const bottomY = childrenStartY + totalChildrenHeight - lastChildSize.height / 2
          convergenceY = (topY + bottomY) / 2
          convergenceX = gateNode ? gateNode.x + gateNode.width + LAYOUT_CONFIG.convergenceOffset + 30 : x + NODE_WIDTH + LAYOUT_CONFIG.gateOffset + LAYOUT_CONFIG.convergenceOffset + 30
          
          maxX = Math.max(maxX, convergenceX)
        }

        node.children.forEach((child) => {
          const childSize = calculateSubtreeSize(child)
          const childX = convergenceX + LAYOUT_CONFIG.convergenceOffset + 30
          const childY = currentChildY
          const childNode = traverse(child, level + 1, childX, childY, nodeId)

          if (childNode) {
            const cx = childNode.x
            const cy = childNode.y + childSize.height / 2

            if (gateNode) {
              const x1 = x + NODE_WIDTH
              const y1 = y + NODE_HEIGHT / 2
              const gateLeftX = gateNode.x
              const gateRightX = gateNode.x + gateNode.width
              const gx = gateNode.x + gateNode.width / 2
              const gy = gateNode.y + gateNode.height / 2
              const convX = convergenceX
              const convY = convergenceY

              const pathData = `M ${x1},${y1} L ${x1},${gy} L ${gateLeftX},${gy} M ${gateRightX},${gy} L ${convX},${gy} L ${convX},${convY} L ${convX},${cy} L ${cx},${cy}`

              connectionsList.push({
                id: `${nodeId}-${childNode.id}`,
                parentId: nodeId,
                childId: childNode.id,
                gateNodeId: gateNode.id,
                convergenceX: convX,
                convergenceY: convY,
                path: pathData,
                confidence: child.confidence,
                gateType: node.gateType,
                selected: nodeId === props.selectedNodeId || childNode.id === props.selectedNodeId
              })
            } else {
              const x1 = x + NODE_WIDTH
              const y1 = y + NODE_HEIGHT / 2
              const midX = x + NODE_WIDTH + LAYOUT_CONFIG.verticalGap / 4

              const pathData = `M ${x1},${y1} L ${midX},${y1} L ${midX},${cy} L ${cx},${cy}`

              connectionsList.push({
                id: `${nodeId}-${childNode.id}`,
                parentId: nodeId,
                childId: childNode.id,
                gateNodeId: null,
                convergenceX: undefined,
                convergenceY: undefined,
                path: pathData,
                confidence: child.confidence,
                gateType: null,
                selected: nodeId === props.selectedNodeId || childNode.id === props.selectedNodeId
              })
            }

            currentChildY += childSize.height + LAYOUT_CONFIG.verticalGap
          }
        })
      }
    }

    return nodeData
  }

  traverse(props.treeData, 0, 0, 0, null)

  const minWidth = Math.max(1200, maxX + LAYOUT_CONFIG.margin + 60)
  const minHeight = Math.max(800, maxY + LAYOUT_CONFIG.margin + 60)
  svgWidth.value = minWidth
  svgHeight.value = minHeight
  layoutNodes.value = nodes
  connections.value = connectionsList
  
  // 确保布局完成后连接路径正确
  nextTick(() => {
    updateConnections()
  })
}

const handleNodeMouseDown = (node, event) => {
  if (event.button !== 0 || node.isGate) return

  if (editingNodeId.value && editingNodeId.value !== node.id) {
    pendingClickNode.value = node
    event.stopPropagation()
    return
  }

  if (editingNodeId.value) {
    event.stopPropagation()
    return
  }

  event.stopPropagation()
  isNodeDragging.value = true
  draggingNodeId.value = node.id
  dragStartNodeX.value = event.clientX
  dragStartNodeY.value = event.clientY
  dragCurrentNode.value = node
  event.preventDefault()
}

const handleNodeClick = (node, event) => {
  console.log('SVG handleNodeClick:', node.id, node.eventName)
  if (isNodeDragging.value || node.isGate) return

  if (editingNodeId.value && editingNodeId.value !== node.id) {
    submitEdit()
  }

  event.stopPropagation()
  emit('node-click', node)
  emit('node-select', node)
}

const handleNodeDoubleClick = (node, event) => {
  if (isNodeDragging.value || node.isGate) return
  event.stopPropagation()

  editingNodeId.value = node.id
  editingText.value = node.eventName || node.name || ''

  nextTick(() => {
    if (editInput.value) {
      editInput.value.focus()
      editInput.value.select()
    }
  })
}

const handleEditKeydown = (event) => {
  if (event.key === 'Enter') submitEdit()
  else if (event.key === 'Escape') cancelEdit()
}

const handleEditBlur = () => submitEdit()

const submitEdit = () => {
  const pendingNode = pendingClickNode.value
  pendingClickNode.value = null

  if (editingNodeId.value && editingText.value.trim()) {
    const node = layoutNodes.value.find(n => n.id === editingNodeId.value)
    if (node) {
      emit('node-edit', node, editingText.value.trim())
    }
  }
  editingNodeId.value = ''
  editingText.value = ''

  if (pendingNode) {
    emit('node-click', pendingNode)
    emit('node-select', pendingNode)
  }
}

const cancelEdit = () => {
  const pendingNode = pendingClickNode.value
  pendingClickNode.value = null
  editingNodeId.value = ''
  editingText.value = ''

  if (pendingNode) {
    emit('node-click', pendingNode)
    emit('node-select', pendingNode)
  }
}

const handleCanvasClick = (event) => {
  if (event.target === svgElement.value) {
    cancelEdit()
    emit('node-select', null)
  }
}

const handleMouseDown = (event) => {
  if (event.button !== 0) return
  if (isNodeDragging.value) return

  isDragging.value = true
  dragStartX.value = event.clientX - translateX.value
  dragStartY.value = event.clientY - translateY.value
  event.preventDefault()
}

const handleMouseMove = (event) => {
  if (isNodeDragging.value && draggingNodeId.value) {
    const node = findNodeById(draggingNodeId.value)
    if (node) {
      const deltaX = (event.clientX - dragStartNodeX.value) / scale.value
      const deltaY = (event.clientY - dragStartNodeY.value) / scale.value

      // 更新节点位置
      node.x = node.x + deltaX
      node.y = node.y + deltaY

      // 同时更新相关的门节点位置
      const gateNode = findGateNodeForParent(node.id)
      if (gateNode) {
        if (props.layoutDirection === 'TB') {
          gateNode.x = node.x + NODE_WIDTH / 2 - gateNode.width / 2
          gateNode.y = node.y + NODE_HEIGHT + LAYOUT_CONFIG.gateOffset
        } else {
          gateNode.x = node.x + NODE_WIDTH + LAYOUT_CONFIG.gateOffset
          gateNode.y = node.y + NODE_HEIGHT / 2 - gateNode.height / 2
        }
      }

      // 更新所有子节点的位置（保持相对位置）
      const childConns = connections.value.filter(c => c.parentId === node.id)
      childConns.forEach(conn => {
        const childNode = findNodeById(conn.childId)
        if (childNode) {
          childNode.x = childNode.x + deltaX
          childNode.y = childNode.y + deltaY
        }
      })

      // 更新相关连接的convergenceX/Y
      connections.value.forEach(conn => {
        if (conn.parentId === node.id && conn.convergenceX !== undefined) {
          conn.convergenceX = conn.convergenceX + deltaX
          conn.convergenceY = conn.convergenceY + deltaY
        }
      })

      dragStartNodeX.value = event.clientX
      dragStartNodeY.value = event.clientY

      updateConnections()
    }
    return
  }

  if (!isDragging.value) return

  translateX.value = event.clientX - dragStartX.value
  translateY.value = event.clientY - dragStartY.value
}

const handleMouseUp = () => {
  isDragging.value = false
  isNodeDragging.value = false
  draggingNodeId.value = ''
  dragCurrentNode.value = null
}

const handleWheel = (event) => {
  event.preventDefault()
  const delta = -event.deltaY / 1000
  const newScale = Math.min(Math.max(scale.value + delta, 0.1), 5)

  const rect = svgElement.value.getBoundingClientRect()
  const mouseX = event.clientX - rect.left
  const mouseY = event.clientY - rect.top

  translateX.value = mouseX - (mouseX - translateX.value) * (newScale / scale.value)
  translateY.value = mouseY - (mouseY - translateY.value) * (newScale / scale.value)
  scale.value = newScale
}

const zoomIn = () => {
  scale.value = Math.min(scale.value * 1.2, 5)
}

const zoomOut = () => {
  scale.value = Math.max(scale.value / 1.2, 0.1)
}

const resetView = () => {
  scale.value = 1
  translateX.value = 0
  translateY.value = 0
}

watch(() => props.treeData, (newVal) => {
  if (newVal) {
    nextTick(() => calculateTreeLayout())
  }
}, { deep: true, immediate: true })

watch(() => props.layoutDirection, () => {
  nextTick(() => calculateTreeLayout())
})

watch(() => props.selectedNodeId, () => {
  connections.value.forEach(conn => {
    conn.selected = conn.parentId === props.selectedNodeId || conn.childId === props.selectedNodeId
  })
})

onMounted(() => {
  if (props.treeData) {
    calculateTreeLayout()
  }
})

defineExpose({
  svgWidth,
  svgHeight,
  layoutNodes
})
</script>

<style scoped>
.svg-fault-tree-container {
  width: 100%;
  height: 100%;
  position: relative;
  background: white;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.svg-canvas {
  width: 100%;
  height: 100%;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
}

.fault-tree-svg {
  background: white;
  cursor: grab;
  display: block;
  border: 2px solid #d9d9d9;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.fault-tree-svg:active {
  cursor: grabbing;
}

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

.node-group {
  cursor: move;
  transition: filter 0.2s;
}

.node-group.is-gate {
  cursor: default;
}

.node-group:hover .node-rect {
  filter: url(#node-shadow);
}

.node-group.selected .node-rect {
  stroke: #1890ff;
  stroke-width: 2;
  filter: url(#selected-glow);
}

.node-group.dragging {
  cursor: move;
  opacity: 0.9;
}

.node-group.dragging .node-rect {
  filter: url(#selected-glow);
}

.node-rect {
  transition: none;
}

.node-text {
  pointer-events: none;
  user-select: none;
}

.edit-input-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.edit-input {
  width: 100%;
  height: 28px;
  border: 1px solid #1890ff;
  border-radius: 4px;
  padding: 0 8px;
  font-size: 14px;
  text-align: center;
  outline: none;
  font-family: inherit;
}

.edit-input:focus {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.zoom-controls {
  position: absolute;
  bottom: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: white;
  padding: 8px 12px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 1000;
}

.zoom-level {
  font-size: 12px;
  color: #666;
  min-width: 40px;
  text-align: center;
}
</style>
