import type { Category } from '../types/category'
import { apiRequest } from './apiClient'

export const categoryApi = {
  getCategories() {
    return apiRequest<Category[]>('/api/v1/categories')
  },
}