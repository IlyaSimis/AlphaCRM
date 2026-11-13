import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'

type NameDialogProps = { title: string; initial: string; onClose: () => void; onSave: (name: string) => Promise<string | null> }

/** Имя папки или файла: без пути, не длиннее 255 символов. */
export function NameDialog({ title, initial, onClose, onSave }: NameDialogProps) {
  const [name, setName] = useState(initial)
  const [error, setError] = useState('')

  const save = async () => {
    const value = name.trim()
    if (!value) {
      setError('Введите название')
      return
    }
    if (/[\\/]/.test(value)) {
      setError('Название без символов / и \\')
      return
    }
    setError((await onSave(value)) ?? '')
  }

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <TextField autoFocus fullWidth label="Название" value={name} onChange={(event) => setName(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); void save() } }} sx={{ mt: 1 }} />
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" onClick={() => void save()}>Сохранить</Button></DialogActions>
    </Dialog>
  )
}
