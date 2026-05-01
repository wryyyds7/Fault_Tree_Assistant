import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const app = createApp(App)
app.use(router)
app.use(ElementPlus)

app.mount('#app')

app.mixin({
  methods: {
    getStatusTagType(status) {
      const types = {
        PENDING: 'info',
        PROCESSING: 'warning',
        COMPLETED: 'success',
        FAILED: 'danger',
        DRAFT: 'info',
        PUBLISHED: 'success',
        ARCHIVED: 'warning'
      }
      return types[status] || 'info'
    },
    getStatusText(status) {
      const texts = {
        PENDING: '待处理',
        PROCESSING: '处理中',
        COMPLETED: '已完成',
        FAILED: '失败',
        DRAFT: '草稿',
        PUBLISHED: '已发布',
        ARCHIVED: '已归档'
      }
      return texts[status] || status
    },
    formatFileSize(bytes) {
      if (!bytes) return '0 B'
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    },
    formatDate(dateStr) {
      if (!dateStr) return '-'
      const date = new Date(dateStr)
      return date.toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      })
    }
  }
})
