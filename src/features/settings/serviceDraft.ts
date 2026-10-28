import type { ServiceItem } from '../../api/types'
import type { NewService } from '../../app/useClinicData'
import { rublesToMinor } from './shifts'

/** mode: FIXED — цена price; OPEN — свободная цена с пределами min–max (рубли). */
export type ServiceDraft = { name: string; minutes: string; price: string; mode?: 'FIXED' | 'OPEN'; min?: string; max?: string }

const RUBLES = 'Введите цену в рублях, например 4500 или 4500,50'

/** Пределы свободной цены в копейках или текст ошибки. */
export function parseOpenLimits(min: string, max: string): { minMinor: number; maxMinor: number } | { error: string } {
  const minMinor = rublesToMinor(min)
  const maxMinor = rublesToMinor(max)
  if (minMinor === null || maxMinor === null) return { error: 'Укажите пределы свободной цены в рублях: от и до' }
  if (maxMinor < minMinor) return { error: 'Верхний предел не может быть меньше нижнего' }
  return { minMinor, maxMinor }
}

/** Новая услуга: название, длительность в минутах (1–720) и цена в рублях; валюта — RUB. */
export function parseServiceDraft(draft: ServiceDraft, existing: ServiceItem[]): { value: NewService } | { error: string } {
  const name = draft.name.trim()
  if (!name) return { error: 'Укажите название услуги' }
  if (name.length > 255) return { error: 'Название не длиннее 255 символов' }
  if (existing.some((item) => item.name.trim().toLowerCase() === name.toLowerCase())) return { error: 'Услуга с таким названием уже есть' }
  const minutes = Number(draft.minutes)
  if (!/^\d+$/.test(draft.minutes.trim()) || minutes < 1 || minutes > 720) return { error: 'Длительность — целое число минут от 1 до 720' }
  if (draft.mode === 'OPEN') {
    const limits = parseOpenLimits(draft.min ?? '', draft.max ?? '')
    if ('error' in limits) return limits
    return { value: { name, durationMinutes: minutes, priceMode: 'OPEN', ...limits } }
  }
  const priceMinor = rublesToMinor(draft.price)
  if (priceMinor === null) return { error: RUBLES }
  return { value: { name, durationMinutes: minutes, priceMinor } }
}
