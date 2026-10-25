import { useState } from 'react'
import { Alert, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, MenuItem, Stack, TextField } from '@mui/material'
import type { RegistryInput } from '../../api/types'
import { referralSources } from '../registry/registry'

const NOTE_LIMIT = 1000
/** Chat id Telegram — число; у личного чата положительное. */
const CHAT_ID = /^-?\d{1,20}$/

type RegistryDialogProps = {
  initial: { referralSource: string | null; registryNote: string | null; telegramConnected: boolean }
  error: string
  onClose: () => void
  onSave: (input: RegistryInput) => void
}

export function RegistryDialog({ initial, error, onClose, onSave }: RegistryDialogProps) {
  const [source, setSource] = useState(initial.referralSource ?? '')
  const [note, setNote] = useState(initial.registryNote ?? '')
  const [chatId, setChatId] = useState('')
  const [removeTelegram, setRemoveTelegram] = useState(false)
  const chatProblem = chatId.trim() && !CHAT_ID.test(chatId.trim()) ? 'Chat id — только цифры, его присылает бот клиники' : ''
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
        <TextField
          label="Telegram chat id"
          value={chatId}
          disabled={removeTelegram}
          onChange={(event) => setChatId(event.target.value)}
          error={Boolean(chatProblem)}
          helperText={chatProblem || (initial.telegramConnected ? 'Подключён. Оставьте пустым, чтобы не менять' : 'Пациент получает его, начав диалог с ботом клиники. Телефона недостаточно')}
          slotProps={{ htmlInput: { inputMode: 'numeric' } }}
        />
        {initial.telegramConnected && <FormControlLabel control={<Checkbox checked={removeTelegram} onChange={(event) => setRemoveTelegram(event.target.checked)} />} label="Отключить Telegram" />}
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={Boolean(chatProblem)} onClick={() => onSave({ referralSource: source || null, registryNote: note.trim() ? note.trim() : null, telegramChatId: removeTelegram ? null : chatId.trim() || null, removeTelegram })}>Сохранить</Button>
    </DialogActions>
  </Dialog>
}
