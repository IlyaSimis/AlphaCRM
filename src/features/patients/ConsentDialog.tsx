import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material'

export const consentBases = [
  { value: 'SIGNED_FORM', label: 'Подписанная форма' },
  { value: 'ELECTRONIC_SIGNATURE', label: 'Электронная подпись' },
]

/** Новая версия согласия. Текст основания не попадает в аудит — только факт и id. */
export function ConsentDialog({ error, onClose, onSave }: { error: string; onClose: () => void; onSave: (input: { version: string; basis: string }) => void }) {
  const [version, setVersion] = useState('v1.3')
  const [basis, setBasis] = useState(consentBases[0].value)
  return <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
    <DialogTitle>Обновить согласие</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="Версия формы" value={version} onChange={(event) => setVersion(event.target.value)} />
        <TextField select label="Основание" value={basis} onChange={(event) => setBasis(event.target.value)}>
          {consentBases.map((item) => <MenuItem key={item.value} value={item.value}>{item.label}</MenuItem>)}
        </TextField>
        <Alert severity="info">Прежние версии сохраняются в истории согласий.</Alert>
      </Stack>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" disabled={!version.trim()} onClick={() => onSave({ version: version.trim(), basis })}>Сохранить согласие</Button></DialogActions>
  </Dialog>
}
