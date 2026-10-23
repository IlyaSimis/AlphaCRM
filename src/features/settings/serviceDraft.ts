import type { ServiceItem } from '../../api/types'
import { rublesToMinor } from './shifts'

export type ServiceDraft = { name: string; minutes: string; price: string }

/** Новая услуга: название, длительность в минутах (1–720) и цена в рублях; валюта — RUB. */
export function parseServiceDraft(draft: ServiceDraft, existing: ServiceItem[]): { value: { name: string; durationMinutes: number; priceMinor: number } } | { error: string } {
  const name = draft.name.trim()
  if (!name) return { error: 'Укажите название услуги' }
  if (name.length > 255) return { error: 'Название не длиннее 255 символов' }
  if (existing.some((item) => item.name.trim().toLowerCase() === name.toLowerCase())) return { error: 'Услуга с таким названием уже есть' }
  const minutes = Number(draft.minutes)
  if (!/^\d+$/.test(draft.minutes.trim()) || minutes < 1 || minutes > 720) return { error: 'Длительность — целое число минут от 1 до 720' }
  const priceMinor = rublesToMinor(draft.price)
  if (priceMinor === null) return { error: 'Введите цену в рублях, например 4500 или 4500,50' }
  return { value: { name, durationMinutes: minutes, priceMinor } }
}
