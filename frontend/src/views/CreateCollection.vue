<template>
  <div class="min-h-screen bg-[#F9F8F6]">
    <!-- 顶部区域：大面积留白，居中显示标题 -->
    <div class="container mx-auto px-6 py-12">
      <div class="max-w-2xl mx-auto">
        <div class="text-center mb-12">
          <h1 class="text-4xl font-serif tracking-wide text-[#2C2C2A] mb-4">
            创建新合集
          </h1>
          <p class="text-stone-500 italic">
            整理你的日记，创建专属合集
          </p>
        </div>

        <!-- 创建表单：杂志风格 -->
        <div class="bg-white rounded-sm border border-[#EAE6DF] p-8 shadow-none">
          <form @submit.prevent="handleSubmit">
            <!-- 合集名称 -->
            <div class="mb-6">
              <label class="block text-[#2C2C2A] font-medium mb-3" for="name">
                合集名称 <span class="text-red-500">*</span>
              </label>
              <input
                v-model="formData.name"
                type="text"
                id="name"
                class="w-full px-4 py-3 border border-[#EAE6DF] rounded-sm focus:outline-none focus:border-[#2C2C2A] bg-white"
                placeholder="给你的合集起个名字"
                required
              />
            </div>

            <!-- 合集描述 -->
            <div class="mb-6">
              <label class="block text-[#2C2C2A] font-medium mb-3" for="description">
                合集描述
              </label>
              <textarea
                v-model="formData.description"
                id="description"
                rows="4"
                class="w-full px-4 py-3 border border-[#EAE6DF] rounded-sm focus:outline-none focus:border-[#2C2C2A] bg-white"
                placeholder="描述一下这个合集的内容..."
              ></textarea>
            </div>

            <!-- 封面图片 -->
            <div class="mb-6">
              <label class="block text-[#2C2C2A] font-medium mb-3" for="coverUrl">
                封面图片
              </label>
              <div class="flex items-center gap-4">
                <input
                  v-model="formData.coverUrl"
                  type="url"
                  id="coverUrl"
                  class="flex-1 px-4 py-3 border border-[#EAE6DF] rounded-sm focus:outline-none focus:border-[#2C2C2A] bg-white"
                  placeholder="输入图片URL"
                />
                <button
                  type="button"
                  class="px-4 py-3 border border-[#EAE6DF] rounded-sm text-[#2C2C2A] hover:bg-[#F3F1EC] transition-colors"
                  @click="generateRandomCover"
                >
                  随机封面
                </button>
              </div>
            </div>

            <!-- 可见性设置 -->
            <div class="mb-8">
              <label class="block text-[#2C2C2A] font-medium mb-3">
                可见性
              </label>
              <div class="flex gap-4">
                <label class="flex items-center">
                  <input
                    v-model="formData.visibility"
                    type="radio"
                    value="PUBLIC"
                    class="mr-2"
                  />
                  <span class="text-[#2C2C2A]">公开</span>
                </label>
                <label class="flex items-center">
                  <input
                    v-model="formData.visibility"
                    type="radio"
                    value="PRIVATE"
                    class="mr-2"
                  />
                  <span class="text-[#2C2C2A]">私密</span>
                </label>
              </div>
            </div>

            <!-- 提交按钮 -->
            <div class="flex justify-end gap-4">
              <button
                type="button"
                class="px-6 py-3 border border-[#EAE6DF] rounded-sm text-[#2C2C2A] hover:bg-[#F3F1EC] transition-colors"
                @click="resetForm"
              >
                重置
              </button>
              <button
                type="submit"
                class="px-6 py-3 bg-[#2C2C2A] text-white rounded-sm hover:bg-[#1a1a17] transition-colors"
                :disabled="loading"
              >
                {{ loading ? '创建中...' : '创建合集' }}
              </button>
            </div>
          </form>
        </div>

        <!-- 成功提示 -->
        <div v-if="showSuccess" class="mt-6 p-4 bg-green-50 border border-green-200 rounded-sm text-green-800">
          合集创建成功！正在跳转...
        </div>

        <!-- 错误提示 -->
        <div v-if="errorMessage" class="mt-6 p-4 bg-red-50 border border-red-200 rounded-sm text-red-800">
          {{ errorMessage }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { collectionApi } from '../api'

const router = useRouter()
const loading = ref(false)
const showSuccess = ref(false)
const errorMessage = ref('')

// 表单数据
const formData = ref({
  name: '',
  description: '',
  coverUrl: '',
  visibility: 'PUBLIC' as 'PUBLIC' | 'PRIVATE'
})

// 生成随机封面
const generateRandomCover = () => {
  const randomId = Math.floor(Math.random() * 1000)
  formData.value.coverUrl = `https://picsum.photos/seed/collection${randomId}/400/300.jpg`
}

// 重置表单
const resetForm = () => {
  formData.value = {
    name: '',
    description: '',
    coverUrl: '',
    visibility: 'PUBLIC'
  }
  errorMessage.value = ''
}

// 提交表单
const handleSubmit = async () => {
  if (!formData.value.name.trim()) {
    errorMessage.value = '请输入合集名称'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await collectionApi.create(formData.value)
    showSuccess.value = true

    // 延迟跳转
    setTimeout(() => {
      router.push(`/collections/${response.data.id}`)
    }, 1500)
  } catch (error: any) {
    errorMessage.value = error.response?.data?.message || '创建合集失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 杂志风格样式 */
.container {
  max-width: 1200px;
}

.font-serif {
  font-family: 'Georgia', 'Times New Roman', serif;
}

/* 输入框样式 */
input[type="text"],
input[type="url"],
textarea {
  transition: border-color 0.2s;
}

input[type="text"]:focus,
input[type="url"]:focus,
textarea:focus {
  border-color: #2C2C2A;
}

/* 按钮样式 */
button {
  transition: all 0.2s;
}

button:hover {
  transform: translateY(-1px);
}

/* 表单容器样式 */
.bg-white {
  background: #ffffff;
}

.border-[#EAE6DF] {
  border-color: #EAE6DF;
}

.text-[#2C2C2A] {
  color: #2C2C2A;
}

.bg-[#2C2C2A] {
  background-color: #2C2C2A;
}

.hover\:bg-\[\#F3F1EC\]:hover {
  background-color: #F3F1EC;
}

.hover\:bg-\[\#1a1a17\]:hover {
  background-color: #1a1a17;
}
</style>