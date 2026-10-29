import { apiFetch, failure } from './client'
import type { AssistantUsage } from './assistant'

export type CommercialPlan = { code: 'FREE' | 'TEAM' | 'CLINIC'; name: string; description: string; priceMinor: number; currency: string; monthlyAiRequests: number }
export type SubscriptionPayment = { id: string; planCode: string; amountMinor: number; currency: string; status: string; provider: string; createdAt: string; paidAt: string | null }
export type Subscription = AssistantUsage & { currentPeriodEnd: string | null; checkoutEnabled: boolean; plans: CommercialPlan[]; payments: SubscriptionPayment[] }

export async function loadSubscription(): Promise<Subscription> {
  const response = await apiFetch('/commercial/subscription')
  if (!response.ok) throw await failure(response, 'Не удалось загрузить тариф')
  return response.json() as Promise<Subscription>
}

export async function createSubscriptionCheckout(planCode: string): Promise<{ paymentId: string; checkoutUrl: string }> {
  const response = await apiFetch('/commercial/subscription/checkout', { method: 'POST', body: JSON.stringify({ planCode }) })
  if (!response.ok) throw await failure(response, 'Не удалось создать оплату')
  return response.json() as Promise<{ paymentId: string; checkoutUrl: string }>
}
