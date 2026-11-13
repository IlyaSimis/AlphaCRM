import type { HrContract, RateUnit } from '../../api/hr'
import { formatMoney } from '../schedule/booking'

export const rateUnitLabels: Record<RateUnit, string> = { MONTH: 'в месяц', HOUR: 'в час', VISIT: 'за приём' }

/** «60 000,00 ₽ в месяц». */
export const rateText = (contract: Pick<HrContract, 'rateMinor' | 'rateUnit' | 'currency'>) => `${formatMoney(contract.rateMinor, contract.currency)} ${rateUnitLabels[contract.rateUnit]}`

/** «с 01.11.2026» или «01.01.2026 – 31.10.2026». */
export function periodText(contract: Pick<HrContract, 'validFrom' | 'validTo'>) {
  const day = (iso: string) => iso.split('-').reverse().join('.')
  return contract.validTo ? `${day(contract.validFrom)} – ${day(contract.validTo)}` : `с ${day(contract.validFrom)}`
}
