import { useEffect, useState } from 'react'
import { PaymentsRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Stack, Typography } from '@mui/material'
import { createCharge, loadCharges, payCharge } from '../../api/billing'
import { loadAppointments } from '../../api/schedule'
import type { ChargeItem, Practitioner, ScheduleItem } from '../../api/types'
import { localToday, shiftDate } from '../../shared/dates'
import type { Role } from '../../shared/roles'
import { Metric } from '../../shared/ui'
import { formatMoney } from '../schedule/booking'
import { buildCashierRows, stateLabels, totals, type CashierRow } from './cashier'

type CashierProps = {
  role: Role
  apiOnline: boolean
  practitioners: Practitioner[]
  /** Демо-режим: завершённые приёмы и счета из памяти интерфейса. */
  localAppointments: ScheduleItem[]
  localCharges: ChargeItem[]
  onLocalCharge: (appointment: ScheduleItem) => void
  onLocalPay: (chargeId: string) => void
  notify: (message: string) => void
}

const PERIOD_DAYS = 90

function shortDay(iso: string) {
  const [year, month, day] = iso.split('-')
  return `${day}.${month}.${year}`
}

/** Касса: только деньги — пациент, врач, услуга, сумма и статус счёта. Без диагнозов и текста приёма. */
export function Cashier({ role, apiOnline, practitioners, localAppointments, localCharges, onLocalCharge, onLocalPay, notify }: CashierProps) {
  const [remote, setRemote] = useState<{ appointments: ScheduleItem[]; charges: ChargeItem[] } | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
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
          <Button variant="outlined" startIcon={<RefreshRounded />} disabled={!apiOnline} onClick={() => setRevision((value) => value + 1)}>Обновить</Button>
        </Stack>
      </Box>
      <Box className="summary-grid" sx={{ mb: 2 }}>
        <Metric value={formatMoney(sums.pending)} label="ожидает оплаты" note="выставленные счета" accent={sums.pending > 0} />
        <Metric value={formatMoney(sums.paid)} label="оплачено" note="за период" />
        <Metric value={String(sums.withoutCharge)} label="без счёта" note={role === 'ADMINISTRATOR' ? 'можно выставить' : 'выставляет администратор'} />
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
                    <td className="nowrap">{amount == null ? '—' : formatMoney(amount, row.charge?.currency ?? row.appointment.currency ?? 'RUB')}</td>
                    <td><Chip size="small" variant="outlined" label={stateLabels[row.state]} color={row.state === 'PAID' ? 'success' : row.state === 'PENDING' ? 'warning' : 'default'} /></td>
                    <td className="nowrap">
                      {row.state === 'NO_CHARGE' && role === 'ADMINISTRATOR' && <Button size="small" disabled={busy !== null || row.appointment.amountMinor == null} aria-label={`Выставить счёт ${row.appointment.name} ${row.appointment.time}`} onClick={() => issue(row)}>Выставить счёт</Button>}
                      {row.state === 'PENDING' && role === 'CASHIER' && <Button size="small" variant="contained" disabled={busy !== null} aria-label={`Принять оплату ${row.appointment.name} ${row.appointment.time}`} onClick={() => pay(row)}>Принять оплату</Button>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </Box>
    </>
  )
}
