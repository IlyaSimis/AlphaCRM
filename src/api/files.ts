import { API, apiFetch, failure } from './client'
import type { PatientFileItem } from './types'

/** Предел размера PDF — как на сервере. */
export const MAX_PDF_BYTES = 10 * 1024 * 1024

export async function loadPatientFiles(patientId: string): Promise<PatientFileItem[]> {
  const response = await apiFetch(`/patients/${patientId}/files`)
  if (!response.ok) throw await failure(response, 'Документы PDF недоступны')
  return fileRows(await response.json())
}

/** appointmentId — привязать файл к записи: так врач загружает заключение со страницы приёма. */
export async function uploadPatientFile(patientId: string, file: File, appointmentId?: string): Promise<PatientFileItem> {
  const body = new FormData()
  body.append('file', file, file.name)
  if (appointmentId) body.append('appointmentId', appointmentId)
  const response = await apiFetch(`/patients/${patientId}/files`, { method: 'POST', body })
  if (!response.ok) throw await failure(response, 'Не удалось загрузить файл', { 413: 'Файл больше 10 МБ' })
  return response.json()
}

export async function signPatientFile(id: string): Promise<PatientFileItem> {
  const response = await apiFetch(`/patient-files/${id}/sign`, { method: 'POST' })
  if (!response.ok) throw await failure(response, 'Не удалось подписать файл')
  return response.json()
}

/** Архив подписанных PDF: врачу — подписанные им, менеджеру — все файлы клиники. */
export async function loadSignedFiles(): Promise<PatientFileItem[]> {
  const response = await apiFetch('/patient-files/signed')
  if (!response.ok) throw await failure(response, 'Подписанные PDF недоступны')
  return fileRows(await response.json())
}

/**
 * Скачивает расшифрованный файл с сессией сотрудника и сохраняет его через временную ссылку
 * браузера. Публичной ссылки на файл нет.
 */
export async function previewPatientFile(file: Pick<PatientFileItem, 'id'>): Promise<Blob> {
  const response = await apiFetch(`/patient-files/${file.id}/inline`)
  if (!response.ok) throw await failure(response, 'Не удалось открыть файл')
  return response.blob()
}

export async function downloadPatientFile(file: Pick<PatientFileItem, 'id' | 'originalName'>) {
  const response = await apiFetch(`/patient-files/${file.id}/content`)
  if (!response.ok) throw await failure(response, 'Не удалось скачать файл')
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.originalName
  document.body.appendChild(link)
  link.click()
  link.remove()
  // Ссылка живёт секунду — ровно чтобы браузер начал сохранение.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Только строки файлов: ответ без имени файла — не файл. */
function fileRows(rows: unknown): PatientFileItem[] {
  return Array.isArray(rows) ? rows.filter((row): row is PatientFileItem => typeof row?.originalName === 'string' && typeof row?.id === 'string') : []
}

/**
 * Печать: файл открывается в новой вкладке встроенным просмотрщиком PDF (сессия та же, права
 * проверяет сервер), затем вызывается печать. Вызывать прямо из обработчика нажатия — иначе
 * браузер заблокирует новую вкладку. false — вкладку открыть не удалось.
 */
export function printPatientFile(file: Pick<PatientFileItem, 'id'>) {
  const tab = window.open(`${API}/patient-files/${file.id}/inline`, '_blank')
  if (!tab) return false
  tab.addEventListener('load', () => {
    try {
      tab.focus()
      tab.print()
    } catch {
      // Просмотрщик не дал вызвать печать — в нём есть своя кнопка печати.
    }
  })
  return true
}

/** «2,4 МБ», «350 КБ». */
export function fileSize(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} МБ`
  return `${Math.max(1, Math.round(bytes / 1024))} КБ`
}

/** Проверка до загрузки — те же правила, что на сервере. null — можно загружать. */
export async function pdfProblem(file: File) {
  if (file.size > MAX_PDF_BYTES) return 'Файл больше 10 МБ'
  const head = new Uint8Array(await readHead(file, 5))
  return String.fromCharCode(...head) === '%PDF-' ? null : 'Можно загрузить только PDF'
}

function readHead(file: File, length: number) {
  return new Promise<ArrayBuffer>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(file.slice(0, length))
  })
}
