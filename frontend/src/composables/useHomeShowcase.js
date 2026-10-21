import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getProductFeed, getProducts } from '../api/products'

function normalizeProduct(product) {
  return {
    ...product,
    id: String(product.id),
    categoryId: product.categoryId == null ? '' : String(product.categoryId),
  }
}

export function useHomeShowcase() {
  const latestProducts = ref([])
  const affordableProducts = ref([])
  const categoryProducts = ref({})
  const homeLoading = ref(true)
  const affordableLoading = ref(true)
  const latestError = ref('')
  const affordableError = ref('')
  let latestController
  let affordableController
  let categoryController
  let latestRequestId = 0
  let affordableRequestId = 0
  let categoryRequestId = 0
  let disposed = false
  let idleHandle
  let idleTimer

  const campaignProducts = computed(() => {
    const products = [...latestProducts.value, ...affordableProducts.value]
    return [...new Map(products.map(product => [product.id, product])).values()].slice(0, 6)
  })

  async function loadLatest() {
    const currentId = ++latestRequestId
    latestController?.abort()
    latestController = new AbortController()
    homeLoading.value = true
    latestError.value = ''
    try {
      const result = await getProductFeed({ size: 8, sortBy: 'createdAt' }, latestController.signal)
      if (!disposed && currentId === latestRequestId) latestProducts.value = result.records.map(normalizeProduct)
    } catch (error) {
      if (error.name !== 'AbortError' && !disposed && currentId === latestRequestId) latestError.value = error.message
    } finally {
      if (!disposed && currentId === latestRequestId) homeLoading.value = false
    }
  }

  async function loadAffordable() {
    const currentId = ++affordableRequestId
    affordableController?.abort()
    affordableController = new AbortController()
    affordableLoading.value = true
    affordableError.value = ''
    try {
      const result = await getProducts({ status: 1, page: 1, size: 4, sortBy: 'price', sortOrder: 'asc', includeTotal: false }, affordableController.signal)
      if (!disposed && currentId === affordableRequestId) affordableProducts.value = result.records.map(normalizeProduct)
    } catch (error) {
      if (error.name !== 'AbortError' && !disposed && currentId === affordableRequestId) affordableError.value = error.message
    } finally {
      if (!disposed && currentId === affordableRequestId) affordableLoading.value = false
    }
  }

  function cancelDeferred() {
    if (idleHandle !== undefined) window.cancelIdleCallback(idleHandle)
    window.clearTimeout(idleTimer)
  }

  function loadHomeShowcase() {
    cancelDeferred()
    // 两块独立更新；上新首屏不等待全站价格排序。
    return Promise.allSettled([loadLatest(), loadAffordable()])
  }

  async function loadCategoryShowcase(categories) {
    const currentId = ++categoryRequestId
    categoryController?.abort()
    categoryController = new AbortController()
    const featuredCategories = categories.slice(0, 4)
    const results = await Promise.allSettled(featuredCategories.map(category =>
      getProductFeed({ categoryId: category.id, size: 1, sortBy: 'price' }, categoryController.signal),
    ))
    if (disposed || currentId !== categoryRequestId) return
    const nextProducts = {}
    results.forEach((result, index) => {
      if (result.status === 'fulfilled' && result.value.records.length) {
        nextProducts[featuredCategories[index].id] = normalizeProduct(result.value.records[0])
      }
    })
    categoryProducts.value = nextProducts
  }

  onMounted(() => {
    loadLatest()
    // 首屏请求先行，低价推荐在浏览器空闲时调度。
    idleTimer = window.setTimeout(() => {
      if (disposed) return
      if ('requestIdleCallback' in window) {
        idleHandle = window.requestIdleCallback(() => { if (!disposed) loadAffordable() }, { timeout: 1000 })
      } else loadAffordable()
    }, 800)
  })
  onBeforeUnmount(() => {
    disposed = true
    cancelDeferred()
    latestController?.abort()
    affordableController?.abort()
    categoryController?.abort()
  })

  return {
    latestProducts, affordableProducts, categoryProducts, campaignProducts,
    homeLoading, affordableLoading, latestError, affordableError, loadHomeShowcase, loadCategoryShowcase,
  }
}
