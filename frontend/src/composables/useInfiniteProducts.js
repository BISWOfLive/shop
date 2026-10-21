import { computed, reactive, watch } from 'vue'
import { useCatalogProducts } from './useCatalogProducts.js'

const batchSize = 24

export function useInfiniteProducts(getQuery) {
  const query = reactive({ keyword: '', categoryId: '', status: 1, page: 1, size: batchSize, sortBy: 'createdAt', sortOrder: 'desc', enabled: true })
  const catalog = useCatalogProducts(query)
  const error = computed(() => catalog.listError.value || catalog.moreError.value)

  watch(() => JSON.stringify(getQuery()), () => {
    const current = getQuery()
    Object.assign(query, {
      keyword: current.keyword || '', categoryId: current.categoryId || '',
      sortBy: current.sortBy || 'createdAt', sortOrder: current.sortOrder || 'desc',
      size: current.size || batchSize, page: 1, enabled: current.enabled !== false,
    })
    catalog.loadProducts()
  }, { immediate: true })

  function loadNext() {
    if (query.enabled && !error.value) return catalog.loadMore()
  }

  function retry() {
    if (!catalog.products.value.length) return catalog.loadProducts()
    return catalog.loadMore()
  }

  return { ...catalog, error, loadNext, retry }
}
