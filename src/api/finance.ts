import { apiFetch, failure } from './client'
import type { SalesGroup, SalesReport } from './types'

/** Продажи за период (даты включительно) по дням, услугам или врачам: оплаты минус возвраты. */
export async function loadSales(from: string, to: string, groupBy: SalesGroup): Promise<SalesReport> {
  const response = await apiFetch(`/finance/sales?${new URLSearchParams({ from, to, groupBy })}`)
  if (!response.ok) throw await failure(response, 'Отчёт о продажах недоступен')
  return response.json()
}
