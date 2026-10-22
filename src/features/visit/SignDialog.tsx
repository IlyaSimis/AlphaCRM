import { LockOutlined } from '@mui/icons-material'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'

export function SignDialog({ open, onClose, onConfirm }: { open: boolean; onClose: () => void; onConfirm: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Подписать медицинский документ?</DialogTitle>
      <DialogContent>
        <Alert severity="warning" icon={<LockOutlined />}>После подписания эту версию нельзя изменить или удалить. Исправления оформляются отдельным дополнением.</Alert>
        <Typography sx={{ mt: 2, fontSize: 14, color: 'text.secondary' }}>Будут сохранены автор, точное время, версия шаблона и контрольный хеш документа.</Typography>
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Вернуться</Button><Button variant="contained" onClick={onConfirm}>Подписать и заблокировать</Button></DialogActions>
    </Dialog>
  )
}
