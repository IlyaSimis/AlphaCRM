import { apiFetch, failure } from './client'
import type { ContractTemplateItem, PatientContractItem } from './types'

export const MAX_CONTRACT_TEMPLATE_BYTES = 5 * 1024 * 1024

export async function loadContractTemplates(): Promise<ContractTemplateItem[]> {
  const response = await apiFetch('/contract-templates')
  if (!response.ok) throw await failure(response, 'Шаблоны договоров недоступны')
  const rows: unknown = await response.json()
  return Array.isArray(rows) ? rows.filter((row): row is ContractTemplateItem => typeof row?.id === 'string' && typeof row?.name === 'string' && Array.isArray(row?.placeholders)) : []
}

export async function uploadContractTemplate(name: string, file: File): Promise<ContractTemplateItem> {
  const body = new FormData()
  body.append('name', name)
  body.append('file', file, file.name)
  const response = await apiFetch('/contract-templates', { method: 'POST', body })
  if (!response.ok) throw await failure(response, 'Не удалось загрузить шаблон', { 413: 'Шаблон больше 5 МБ' })
  return response.json()
}

export async function loadPatientContracts(patientId: string): Promise<PatientContractItem[]> {
  const response = await apiFetch(`/patients/${patientId}/contracts`)
  if (!response.ok) throw await failure(response, 'Договоры пациента недоступны')
  const rows: unknown = await response.json()
  return Array.isArray(rows) ? rows.filter((row): row is PatientContractItem => typeof row?.id === 'string' && typeof row?.contractNumber === 'string') : []
}

export async function createPatientContract(patientId: string, input: { templateId: string; serviceId?: string; values: Record<string, string> }): Promise<PatientContractItem> {
  const response = await apiFetch(`/patients/${patientId}/contracts`, { method: 'POST', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось сформировать договор')
  return response.json()
}

/** Файл расшифровывается только после серверной проверки сессии; публичной ссылки нет. */
export async function downloadPatientContract(contract: Pick<PatientContractItem, 'id' | 'contractNumber'>, format: 'docx' | 'pdf') {
  const response = await apiFetch(`/patient-contracts/${contract.id}/${format}`)
  if (!response.ok) throw await failure(response, 'Не удалось скачать договор')
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = `${contract.contractNumber}.${format}`
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function contractTemplateProblem(file: File): string | null {
  if (file.size > MAX_CONTRACT_TEMPLATE_BYTES) return 'Шаблон больше 5 МБ'
  return file.name.toLocaleLowerCase('ru-RU').endsWith('.docx') ? null : 'Можно загрузить только DOCX'
}
