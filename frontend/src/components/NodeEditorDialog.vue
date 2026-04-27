<template>
  <el-dialog
    v-model="visible"
    title="编辑节点"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="form" label-width="100px" ref="formRef">
      <el-form-item label="节点名称">
        <el-input v-model="form.eventName" placeholder="请输入节点名称" />
      </el-form-item>

      <el-form-item label="节点类型">
        <el-select v-model="form.eventType" placeholder="选择节点类型" style="width: 100%">
          <el-option label="顶事件" value="TOP" />
          <el-option label="中间事件" value="INTERMEDIATE" />
          <el-option label="底事件" value="BASIC" />
        </el-select>
      </el-form-item>

      <el-form-item label="逻辑门类型" v-if="form.eventType !== 'BASIC'">
        <el-select v-model="form.gateType" placeholder="选择逻辑门" style="width: 100%">
          <el-option label="与门 (AND)" value="AND" />
          <el-option label="或门 (OR)" value="OR" />
          <el-option label="异或门 (XOR)" value="XOR" />
        </el-select>
      </el-form-item>

      <el-form-item label="置信度">
        <div class="confidence-slider">
          <el-slider v-model="form.confidence" :min="0" :max="1" :step="0.1" show-stops />
          <span class="confidence-value">{{ (form.confidence * 100).toFixed(0) }}%</span>
        </div>
      </el-form-item>

      <el-form-item label="验证状态">
        <el-radio-group v-model="form.verificationStatus">
          <el-radio label="PENDING">待确认</el-radio>
          <el-radio label="CONFIRMED">已确认</el-radio>
          <el-radio label="REJECTED">已驳回</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="节点描述">
        <el-input v-model="form.sourceDetail" type="textarea" :rows="3" placeholder="请输入节点描述" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  node: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'save'])

const visible = ref(false)
const formRef = ref(null)

const form = reactive({
  eventName: '',
  eventType: 'INTERMEDIATE',
  gateType: 'OR',
  confidence: 1.0,
  verificationStatus: 'PENDING',
  sourceDetail: ''
})

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.node) {
    form.eventName = props.node.eventName || ''
    form.eventType = props.node.eventType || 'INTERMEDIATE'
    form.gateType = props.node.gateType || 'OR'
    form.confidence = props.node.confidence ?? 1.0
    form.verificationStatus = props.node.verificationStatus || 'PENDING'
    form.sourceDetail = props.node.sourceDetail || ''
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const handleClose = () => {
  visible.value = false
}

const handleSave = () => {
  if (!form.eventName.trim()) {
    ElMessage.warning('请输入节点名称')
    return
  }

  emit('save', {
    eventId: props.node?.eventId,
    eventName: form.eventName,
    eventType: form.eventType,
    gateType: form.eventType === 'BASIC' ? null : form.gateType,
    confidence: form.confidence,
    verificationStatus: form.verificationStatus,
    sourceDetail: form.sourceDetail
  })

  handleClose()
}
</script>

<style scoped>
.confidence-slider {
  display: flex;
  align-items: center;
  width: 100%;
}

.confidence-slider .el-slider {
  flex: 1;
}

.confidence-value {
  margin-left: 16px;
  min-width: 50px;
  font-weight: 600;
  color: #409eff;
}
</style>
