import { apiFetch, failure } from './client'

export type AssistantUsage = { planCode: string; planName: string; monthlyAiRequests: number; usedAiRequests: number; remainingAiRequests: number }
export type AssistantStatus = { enabled: boolean; model: string; usage: AssistantUsage }
export type AssistantDestination = { id: string; title: string; description: string; keywords: string[] }

export async function loadAssistantStatus(): Promise<AssistantStatus> {
  const response = await apiFetch('/assistant/status')
  if (!response.ok) throw await failure(response, 'Не удалось получить состояние ИИ-навигатора')
  return response.json() as Promise<AssistantStatus>
}

export async function navigateWithAssistant(query: string, currentPage: string, destinations: AssistantDestination[]): Promise<{ destinationId: string; source: string; usage: AssistantUsage }> {
  const response = await apiFetch('/assistant/navigate', { method: 'POST', body: JSON.stringify({ query, currentPage, destinations }) })
  if (!response.ok) throw await failure(response, 'ИИ-навигатор временно недоступен')
  return response.json() as Promise<{ destinationId: string; source: string; usage: AssistantUsage }>
}
