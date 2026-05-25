/**
 * Shared API response types — single source of truth for backend payload shapes.
 */

/** Standard backend envelope */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** Paginated list wrapper */
export interface PaginatedData<T = unknown> {
  items: T[]
  total: number
  page: number
  size: number
}

/** Narrow a raw axios response to the inner data payload */
export type ApiData<R> = R extends ApiResponse<infer T> ? T : never
