/**
 * Auth token helpers — single source of truth.
 */

export function isUsableToken(token: string | null): boolean {
  if (!token) return false
  const normalized = token.trim().toLowerCase()
  return normalized !== '' && normalized !== 'null' && normalized !== 'undefined'
}

export function getStoredToken(): string | null {
  const token = localStorage.getItem('token')
  if (!isUsableToken(token)) {
    localStorage.removeItem('token')
    return null
  }
  return token
}

export function clearAuthStorage(): void {
  localStorage.removeItem('token')
  localStorage.removeItem('role')
}
