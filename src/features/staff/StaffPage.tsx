import { useEffect, useState } from 'react'
import { AddRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, Typography } from '@mui/material'
import { createStaff, loadStaff, resetStaffPassword, setStaffActive } from '../../api/staff'
import type { Practitioner, StaffItem } from '../../api/types'
import { sandboxStaff as initialSandboxStaff } from '../../app/demoData'
import { roleLabels } from '../../shared/roles'
import { useSandboxState } from '../../shared/sandbox'
import { PasswordDialog } from '../auth/PasswordDialog'
import { StaffDialog } from './StaffDialog'
import { canDeactivate, staffProblem, type StaffDraft } from './staffRules'

type StaffPageProps = {
  self: StaffItem
  live: boolean
  practitioners: Practitioner[]
  notify: (message: string) => void
}

/** Учётные записи сотрудников. Только администратор; хеши паролей сюда не приходят. */
export function StaffPage({ self, live, practitioners, notify }: StaffPageProps) {
  const [sandboxStaff, setSandboxStaff] = useSandboxState<StaffItem[]>('staff', initialSandboxStaff, !live)
  const [remote, setRemote] = useState<StaffItem[] | null>(null)
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState('')
  const [resetting, setResetting] = useState<StaffItem | null>(null)
  const [resetError, setResetError] = useState('')
  const [revision, setRevision] = useState(0)
  // Временный пароль показывается один раз — сразу после того, как администратор его ввёл.
  const [issued, setIssued] = useState<{ username: string; password: string } | null>(null)
  const items = live ? remote ?? [] : sandboxStaff

  useEffect(() => {
    if (!live) return
    let cancelled = false
    loadStaff()
      .then((rows) => { if (!cancelled) { setRemote(rows); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [live, revision])

  const create = async (draft: StaffDraft) => {
    const problem = staffProblem(draft, items, practitioners.map((item) => item.id))
    if (problem) {
      setCreateError(problem)
      return
    }
    if (live) {
      try {
        await createStaff(draft)
      } catch (reason) {
        setCreateError((reason as Error).message)
        return
      }
      setRevision((value) => value + 1)
      notify('Сотрудник создан')
      setIssued({ username: draft.username.toLowerCase(), password: draft.password })
    } else {
      const id = draft.role === 'DOCTOR' && draft.practitionerId ? draft.practitionerId : crypto.randomUUID()
      setSandboxStaff((rows) => [...rows, { id, fullName: draft.fullName, username: draft.username.toLowerCase(), role: draft.role, practitionerId: draft.practitionerId, active: true, mustChangePassword: true }])
      notify('Демо-режим: сотрудник виден только до обновления страницы, войти им нельзя')
    }
    setCreating(false)
  }

  const toggle = async (target: StaffItem) => {
    if (live) {
      try {
        await setStaffActive(target.id, !target.active)
        setRevision((value) => value + 1)
        notify(target.active ? 'Сотрудник отключён' : 'Сотрудник включён')
      } catch (reason) {
        notify((reason as Error).message)
      }
      return
    }
    setSandboxStaff((rows) => rows.map((row) => row.id === target.id ? { ...row, active: !row.active } : row))
    notify(target.active ? 'Демо-режим: сотрудник отключён' : 'Демо-режим: сотрудник включён')
  }

  const reset = async (newPassword: string) => {
    if (!resetting) return
    if (live) {
      try {
        await resetStaffPassword(resetting.id, newPassword)
      } catch (reason) {
        setResetError((reason as Error).message)
        return
      }
      notify('Временный пароль задан')
      setIssued({ username: resetting.username, password: newPassword })
      setRevision((value) => value + 1)
    } else {
      notify('Демо-режим: пароли в песочнице не хранятся')
    }
    setResetting(null)
  }

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Сотрудники</Typography><Typography sx={{ color: 'text.secondary' }}>Учётные записи для входа. Роль задаётся при создании и дальше не меняется.</Typography></Box>
        <Button variant="contained" startIcon={<AddRounded />} onClick={() => { setCreateError(''); setCreating(true) }}>Новый сотрудник</Button>
      </Box>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {issued && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setIssued(null)}>
        Временный пароль для <b>{issued.username}</b>: <b>{issued.password}</b>. Он показан один раз и хранится только хешем; при первом входе сотрудник задаст свой.
      </Alert>}
      <Box className="panel">
        {items.map((item) => (
          <Box className="history-item" key={item.id}>
            <Box>
              <Typography sx={{ fontWeight: 650 }}>{item.fullName}{item.id === self.id ? ' (вы)' : ''}</Typography>
              <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{item.username} · {roleLabels[item.role]}</Typography>
            </Box>
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
              <Chip size="small" variant="outlined" label={item.active ? 'Активен' : 'Отключён'} color={item.active ? 'success' : 'default'} />
              {item.mustChangePassword && <Chip size="small" variant="outlined" color="warning" label="Сменит пароль при входе" />}
              <Button size="small" aria-label={`Задать пароль ${item.fullName}`} onClick={() => { setResetError(''); setResetting(item) }}>Задать пароль</Button>
              {item.active
                ? <Button size="small" color="warning" disabled={!canDeactivate(item, self.id)} aria-label={`Отключить ${item.fullName}`} onClick={() => void toggle(item)}>Отключить</Button>
                : <Button size="small" aria-label={`Включить ${item.fullName}`} onClick={() => void toggle(item)}>Включить</Button>}
            </Box>
          </Box>
        ))}
      </Box>
      {creating && <StaffDialog practitioners={practitioners} error={createError} onClose={() => setCreating(false)} onSave={(draft) => void create(draft)} />}
      {resetting && <PasswordDialog requireCurrent={false} title={`Временный пароль: ${resetting.fullName}`} error={resetError} onClose={() => setResetting(null)} onSave={(input) => void reset(input.newPassword)} />}
    </>
  )
}
