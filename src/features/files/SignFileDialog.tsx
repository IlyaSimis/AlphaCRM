import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import type { PatientFileItem } from '../../api/types'
import { SIGN_NOTICE } from './fileRules'

type SignFileDialogProps = {
  file: PatientFileItem
  error: string
  busy: boolean
  onClose: () => void
  onSign: () => void
}

export function SignFileDialog({ file, error, busy, onClose, onSign }: SignFileDialogProps) {
  return <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
    <DialogTitle>Подписать файл</DialogTitle>
    <DialogContent>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Typography sx={{ fontWeight: 650, mb: 1, overflowWrap: 'anywhere' }}>{file.originalName}</Typography>
      <Typography>{SIGN_NOTICE}</Typography>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={busy} onClick={onSign}>Подписать</Button>
    </DialogActions>
  </Dialog>
}
