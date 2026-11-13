import { useState } from 'react'
import { Alert, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, Stack, Typography } from '@mui/material'
import type { StaffItem, StaffRole } from '../../api/types'
import { roleLabels, staffRoles, staffRolesOf } from '../../shared/roles'
import { rolesChangeProblem } from './staffRules'

type RolesDialogProps = {
  item: StaffItem
  /** Роли, которые вошедший сотрудник может назначать и снимать. */
  assignable: StaffRole[]
  error: string
  onClose: () => void
  onSave: (roles: StaffRole[]) => void
}

/**
 * Роли сотрудника: несколько ролей — права складываются. Роль врача связана с карточкой врача и
 * здесь не меняется; роли выше своих видны, но не переключаются.
 */
export function RolesDialog({ item, assignable, error, onClose, onSave }: RolesDialogProps) {
  const [selected, setSelected] = useState<StaffRole[]>(() => staffRolesOf(item))
  const problem = rolesChangeProblem(selected)
  const toggle = (role: StaffRole) => setSelected((current) => current.includes(role) ? current.filter((value) => value !== role) : staffRoles.filter((value) => value === role || current.includes(value)))
  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Роли: {item.fullName}</DialogTitle>
      <DialogContent>
        <Typography sx={{ fontSize: 13, color: 'text.secondary', mb: 1 }}>Права ролей складываются. Изменения действуют сразу, без нового входа.</Typography>
        <Stack>
          {staffRoles.map((role) => {
            const doctor = role === 'DOCTOR'
            const locked = doctor || !assignable.includes(role)
            return (
              <FormControlLabel
                key={role}
                control={<Checkbox checked={selected.includes(role)} disabled={locked} onChange={() => toggle(role)} />}
                label={<>{roleLabels[role]}{doctor && <Typography component="span" sx={{ fontSize: 12, color: 'text.secondary' }}> — по карточке врача</Typography>}</>}
              />
            )
          })}
        </Stack>
        {(error || problem) && <Alert severity="error" sx={{ mt: 1 }}>{error || problem}</Alert>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Отмена</Button>
        <Button variant="contained" disabled={Boolean(problem)} onClick={() => onSave(selected)}>Сохранить</Button>
      </DialogActions>
    </Dialog>
  )
}
