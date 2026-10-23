import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material'
import { referralSources } from '../registry/registry'

const NOTE_LIMIT = 1000

type RegistryDialogProps = {
  initial: { referralSource: string | null; registryNote: string | null }
  error: string
  onClose: () => void
  onSave: (input: { referralSource: string | null; registryNote: string | null }) => void
}

export function RegistryDialog({ initial, error, onClose, onSave }: RegistryDialogProps) {
  const [source, setSource] = useState(initial.referralSource ?? '')
  const [note, setNote] = useState(initial.registryNote ?? '')
  return <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle>Данные регистратуры</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField select label="Откуда пришёл" value={source} onChange={(event) => setSource(event.target.value)}>
          <MenuItem value="">Не указано</MenuItem>
          {referralSources.map((item) => <MenuItem key={item.value} value={item.value}>{item.label}</MenuItem>)}
        </TextField>
        <TextField label="Заметка регистратуры" multiline minRows={3} value={note} onChange={(event) => setNote(event.target.value.slice(0, NOTE_LIMIT))} helperText={`${note.length}/${NOTE_LIMIT}`} />
        <Alert severity="info">Заметка административная: не попадает в клиническую карту, а в журнал аудита пишется только факт изменения, без текста.</Alert>
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" onClick={() => onSave({ referralSource: source || null, registryNote: note.trim() ? note.trim() : null })}>Сохранить</Button>
    </DialogActions>
  </Dialog>
}
