import axios from 'axios'
import { ElMessage } from 'element-plus'

const API_BASE_URL = '/api/v1'
const CHAT_API_BASE_URL = '/api/v1/chat'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000
})

const chatApiClient = axios.create({
  baseURL: CHAT_API_BASE_URL,
  timeout: 120000,
  headers: {
    'Content-Type': 'application/json'
  }
})

chatApiClient.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    const userId = localStorage.getItem('userId')
    if (userId) {
      config.headers['X-User-Id'] = userId
      config.headers['X-User-Key'] = userId
      config.headers['X-User-Role'] = localStorage.getItem('role') || 'USER'
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

chatApiClient.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    if (error.response) {
      console.error('Chat API Error:', error.response.status, error.response.data)
      // 对于获取会话列表的请求，不显示 404 错误提示
      const isGetSessionsRequest = error.config?.url?.includes('/sessions/user/')
      switch (error.response.status) {
        case 401:
          ElMessage.error('未授权，请重新登录')
          localStorage.removeItem('token')
          window.location.href = '/login'
          break
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          if (!isGetSessionsRequest) {
            ElMessage.error('请求资源不存在')
          }
          break
        case 500:
          ElMessage.error('服务器内部错误: ' + (error.response.data?.detail || ''))
          break
        default:
          ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else if (error.request) {
      ElMessage.error('网络连接失败，请检查网络')
    } else {
      ElMessage.error('请求配置错误')
    }
    return Promise.reject(error)
  }
)

apiClient.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    const userId = localStorage.getItem('userId')
    if (userId) {
      config.headers['X-User-Id'] = userId
      config.headers['X-User-Key'] = userId
      config.headers['X-User-Role'] = localStorage.getItem('role') || 'USER'
    }
    console.log('📤 [API Request]', config.method?.toUpperCase(), config.baseURL + config.url)
    console.log('📤 [API Request] Headers:', config.headers)
    if (config.data) {
      console.log('📤 [API Request] Body:', config.data)
    }
    if (config.params) {
      console.log('📤 [API Request] Params:', config.params)
    }
    return config
  },
  error => {
    console.error('❌ [API Request Error]', error)
    return Promise.reject(error)
  }
)

apiClient.interceptors.response.use(
  response => {
    console.log('📥 [API Response]', response.config.method?.toUpperCase(), response.config.url, '=>', response.status)
    console.log('📥 [API Response] Data:', response.data)
    return response.data
  },
  error => {
    if (error.response) {
      console.error('❌ [API Response Error]', error.config?.method?.toUpperCase(), error.config?.url, '=>', error.response.status)
      console.error('❌ [API Response Error] Data:', error.response.data)
      switch (error.response.status) {
        case 401:
          ElMessage.error('未授权，请重新登录')
          localStorage.removeItem('token')
          window.location.href = '/login'
          break
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          ElMessage.error('请求资源不存在')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else if (error.request) {
      console.error('❌ [API Response Error] No response received:', error.request)
      ElMessage.error('网络连接失败，请检查网络')
    } else {
      console.error('❌ [API Response Error]', error.message)
      ElMessage.error('请求配置错误')
    }
    return Promise.reject(error)
  }
)

export const authAPI = {
  login: (data) => apiClient.post('/auth/login', data),
  register: (data) => apiClient.post('/auth/register', data)
}

export const documentAPI = {
  upload: (formData) => apiClient.post('/documents/upload', formData),
  getDocument: (docId) => apiClient.get(`/documents/${docId}`),
  getDocumentContent: (docId) => apiClient.get(`/documents/${docId}/content`),
  getDocumentList: () => apiClient.get('/documents'),
  deleteDocument: (docId) => apiClient.delete(`/documents/${docId}`)
}

export const faultTreeAPI = {
  getAll: (params) => apiClient.get('/fault-trees', { params }),
  getById: (treeId) => apiClient.get(`/fault-trees/${treeId}`),
  create: (data) => apiClient.post('/fault-trees', data),
  update: (treeId, data) => apiClient.put(`/fault-trees/${treeId}`, data),
  delete: (treeId) => apiClient.delete(`/fault-trees/${treeId}`),

  getVersions: (treeId) => apiClient.get(`/fault-trees/${treeId}/versions`),
  createVersion: (treeId, data) => apiClient.post(`/fault-trees/${treeId}/versions`, data),
  getVersion: (treeId, versionNumber) => apiClient.get(`/fault-trees/${treeId}/versions/${versionNumber}`),

  addNode: (treeId, parentEventId, nodeData) => apiClient.post(
    `/fault-trees/${treeId}/nodes`,
    nodeData,
    { params: { parentEventId } }
  ),
  updateNode: (treeId, nodeData) => apiClient.put(`/fault-trees/${treeId}/nodes`, nodeData),
  deleteNode: (treeId, eventId) => apiClient.delete(`/fault-trees/${treeId}/nodes/${eventId}`),
  moveNode: (treeId, eventId, newParentId) => apiClient.put(
    `/fault-trees/${treeId}/nodes/${eventId}/move`,
    {},
    { params: { newParentId } }
  ),
  updatePositions: (treeId, positions) => apiClient.put(
    `/fault-trees/${treeId}/positions`,
    positions
  ),
  updateConfidence: (treeId, eventId, confidence, verificationStatus) => apiClient.put(
    `/fault-trees/${treeId}/nodes/${eventId}/confidence`,
    { confidence, verificationStatus }
  ),
  updateVerification: (treeId, eventId, status) => apiClient.put(
    `/fault-trees/${treeId}/nodes/${eventId}/verification`,
    { status }
  )
}

export const validationAPI = {
  validate: (faultTree) => apiClient.post('/validation/validate', faultTree)
}

export const ragAPI = {
  generate: (data) => apiClient.post('/rag/generate', data),
  getTaskStatus: (taskId) => apiClient.get(`/rag/tasks/${taskId}`),
  getEvidence: (paragraphId) => apiClient.get(`/rag/evidence/${paragraphId}`),
  getEvidenceList: (data) => apiClient.post('/rag/evidence', data),
  getParagraphWithContext: (paragraphId, userId, contextBefore = 2, contextAfter = 2) => 
    apiClient.get(`/rag/evidence/${paragraphId}/context`, { 
      params: { userId, contextBefore, contextAfter } 
    })
}

export const chatAPI = {
  createSession: (data) => chatApiClient.post('/sessions', data),
  getSession: (sessionId) => chatApiClient.get(`/sessions/${sessionId}`),
  getUserSessions: (userId) => chatApiClient.get(`/sessions/user/${userId}`),
  deleteSession: (sessionId) => chatApiClient.delete(`/sessions/${sessionId}`),
  chat: (data) => chatApiClient.post('/chat', data),
  linkTree: (sessionId, treeId) => chatApiClient.post('/link/tree', { sessionId, treeId }),
  unlinkTree: (sessionId, treeId) => chatApiClient.delete(`/sessions/${sessionId}/tree/${treeId}`),
  linkDocument: (sessionId, docId) => chatApiClient.post('/link/document', { sessionId, docId }),
  unlinkDocument: (sessionId, docId) => chatApiClient.delete(`/sessions/${sessionId}/doc/${docId}`),
  getUserFaultTrees: (userId) => chatApiClient.get(`/trees/${userId}`),
  getUserDocuments: (userId) => chatApiClient.get(`/documents/${userId}`),
  chatStream: async function* (data) {
    const token = localStorage.getItem('token')
    const userId = localStorage.getItem('userId')
    const response = await fetch('/api/v1/chat/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
        'X-User-Id': userId || '',
        'X-User-Key': userId || '',
        'X-User-Role': localStorage.getItem('role') || 'USER'
      },
      body: JSON.stringify(data)
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          try {
            const json = JSON.parse(line.slice(6))
            yield json
          } catch (e) {}
        }
      }
    }
  }
}

export const knowledgeGraphAPI = {
  getData: (userId) => apiClient.get('/kg/data', { params: { userId } }),
  queryTemplate: (data) => apiClient.post('/kg/query-template', data),
  enrich: (data) => apiClient.put('/kg/enrich', data),
  initialize: () => apiClient.post('/kg/initialize'),
  deleteUserDocument: (userId, docId) => apiClient.delete('/kg/user-document', { params: { userId, docId } })
}

export const vectorAPI = {
  search: (data) => apiClient.post('/vector/search', data),
  searchWithEvidence: (data) => apiClient.post('/vector/search-with-evidence', data),
  getParagraphEvidence: (paragraphId) => apiClient.get(`/vector/paragraphs/${paragraphId}/evidence`)
}

export const feedbackAPI = {
  create: (data) => apiClient.post('/feedback', data),
  getById: (feedbackId) => apiClient.get(`/feedback/${feedbackId}`),
  getByTreeId: (treeId) => apiClient.get(`/feedback/tree/${treeId}`),
  getByUserId: (userId) => apiClient.get(`/feedback/user/${userId}`),
  getAll: () => apiClient.get('/feedback'),
  processBatch: (data) => apiClient.post('/feedback/process-batch', data),
  optimizeModels: () => apiClient.post('/feedback/optimize-models')
}

export const statsAPI = {
  getDashboardStats: () => apiClient.get('/stats/dashboard')
}

export default {
  auth: authAPI,
  document: documentAPI,
  faultTree: faultTreeAPI,
  validation: validationAPI,
  rag: ragAPI,
  knowledgeGraph: knowledgeGraphAPI,
  vector: vectorAPI,
  feedback: feedbackAPI,
  stats: statsAPI
}
