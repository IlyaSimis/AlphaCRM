import { apiFetch, failure } from './client'

export type PostgresImportConfig = {
  url: string
  username: string
  password: string
  table: string
  columns: { fullName: string; birthDate: string; phone: string; secondPhone: string; email: string }
  limit: number
}

export type ImportRow = {
  row: number
  fullName: string
  birthDate: string
  phone: string
  secondPhone: string
  email: string
  status: 'NEW' | 'DUPLICATE' | 'ERROR'
  reason: string
  existingPatientName: string | null
}

export type ImportPreview = { total: number; created: number; duplicates: number; errors: number; rows: ImportRow[] }
export type ImportResult = Omit<ImportPreview, 'rows'>

async function send(path: string, config: PostgresImportConfig) {
  const response = await apiFetch(`/patients/import/postgresql/${path}`, { method: 'POST', body: JSON.stringify(config) })
  if (!response.ok) throw await failure(response, 'Не удалось выполнить импорт из PostgreSQL')
  return response.json()
}

export const previewPostgresImport = (config: PostgresImportConfig): Promise<ImportPreview> => send('preview', config)
export const commitPostgresImport = (config: PostgresImportConfig): Promise<ImportResult> => send('commit', config)
