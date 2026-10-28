import { useEffect, useState } from 'react'
import { Alert, Box, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { loadContracts, loadMyContracts, saveContract, type HrContract, type RateUnit } from '../../api/hr'
import { loadStaff } from '../../api/staff'
import type { StaffItem } from '../../api/types'
import { localToday } from '../../shared/dates'
import { roleText, staffRolesOf } from '../../shared/roles'
import { rublesFromInput } from '../schedule/booking'
import { periodText, rateText, rateUnitLabels } from './hr'

/** Свои условия работы: должность, ставка и их история. Видит только сам сотрудник (и владелец). */
export function MyContractPage({ apiOnline }: { apiOnline: boolean }) {
  const [rows, setRows] = useState<HrContract[] | null>(null)
  const [error, setError] = useState('')
  useEffect(() => {
    if (!apiOnline) return
    loadMyContracts().then(setRows).catch((reason: Error) => setError(reason.message))
  }, [apiOnline])
  if (!apiOnline) return <Alert severity="info">Условия работы хранятся на сервере: в демо-песочнице их нет.</Alert>
  if (error) return <Alert severity="error">{error}</Alert>
  if (!rows) return <Box className="empty" sx={{ minHeight: 120 }}><CircularProgress aria-label="Загрузка условий" /></Box>
  if (rows.length === 0) return <Alert severity="info">Условия работы ещё не внесены — их вносит владелец клиники.</Alert>
  return (
    <Box className="panel">
      {rows.map((row) => (
        <Box className="history-item" key={row.contractId}>
          <Box><Typography sx={{ fontWeight: 650 }}>{row.position}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{periodText(row)}{row.validTo ? '' : ' · действуют сейчас'}</Typography></Box>
          <Typography sx={{ fontWeight: 700 }}>{rateText(row)}</Typography>
        </Box>
      ))}
    </Box>
  )
}

/** Ставки всех сотрудников — только владелец (раздел C, hr). Новые условия закрывают прежние. */
export function ContractsPage({ apiOnline, notify }: { apiOnline: boolean; notify: (message: string) => void }) {
  const [staff, setStaff] = useState<StaffItem[] | null>(null)
  const [contracts, setContracts] = useState<HrContract[]>([])
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<StaffItem | null>(null)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!apiOnline) return
    Promise.all([loadStaff(), loadContracts()])
      .then(([people, terms]) => { setStaff(people); setContracts(terms); setError('') })
      .catch((reason: Error) => setError(reason.message))
  }, [apiOnline, revision])

  if (!apiOnline) return <Alert severity="info">Ставки хранятся на сервере: в демо-песочнице их нет.</Alert>
  const current = (id: string) => contracts.find((contract) => contract.staffId === id)
  return (
    <>
      <Box className="section-intro"><Box><Typography sx={{ color: 'text.secondary' }}>Должности и ставки видит только владелец; каждый сотрудник видит свои в «Мои условия работы». Суммы в журнал не пишутся.</Typography></Box></Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box className="panel">
        {!staff && !error && <CircularProgress aria-label="Загрузка ставок" />}
        {staff?.map((item) => {
          const contract = current(item.id)
          return (
            <Box className="history-item" key={item.id}>
              <Box>
                <Typography sx={{ fontWeight: 650 }}>{item.fullName}</Typography>
                <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{roleText(staffRolesOf(item))}{contract ? ` · ${contract.position}, ${periodText(contract)}` : ' · условия не внесены'}</Typography>
              </Box>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                {contract && <Typography sx={{ fontWeight: 700 }}>{rateText(contract)}</Typography>}
                <Button size="small" aria-label={`Условия ${item.fullName}`} onClick={() => setEditing(item)}>{contract ? 'Изменить' : 'Внести'}</Button>
              </Stack>
            </Box>
          )
        })}
      </Box>
      {editing && <ContractDialog item={editing} current={current(editing.id)} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); setRevision((value) => value + 1); notify('Условия сохранены') }} />}
    </>
  )
}

function ContractDialog({ item, current, onClose, onSaved }: { item: StaffItem; current?: HrContract; onClose: () => void; onSaved: () => void }) {
  const [position, setPosition] = useState(current?.position ?? '')
  const [rate, setRate] = useState(current ? String(current.rateMinor / 100) : '')
  const [unit, setUnit] = useState<RateUnit>(current?.rateUnit ?? 'MONTH')
  const [from, setFrom] = useState(localToday())
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const save = async () => {
    const minor = rublesFromInput(rate)
    if (!position.trim() || minor === null || !from) {
      setError('Укажите должность, ставку в рублях и дату начала')
      return
    }
    setBusy(true)
    try {
      await saveContract(item.id, { position: position.trim(), rateMinor: minor, rateUnit: unit, validFrom: from })
      onSaved()
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Условия работы: {item.fullName}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField label="Должность" value={position} onChange={(event) => setPosition(event.target.value)} />
          <Stack direction="row" spacing={1.5}>
            <TextField label="Ставка, ₽" value={rate} onChange={(event) => setRate(event.target.value)} slotProps={{ htmlInput: { inputMode: 'decimal' } }} sx={{ flex: 1 }} />
            <TextField select label="За" value={unit} onChange={(event) => setUnit(event.target.value as RateUnit)} sx={{ width: 140 }}>
              {(Object.keys(rateUnitLabels) as RateUnit[]).map((value) => <MenuItem key={value} value={value}>{rateUnitLabels[value]}</MenuItem>)}
            </TextField>
          </Stack>
          <TextField type="date" label="Действуют с" value={from} onChange={(event) => setFrom(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} helperText={current ? 'Прежние условия закроются днём раньше' : undefined} />
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Отмена</Button>
        <Button variant="contained" disabled={busy} onClick={() => void save()}>Сохранить</Button>
      </DialogActions>
    </Dialog>
  )
}
