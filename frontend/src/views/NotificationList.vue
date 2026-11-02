<script setup>
import { onMounted } from 'vue'
import { useNotifications } from '../composables/useNotifications.js'
import { orderTime } from '../utils/orderPresentation.js'

const {
  notifications, total, page, pages, unread, loading, error,
  refresh, refreshAll, refreshUnreadCount, markRead, setUnreadFilter, goPage,
} = useNotifications()

const FILTERS = [
  { label: '全部', value: null },
  { label: '未读', value: true },
  { label: '已读', value: false },
]

onMounted(() => {
  // 进入通知页时同时读取当前筛选列表与本人未读数。
  refreshAll()
})
</script>

<template>
  <section class="trade-page notification-page">
    <div class="trade-heading">
      <h2>通知</h2>
      <button class="button secondary" type="button" :disabled="loading" @click="refreshAll">刷新</button>
    </div>
    <div class="notification-filters" role="group" aria-label="通知筛选">
      <button v-for="filter in FILTERS" :key="String(filter.value)" type="button"
              class="button secondary" :class="{ active: unread === filter.value }"
              :aria-pressed="unread === filter.value" :disabled="loading"
              @click="setUnreadFilter(filter.value)">{{ filter.label }}</button>
    </div>
    <p v-if="error" class="trade-error" role="alert">{{ error }}</p>
    <p v-if="loading" class="notification-state" role="status">正在加载通知…</p>
    <p v-else-if="!notifications.length" class="trade-empty">没有符合条件的通知</p>
    <ul v-else class="notification-list">
      <li v-for="item in notifications" :key="item.id" :class="{ unread: !item.readAt }">
        <div class="notification-head">
          <strong>{{ item.title }}</strong>
          <span class="status-badge">{{ item.readAt ? '已读' : '未读' }}</span>
        </div>
        <p class="notification-content">{{ item.content }}</p>
        <div class="notification-meta">
          <time :datetime="item.createdAt">{{ orderTime(item.createdAt) }}</time>
          <a v-if="item.orderNo" :href="'#orders/' + item.orderNo">查看订单</a>
          <button v-if="!item.readAt" type="button" class="button secondary" @click="markRead(item.id)">标为已读</button>
        </div>
      </li>
    </ul>
    <div v-if="pages > 1" class="trade-pagination">
      <button class="button secondary" type="button" :disabled="loading || page <= 1" @click="goPage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ pages }} 页，共 {{ total }} 条</span>
      <button class="button secondary" type="button" :disabled="loading || page >= pages" @click="goPage(page + 1)">下一页</button>
    </div>
  </section>
</template>
