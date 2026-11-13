import { useEffect, useState } from 'react'
import { DeleteOutlineRounded } from '@mui/icons-material'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { addShare, linkAddress, loadPrincipals, loadShares, removeShare } from '../../api/drive'
import type { DriveNode, DrivePermission, DrivePrincipal, DriveShare } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { roleLabels, staffRoles } from '../../shared/roles'
import { expiryIn, permissionLabels, principalLabels } from './drive'

type ShareDialogProps = { node: DriveNode; onClose: () => void; notify: (message: string) => void }

/**
 * Доступ к папке или файлу: сотруднику, роли, всей клинике или по ссылке. Ссылка — только на
 * просмотр, со сроком до 30 дней и только для сотрудников клиники после входа; токен показывается
 * один раз, в базе хранится его хеш.
 */
export function ShareDialog({ node, onClose, notify }: ShareDialogProps) {
  const [shares, setShares] = useState<DriveShare[]>([])
  const [principals, setPrincipals] = useState<DrivePrincipal[]>([])
  const [type, setType] = useState<DriveShare['principalType']>('USER')
  const [principal, setPrincipal] = useState('')
  const [permission, setPermission] = useState<DrivePermission>('VIEW')
  const [days, setDays] = useState('7')
  const [issued, setIssued] = useState('')
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    Promise.all([loadShares(node.id), loadPrincipals()])
      .then(([rows, people]) => { setShares(rows); setPrincipals(people) })
      .catch((reason: Error) => setError(reason.message))
  }, [node.id, revision])

  const grant = async () => {
    if ((type === 'USER' || type === 'ROLE') && !principal) {
      setError(type === 'USER' ? 'Выберите сотрудника' : 'Выберите роль')
      return
    }
    const period = Number(days)
    if (type === 'LINK' && (!Number.isInteger(period) || period < 1 || period > 30)) {
      setError('Срок ссылки — от 1 до 30 дней')
      return
    }
    try {
      const share = await addShare(node.id, {
        principalType: type,
        principalId: type === 'USER' || type === 'ROLE' ? principal : null,
        permission: type === 'LINK' ? 'VIEW' : permission,
        expiresAt: type === 'LINK' ? expiryIn(period) : null,
      })
      if (share.token) setIssued(linkAddress(share.token))
      setError('')
      setPrincipal('')
      notify('Доступ выдан')
      setRevision((value) => value + 1)
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  const revoke = async (share: DriveShare) => {
    try {
      await removeShare(node.id, share.id)
      notify('Доступ отозван')
      setRevision((value) => value + 1)
    } catch (reason) {
      setError((reason as Error).message)
    }
  }

  const label = (share: DriveShare) => share.principalType === 'ROLE' ? roleLabels[share.principalId as keyof typeof roleLabels] ?? share.principalId : share.principalType === 'CLINIC' ? 'Все сотрудники клиники' : share.principalType === 'LINK' ? 'Ссылка' : share.label ?? share.principalId

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Доступ: {node.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {issued && <Alert severity="success">Ссылка (показывается один раз, работает после входа в AlphaCRM): <Box component="code" sx={{ wordBreak: 'break-all' }}>{issued}</Box></Alert>}
          {shares.length === 0
            ? <Typography sx={{ color: 'text.secondary' }}>Доступ есть только у владельца{node.kind === 'FOLDER' ? ' и у тех, кому выдана папка выше' : ''}.</Typography>
            : shares.map((share) => (
              <Stack key={share.id} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                <Typography sx={{ flex: 1 }}>{principalLabels[share.principalType]}: {label(share)} · {permissionLabels[share.permission]}{share.expiresAt ? ` · до ${formatClinicMoment(share.expiresAt)}` : ''}</Typography>
                <IconButton aria-label={`Отозвать доступ ${label(share)}`} onClick={() => void revoke(share)}><DeleteOutlineRounded /></IconButton>
              </Stack>
            ))}
          <Typography className="panel-label">ВЫДАТЬ ДОСТУП</Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
            <TextField select size="small" label="Кому" value={type} onChange={(event) => { setType(event.target.value as DriveShare['principalType']); setPrincipal('') }} sx={{ minWidth: 160 }}>
              {Object.entries(principalLabels).map(([value, text]) => <MenuItem key={value} value={value}>{text}</MenuItem>)}
            </TextField>
            {type === 'USER' && <TextField select size="small" label="Сотрудник" value={principal} onChange={(event) => setPrincipal(event.target.value)} sx={{ minWidth: 220 }}>
              {principals.filter((person) => person.id !== node.ownerId).map((person) => <MenuItem key={person.id} value={person.id}>{person.fullName} · {roleLabels[person.role] ?? person.role}</MenuItem>)}
            </TextField>}
            {type === 'ROLE' && <TextField select size="small" label="Роль" value={principal} onChange={(event) => setPrincipal(event.target.value)} sx={{ minWidth: 220 }}>
              {staffRoles.map((role) => <MenuItem key={role} value={role}>{roleLabels[role]}</MenuItem>)}
            </TextField>}
            {type === 'LINK'
              ? <TextField size="small" label="Срок, дней (до 30)" value={days} onChange={(event) => setDays(event.target.value)} sx={{ width: 160 }} />
              : <TextField select size="small" label="Право" value={permission} onChange={(event) => setPermission(event.target.value as DrivePermission)} sx={{ minWidth: 160 }}>
                {(Object.keys(permissionLabels) as DrivePermission[]).map((value) => <MenuItem key={value} value={value}>{permissionLabels[value]}</MenuItem>)}
              </TextField>}
          </Stack>
          {type === 'LINK' && <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Ссылка даёт только просмотр и работает лишь для сотрудников вашей клиники после входа.</Typography>}
          <Box><Button variant="contained" onClick={() => void grant()}>Выдать</Button></Box>
        </Stack>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Готово</Button></DialogActions>
    </Dialog>
  )
}
