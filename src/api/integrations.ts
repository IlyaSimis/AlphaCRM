import { apiFetch, failure } from './client'

export type IntegrationType = 'MANGO' | 'TELEGRAM' | 'MAX' | 'YANDEX_METRICA'
export type IntegrationStatus = 'PENDING' | 'ACTIVE' | 'DISABLED' | 'ERROR'

export type IntegrationSettings = {
  apiUrl?: string
  defaultExtension?: string
  lineNumber?: string
  publicUrl?: string
  relayUrl?: string
  webhookBaseUrl?: string
  reminderButtons?: boolean
  counterId?: string
  offlineGoalId?: string
}

export type IntegrationWebhookStatus = {
  state: 'CONNECTED' | 'NOT_CONFIGURED' | 'ERROR'
  url: string
  detail: string | null
  pendingUpdates: number | null
}

export type IntegrationView = {
  id: string
  type: IntegrationType
  status: IntegrationStatus
  publicKey: string
  settings: IntegrationSettings
  hasSecret: boolean
  secretVersion: number | null
}

export type IntegrationSecretInput = {
  apiKey?: string
  apiSalt?: string
  botToken?: string
  relaySecret?: string
  webhookSecret?: string
  oauthToken?: string
}

export type MetricaOfflineConversionsStatus = {
  configured: boolean
  attributed: number
  pending: number
  uploading: number
  uploaded: number
  failed: number
  lastUploadedAt: string | null
  lastError: string | null
}

async function request<T = IntegrationView>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const response = await apiFetch(path, { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
  if (!response.ok) throw await failure(response, 'Не удалось изменить интеграцию')
  return response.json()
}

export async function loadIntegrations(): Promise<IntegrationView[]> {
  const response = await apiFetch('/integrations')
  if (!response.ok) throw await failure(response, 'Интеграции недоступны')
  return response.json()
}

export function saveIntegrationSettings(type: IntegrationType, settings: IntegrationSettings) {
  return request(`/integrations/${type.toLowerCase()}/settings`, 'PUT', settings)
}

export function saveIntegrationSecret(type: IntegrationType, secret: IntegrationSecretInput) {
  return request(`/integrations/${type.toLowerCase()}/secret`, 'PUT', secret)
}

export function changeIntegrationStatus(type: IntegrationType, action: 'activate' | 'disable') {
  return request(`/integrations/${type.toLowerCase()}/${action}`, 'POST')
}

export function connectIntegrationWebhook(type: IntegrationType) {
  return request<IntegrationWebhookStatus>(`/integrations/${type.toLowerCase()}/webhook/connect`, 'POST')
}

export function checkIntegrationWebhookStatus(type: IntegrationType) {
  return request<IntegrationWebhookStatus>(`/integrations/${type.toLowerCase()}/webhook/status`)
}

export function loadMetricaOfflineConversions() {
  return request<MetricaOfflineConversionsStatus>('/integrations/yandex_metrica/offline-conversions')
}

export function uploadMetricaOfflineConversions() {
  return request<{ uploaded: number; uploadingId: string | null }>('/integrations/yandex_metrica/offline-conversions/upload', 'POST')
}
