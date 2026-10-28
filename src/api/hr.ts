import { apiFetch, failure } from './client'

export type RateUnit = 'MONTH' | 'HOUR' | 'VISIT'

/** Условия работы сотрудника с даты validFrom; validTo пусто — действуют сейчас. */
export type HrContract = {
  contractId: string
  staffId: string
  fullName: string
  position: string
  rateMinor: number
  rateUnit: RateUnit
  currency: string
  validFrom: string
  validTo: string | null
}

export type HrContractInput = { position: string; rateMinor: number; rateUnit: RateUnit; validFrom: string }

/** Свои условия работы — каждый сотрудник; история, новые сверху. */
export async function loadMyContracts(): Promise<HrContract[]> {
  const response = await apiFetch('/hr/contracts/me')
  if (!response.ok) throw await failure(response, 'Условия работы недоступны')
  return response.json()
}

/** Действующие условия всех сотрудников — только владелец. */
export async function loadContracts(): Promise<HrContract[]> {
  const response = await apiFetch('/hr/contracts')
  if (!response.ok) throw await failure(response, 'Ставки недоступны')
  return response.json()
}

/** Новые условия с даты: прежние закрываются днём раньше — только владелец. */
export async function saveContract(staffId: string, input: HrContractInput): Promise<HrContract> {
  const response = await apiFetch(`/hr/contracts/${staffId}`, { method: 'PUT', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить условия')
  return response.json()
}
