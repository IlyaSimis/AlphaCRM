import { useEffect, useState } from 'react'
import { PaymentsRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Stack, TextField, Typography } from '@mui/material'
import { createCharge, loadCharges, payCharge, setChargeAmount } from '../../api/billing'
import type { ApiError } from '../../api/client'
import { loadAppointments } from '../../api/schedule'
import type { ChargeItem, Practitioner, ScheduleItem } from '../../api/types'
import { localToday, shiftDate } from '../../shared/dates'
import { can } from '../../shared/permissions'
import { canTakePayments, type Role } from '../../shared/roles'
import { Metric } from '../../shared/ui'
import { formatMoney, rublesFromInput } from '../schedule/booking'
import { buildCashierRows, stateLabels, totals, type CashierRow } from './cashier'
import { CloseChargeDialog } from './CloseChargeDialog'
import { refundReasonLabels, voidReasonLabels } from '../finance/finance'
import type { RefundReason, VoidReason } from '../../api/types'

type CashierProps = {
  role: Role
  apiOnline: boolean
  practitioners: Practitioner[]
  /** Демо-режим: завершённые приёмы и счета из памяти интерфейса. */
  localAppointments: ScheduleItem[]
  localCharges: ChargeItem[]
  onLocalCharge: (appointment: ScheduleItem) => void
  onLocalPay: (chargeId: string) => void
  /** Демо-режим: сумма свободной цены. */
  onLocalAmount?: (chargeId: string, amountMinor: number) => void
  notify: (message: string) => void
}

const PERIOD_DAYS = 90

/** Пределы свободной цены (и оценка, если есть) для строки без суммы. */
function openRange(row: CashierRow) {
  const charge = row.charge
  const currency = charge?.currency ?? row.appointment.currency ?? 'RUB'
  const estimate = charge?.estimateMinor ?? row.appointment.estimateMinor
  const range = charge?.minMinor != null && charge?.maxMinor != null ? `${formatMoney(charge.minMinor, currency)} – ${formatMoney(charge.maxMinor, currency)}` : 'свободная цена'
  return estimate != null ? `${range} (оценка ${formatMoney(estimate, currency)})` : range
}

function shortDay(iso: string) {
  const [year, month, day] = iso.split('-')
  return `${day}.${month}.${year}`
}

/** Касса: только деньги — пациент, врач, услуга, сумма и статус счёта. Без диагнозов и текста приёма. */
export function Cashier({ role, apiOnline, practitioners, localAppointments, localCharges, onLocalCharge, onLocalPay, onLocalAmount, notify }: CashierProps) {
  const [remote, setRemote] = useState<{ appointments: ScheduleItem[]; charges: ChargeItem[] } | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  /** Введённые суммы свободной цены по счёту, в рублях. */
  const [amounts, setAmounts] = useState<Record<string, string>>({})
  const payments = canTakePayments(role)
  const amountsAllowed = can(role, 'finance.invoice.set-amount')
  const mayVoid = can(role, 'finance.invoice.void')
  const mayRefund = can(role, 'finance.payment.refund')
  /** Открытое окно аннулирования или возврата. */
  const [closing, setClosing] = useState<{ mode: 'void' | 'refund'; row: CashierRow } | null>(null)
  const today = localToday()

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    Promise.all([loadAppointments({ from: shiftDate(today, -PERIOD_DAYS), to: today, status: 'COMPLETED' }), loadCharges()])
      .then(([appointments, charges]) => { if (!cancelled) { setRemote({ appointments, charges }); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline, today, revision])

  const source = apiOnline ? remote : { appointments: localAppointments.filter((item) => item.statusCode === 'COMPLETED'), charges: localCharges }
  const rows = source ? buildCashierRows(source.appointments, source.charges) : []
  const sums = totals(rows)
  const doctorName = (id: string) => practitioners.find((doctor) => doctor.id === id)?.fullName ?? 'Врач'

  const act = async (key: string, action: () => Promise<void>, message: string | (() => string)) => {
    setBusy(key)
    try {
      await action()
      notify(typeof message === 'function' ? message() : message)
      setRevision((value) => value + 1)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(null)
    }
  }

  const issue = (row: CashierRow) => {
    if (!apiOnline) {
      onLocalCharge(row.appointment)
      notify('Демо-режим: счёт виден до обновления страницы')
      return
    }
    const { appointment } = row
    if (appointment.amountMinor == null || !appointment.id) return
    const appointmentId = appointment.id
    const amountMinor = appointment.amountMinor
    void act(appointmentId, async () => { await createCharge({ appointmentId, amountMinor, currency: appointment.currency ?? 'RUB' }) }, 'Счёт выставлен')
  }

  /** Все недостающие счета разом: по одному запросу на приём, уже выставленные пропускаются. */
  const issueAll = async () => {
    const missing = rows.filter((row) => row.state === 'NO_CHARGE' && row.appointment.amountMinor != null && row.appointment.id)
    if (missing.length === 0) return
    if (!apiOnline) {
      missing.forEach((row) => onLocalCharge(row.appointment))
      notify(`Демо-режим: выставлено счетов — ${missing.length}, видны до обновления страницы`)
      return
    }
    setBusy('all')
    let issued = 0
    try {
      for (const row of missing) {
        try {
          await createCharge({ appointmentId: row.appointment.id as string, amountMinor: row.appointment.amountMinor as number, currency: row.appointment.currency ?? 'RUB' })
          issued++
        } catch (reason) {
          if ((reason as ApiError).code !== 'CHARGE_ALREADY_EXISTS') throw reason
        }
      }
      notify(`Выставлено счетов: ${issued}`)
    } catch (reason) {
      setError(`${(reason as Error).message}. Выставлено до ошибки: ${issued}`)
    } finally {
      setBusy(null)
      setRevision((value) => value + 1)
    }
  }

  /** Свободная цена: сумма в пределах услуги, после неё счёт можно оплатить. */
  const saveAmount = (row: CashierRow) => {
    if (!row.charge) return
    const chargeId = row.charge.chargeId
    const minor = rublesFromInput(amounts[chargeId] ?? '')
    if (minor === null) {
      setError('Введите сумму в рублях, например 4500 или 4500,50')
      return
    }
    const { minMinor, maxMinor } = row.charge
    if ((minMinor != null && minor < minMinor) || (maxMinor != null && minor > maxMinor)) {
      setError('Сумма вне пределов свободной цены услуги')
      return
    }
    setError('')
    if (!apiOnline) {
      onLocalAmount?.(chargeId, minor)
      notify('Демо-режим: сумма видна до обновления страницы')
      return
    }
    void act(chargeId, async () => { await setChargeAmount(chargeId, minor) }, 'Сумма сохранена')
  }

  const pay = (row: CashierRow) => {
    if (!row.charge) return
    const chargeId = row.charge.chargeId
    if (!apiOnline) {
      onLocalPay(chargeId)
      notify('Демо-режим: оплата видна до обновления страницы')
      return
    }
    // Платёжный шлюз не подключён (MOCK_NO_PROVIDER): оплата только отмечена — не выдаём её за настоящий платёж.
    let message = 'Оплата принята'
    void act(chargeId, async () => {
      const view = await payCharge(chargeId, `pay-${chargeId}`)
      if (view?.paymentStatus === 'MOCK_NO_PROVIDER') message = 'Оплата отмечена в кассе. Платёжный шлюз не подключён: деньги через систему не проводились, чек ждёт подключения кассы'
    }, () => message)
  }

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Касса</Typography><Typography sx={{ color: 'text.secondary' }}>Завершённые приёмы за {PERIOD_DAYS} дней: к оплате и оплаченные</Typography></Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Chip size="small" label={apiOnline ? 'Данные API' : 'Демо-режим'} color={apiOnline ? 'success' : 'default'} variant="outlined" />
          {payments && sums.withoutCharge > 0 && <Button variant="contained" disabled={busy !== null} onClick={() => void issueAll()}>Выставить все счета ({sums.withoutCharge})</Button>}
          <Button variant="outlined" startIcon={<RefreshRounded />} disabled={!apiOnline} onClick={() => setRevision((value) => value + 1)}>Обновить</Button>
        </Stack>
      </Box>
      <Box className="summary-grid" sx={{ mb: 2 }}>
        <Metric value={formatMoney(sums.pending)} label="ожидает оплаты" note="выставленные счета" accent={sums.pending > 0} />
        <Metric value={formatMoney(sums.paid)} label="оплачено" note={sums.refunded > 0 ? `за период; возвращено ${formatMoney(sums.refunded)}` : 'за период'} />
        <Metric value={String(sums.withoutCharge)} label="без счёта" note={payments ? 'можно выставить' : 'выставляют касса и администратор'} />
        {sums.awaitingAmount > 0 && <Metric value={String(sums.awaitingAmount)} label="ждут суммы" note="свободная цена" accent />}
      </Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box className="panel audit-panel">
        {apiOnline && !remote && !error ? <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress aria-label="Загрузка кассы" /></Box> : rows.length === 0 ? (
          <Box className="empty" sx={{ minHeight: 160 }}><PaymentsRounded /><Typography sx={{ fontWeight: 700 }}>Завершённых приёмов нет</Typography></Box>
        ) : (
          <table className="audit-table">
            <thead><tr><th>Дата</th><th>Пациент</th><th>Врач</th><th>Услуга</th><th>Сумма</th><th>Статус счёта</th><th /></tr></thead>
            <tbody>
              {rows.map((row) => {
                const amount = row.charge?.amountMinor ?? row.appointment.amountMinor
                return (
                  <tr key={row.appointment.id}>
                    <td className="nowrap">{shortDay(row.appointment.date)} {row.appointment.time}</td>
                    <td>{row.appointment.name}</td>
                    <td>{doctorName(row.appointment.practitionerId)}</td>
                    <td>{row.appointment.type}</td>
                    <td className="nowrap">{amount == null ? (row.charge?.priceMode === 'OPEN' || row.appointment.priceMode === 'OPEN' ? openRange(row) : '—') : formatMoney(amount, row.charge?.currency ?? row.appointment.currency ?? 'RUB')}</td>
                    <td>
                      <Chip size="small" variant="outlined" label={stateLabels[row.state]} color={row.state === 'PAID' ? 'success' : row.state === 'PENDING' || row.state === 'AWAITING_AMOUNT' ? 'warning' : row.state === 'REFUNDED' ? 'error' : 'default'} />
                      {row.state === 'VOIDED' && row.charge?.voidReason && <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 0.5 }}>{voidReasonLabels[row.charge.voidReason as VoidReason] ?? row.charge.voidReason}</Typography>}
                      {row.state === 'REFUNDED' && row.charge?.refundReason && <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 0.5 }}>{refundReasonLabels[row.charge.refundReason as RefundReason] ?? row.charge.refundReason}</Typography>}
                    </td>
                    <td className="nowrap">
                      {row.state === 'AWAITING_AMOUNT' && amountsAllowed && row.charge && <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        <TextField size="small" label="Сумма, ₽" value={amounts[row.charge.chargeId] ?? ''} onChange={(event) => setAmounts((current) => ({ ...current, [row.charge?.chargeId ?? '']: event.target.value }))} slotProps={{ htmlInput: { 'aria-label': `Сумма ${row.appointment.name} ${row.appointment.time}, рублей` } }} sx={{ width: 120 }} />
                        <Button size="small" variant="contained" disabled={busy !== null} aria-label={`Сохранить сумму ${row.appointment.name} ${row.appointment.time}`} onClick={() => saveAmount(row)}>Сохранить</Button>
                      </Stack>}
                      {row.state === 'NO_CHARGE' && payments && <Button size="small" disabled={busy !== null || row.appointment.amountMinor == null} aria-label={`Выставить счёт ${row.appointment.name} ${row.appointment.time}`} onClick={() => issue(row)}>Выставить счёт</Button>}
                      {row.state === 'PENDING' && payments && <Button size="small" variant="contained" disabled={busy !== null} aria-label={`Принять оплату ${row.appointment.name} ${row.appointment.time}`} onClick={() => pay(row)}>Принять оплату</Button>}
                      {apiOnline && mayVoid && (row.state === 'PENDING' || row.state === 'AWAITING_AMOUNT') && <Button size="small" color="warning" disabled={busy !== null} aria-label={`Аннулировать счёт ${row.appointment.name} ${row.appointment.time}`} onClick={() => setClosing({ mode: 'void', row })}>Аннулировать</Button>}
                      {apiOnline && mayRefund && row.state === 'PAID' && row.charge?.paymentId && <Button size="small" color="error" disabled={busy !== null} aria-label={`Возврат ${row.appointment.name} ${row.appointment.time}`} onClick={() => setClosing({ mode: 'refund', row })}>Возврат</Button>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </Box>
      {closing?.row.charge && <CloseChargeDialog mode={closing.mode} charge={closing.row.charge} subject={`${closing.row.appointment.name}, ${shortDay(closing.row.appointment.date)} ${closing.row.appointment.time}, ${closing.row.appointment.type}`} onClose={() => setClosing(null)} onDone={(message) => { setClosing(null); notify(message); setRevision((value) => value + 1) }} />}
    </>
  )
}
