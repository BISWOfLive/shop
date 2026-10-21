import { computed, onBeforeUnmount, ref } from 'vue'
import { getProductFeed, getProducts } from '../api/products.js'

function normalize(product) {
  return { ...product, id: String(product.id), categoryId: String(product.categoryId ?? '') }
}

export function useCatalogProducts(query) {
  const products = ref([])
  const total = ref(0)
  const loading = ref(true)
  const loadingMore = ref(false)
  const listError = ref('')
  const moreError = ref('')
  const hasMore = ref(false)
  const feedMode = computed(() => !query.keyword && (
    (!query.categoryId && query.sortBy === 'createdAt' && query.sortOrder === 'desc') ||
    (Boolean(query.categoryId) && query.sortBy === 'price' && query.sortOrder === 'asc')
  ))
  let controller
  let generation = 0
  let disposed = false
  let nextCursor = null
  let nextPage = 2
  let feedQuery
  let pageQuery
  let prepared
  let seen = new Set()

  function prepareNext(currentGeneration) {
    if (!hasMore.value || disposed || currentGeneration !== generation) return
    const cursor = feedMode.value ? nextCursor : 'page:' + nextPage
    const signal = controller.signal
    const pending = feedMode.value
      ? getProductFeed({ ...feedQuery, cursor }, signal)
      : getProducts({ ...pageQuery, page: nextPage, includeTotal: false }, signal)
    const promise = pending.then(result => {
      // 仅预取一批，且只预热其中少量图片；不随预取结果自动继续查询。
      if (!disposed && generation === currentGeneration && !signal.aborted &&
          !navigator.connection?.saveData && !/2g/.test(navigator.connection?.effectiveType || '')) {
        for (const product of result.records.slice(0, 6)) {
          if (!product.imageUrl) continue
          const image = new Image()
          image.decoding = 'async'
          image.src = product.imageUrl
        }
      }
      return { result }
    }, error => ({ error }))
    prepared = { cursor, promise }
  }

  async function loadProducts() {
    const currentGeneration = ++generation
    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    const useFeed = feedMode.value
    const { enabled, ...snapshot } = { ...query }
    products.value = []
    total.value = 0
    hasMore.value = false
    nextCursor = null
    nextPage = snapshot.page + 1
    prepared = null
    seen = new Set()
    loading.value = true
    loadingMore.value = false
    listError.value = ''
    moreError.value = ''
    feedQuery = { sortBy: snapshot.sortBy, categoryId: snapshot.categoryId, size: snapshot.size }
    pageQuery = snapshot
    if (enabled === false) {
      loading.value = false
      return
    }
    try {
      const result = useFeed ? await getProductFeed(feedQuery, signal) : await getProducts(snapshot, signal)
      if (disposed || currentGeneration !== generation) return
      if (!useFeed) {
        const maxPage = Math.max(1, Math.ceil(result.total / snapshot.size))
        if (query.page > maxPage) {
          query.page = maxPage
          return await loadProducts()
        }
        total.value = result.total
      }
      products.value = result.records.map(normalize)
      seen = new Set(products.value.map(product => product.id))
      if (useFeed) {
        nextCursor = result.nextCursor
        hasMore.value = Boolean(result.hasMore && nextCursor)
      } else {
        hasMore.value = snapshot.page * snapshot.size < total.value && result.records.length === snapshot.size
      }
      prepareNext(currentGeneration)
    } catch (error) {
      if (error.name !== 'AbortError' && !disposed && currentGeneration === generation) {
        listError.value = error.message
      }
    } finally {
      if (!disposed && currentGeneration === generation) loading.value = false
    }
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || !hasMore.value || disposed) return
    const currentGeneration = generation
    loadingMore.value = true
    moreError.value = ''
    const cursor = feedMode.value ? nextCursor : 'page:' + nextPage
    if (!prepared || prepared.cursor !== cursor) prepareNext(currentGeneration)
    const batch = prepared
    try {
      const { result, error } = await batch.promise
      if (disposed || currentGeneration !== generation) return
      prepared = null
      if (error) throw error
      const additions = []
      for (const record of result.records) {
        const product = normalize(record)
        if (seen.has(product.id)) continue
        seen.add(product.id)
        additions.push(product)
      }
      products.value.push(...additions)
      if (feedMode.value) {
        nextCursor = result.nextCursor
        hasMore.value = Boolean(result.hasMore && nextCursor)
      } else {
        nextPage += 1
        hasMore.value = result.records.length === pageQuery.size && (nextPage - 1) * pageQuery.size < total.value
      }
      prepareNext(currentGeneration)
    } catch (error) {
      if (error.name !== 'AbortError' && !disposed && currentGeneration === generation) {
        // 已有商品与游标保持不变，下一次点击重试仍取同一批。
        moreError.value = error.message
      }
    } finally {
      if (!disposed && currentGeneration === generation) loadingMore.value = false
    }
  }

  onBeforeUnmount(() => {
    disposed = true
    generation += 1
    controller?.abort()
  })

  return { products, total, loading, loadingMore, listError, moreError, hasMore, feedMode, loadProducts, loadMore }
}
