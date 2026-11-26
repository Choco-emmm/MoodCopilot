import { api } from './core'
import type { ApiResponse } from './types'
import {
  ONBOARDING_GUIDE_VERSION,
  normalizeOnboardingState,
  type OnboardingDismissRequest,
  type OnboardingEventRequest,
  type OnboardingState,
} from '../constants/onboarding'

const ONBOARDING_TIMEOUT_MS = 8000

function unwrapOnboardingResponse(payload: ApiResponse<OnboardingState> | undefined): OnboardingState {
  if (!payload || (typeof payload.code === 'number' && payload.code !== 0) || !payload.data) {
    throw new Error(payload?.message || '无效的引导状态响应')
  }
  return normalizeOnboardingState(payload.data)
}

export async function fetchOnboardingState(): Promise<OnboardingState> {
  const res = await api.get<ApiResponse<OnboardingState>>('/onboarding', {
    params: { scope: 'WEB' },
    timeout: ONBOARDING_TIMEOUT_MS,
  })
  return unwrapOnboardingResponse(res.data)
}

export async function postOnboardingEvent(
  payload: OnboardingEventRequest,
): Promise<OnboardingState> {
  const res = await api.post<ApiResponse<OnboardingState>>(
    '/onboarding/events',
    {
      ...payload,
      scope: payload.scope ?? 'WEB',
      guideVersion: payload.guideVersion ?? ONBOARDING_GUIDE_VERSION,
    },
    {
      timeout: ONBOARDING_TIMEOUT_MS,
    },
  )
  return unwrapOnboardingResponse(res.data)
}

export async function postOnboardingDismiss(
  payload: OnboardingDismissRequest,
): Promise<OnboardingState> {
  const res = await api.post<ApiResponse<OnboardingState>>(
    '/onboarding/dismiss',
    {
      ...payload,
      scope: payload.scope ?? 'WEB',
      guideVersion: payload.guideVersion ?? ONBOARDING_GUIDE_VERSION,
    },
    {
      timeout: ONBOARDING_TIMEOUT_MS,
    },
  )
  return unwrapOnboardingResponse(res.data)
}

export const onboardingApi = {
  getState: fetchOnboardingState,
  recordEvent: postOnboardingEvent,
  dismiss: postOnboardingDismiss,
}
