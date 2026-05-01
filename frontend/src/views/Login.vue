<template>
  <div class="login-container" aria-label="登录页面">
    <div class="login-box">
      <div class="login-header">
        <el-icon class="logo-icon" aria-label="系统Logo"><Grid /></el-icon>
        <h1 class="title">工业设备故障树智能生成系统</h1>
        <p class="subtitle">欢迎回来，请登录您的账户</p>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" class="login-form">
            <el-form-item prop="username" label="用户名" label-width="60px">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                clearable
                aria-label="用户名"
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password" label="密码" label-width="60px">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                aria-label="密码"
                @keyup.enter="handleLogin"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item>
              <div class="form-options">
                <el-checkbox v-model="loginForm.rememberMe">记住我</el-checkbox>
                <el-link type="primary" aria-label="忘记密码">忘记密码？</el-link>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                class="login-button"
                :loading="loginLoading"
                @click="handleLogin"
                aria-label="登录"
              >
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" class="login-form">
            <el-form-item prop="username" label="用户名" label-width="60px">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名"
                size="large"
                clearable
                aria-label="用户名"
              >
                <template #prefix>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="email" label="邮箱" label-width="60px">
              <el-input
                v-model="registerForm.email"
                placeholder="请输入邮箱"
                size="large"
                clearable
                aria-label="邮箱"
              >
                <template #prefix>
                  <el-icon><Message /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password" label="密码" label-width="60px">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                show-password
                aria-label="密码"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item prop="confirmPassword" label="确认密码" label-width="60px">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请确认密码"
                size="large"
                show-password
                aria-label="确认密码"
                @keyup.enter="handleRegister"
              >
                <template #prefix>
                  <el-icon><Lock /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                class="login-button"
                :loading="registerLoading"
                @click="handleRegister"
                aria-label="注册"
              >
                注册
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="login-footer">
        <el-divider>
          <span style="color: #909399;">其他登录方式</span>
        </el-divider>
        <div class="social-login">
          <el-button circle aria-label="社交登录1">
            <el-icon><ChatDotRound /></el-icon>
          </el-button>
          <el-button circle aria-label="社交登录2">
            <el-icon><Share /></el-icon>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Grid, User, Lock, Message, ChatDotRound, Share
} from '@element-plus/icons-vue'
import { authAPI } from '@/api'

const router = useRouter()

const activeTab = ref('login')

const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginLoading = ref(false)
const registerLoading = ref(false)

const loginForm = reactive({
  username: '',
  password: '',
  rememberMe: false
})

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 个字符', trigger: 'blur' }
  ]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
    loginLoading.value = true

    const response = await authAPI.login({
      username: loginForm.username,
      password: loginForm.password
    })

    if (response && response.accessToken) {
      localStorage.setItem('token', response.accessToken)
      localStorage.setItem('userId', response.userId || loginForm.username)
      localStorage.setItem('role', response.role || 'USER')
      localStorage.setItem('username', loginForm.username)
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error('登录失败，请检查用户名和密码')
    }
  } catch (error) {
    console.error('登录失败:', error)
    if (error !== false) {
      ElMessage.error(error.response?.data?.message || '登录失败，请稍后重试')
    }
  } finally {
    loginLoading.value = false
  }
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  try {
    await registerFormRef.value.validate()
    registerLoading.value = true

    const response = await authAPI.register({
      username: registerForm.username,
      email: registerForm.email,
      password: registerForm.password
    })

    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.username = registerForm.username
  } catch (error) {
    console.error('注册失败:', error)
    if (error !== false) {
      ElMessage.error(error.response?.data?.message || '注册失败，请稍后重试')
    }
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>

.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
  padding: 16px;
}

.login-box {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 6px 32px rgba(30, 41, 59, 0.10);
  padding: 32px 18px 28px 18px;
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}


.logo-icon {
  font-size: 44px;
  color: #2563eb;
  margin-bottom: 10px;
}


.title {
  font-size: 21px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px 0;
}


.subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}


.login-tabs {
  margin-bottom: 18px;
}


.login-form {
  margin-top: 18px;
}


.form-options {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}


.login-button {
  width: 100%;
  margin-top: 10px;
  font-size: 16px;
  border-radius: 8px;
  transition: background 0.2s, box-shadow 0.2s;
}
.login-button:focus {
  box-shadow: 0 0 0 2px #2563eb33;
}


.login-footer {
  margin-top: 28px;
}


.social-login {
  display: flex;
  justify-content: center;
  gap: 18px;
  margin-top: 18px;
}

@media (max-width: 600px) {
  .login-box {
    padding: 16px 4vw 16px 4vw;
    max-width: 98vw;
  }
  .login-header {
    margin-bottom: 18px;
  }
  .login-footer {
    margin-top: 18px;
  }
}
</style>
