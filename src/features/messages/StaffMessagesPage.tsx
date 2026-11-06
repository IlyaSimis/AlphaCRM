import { useEffect, useRef, useState } from 'react'
import { AddRounded, GroupAddRounded, PersonAddRounded, SendRounded } from '@mui/icons-material'
import { Alert, Avatar, Badge, Box, Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, List, ListItemButton, ListItemText, Paper, TextField, Typography } from '@mui/material'
import { createDirectConversation, createGroupConversation, sendStaffMessage, type StaffConversation } from '../../api/staffChat'
import type { StaffItem } from '../../api/types'
import { initialsOf } from '../../shared/text'
import { roleLabels } from '../../shared/roles'
import type { StaffChatState } from './useStaffChat'

function messageTime(value: string) {
  return new Date(value).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}

type Props = { self: StaffItem; apiOnline: boolean; chat: StaffChatState }

/** Внутренний чат сотрудников использует общее состояние рабочего места. */
export function StaffMessagesPage({ self, apiOnline, chat }: Props) {
  const {
    conversations, colleagues, selectedId, messages, loading, messagesLoading, connected, error,
    setError, reloadConversations, openConversation, clearConversation, appendMessage,
  } = chat
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [directOpen, setDirectOpen] = useState(false)
  const [groupOpen, setGroupOpen] = useState(false)
  const [groupTitle, setGroupTitle] = useState('')
  const [groupMembers, setGroupMembers] = useState<string[]>([])
  const [creating, setCreating] = useState(false)
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const selected = conversations.find((item) => item.id === selectedId) ?? null

  useEffect(() => {
    if (scrollRef.current) scrollRef.current.scrollTop = scrollRef.current.scrollHeight
  }, [messages, selectedId])

  const submit = async () => {
    if (!selectedId || !draft.trim()) return
    setSending(true)
    setError('')
    try {
      const message = await sendStaffMessage(selectedId, draft)
      appendMessage(message)
      setDraft('')
      await reloadConversations()
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setSending(false)
    }
  }

  const finishCreate = async (conversation: StaffConversation) => {
    setDirectOpen(false)
    setGroupOpen(false)
    setGroupTitle('')
    setGroupMembers([])
    await reloadConversations()
    await openConversation(conversation.id)
  }

  const createDirect = async (staffId: string) => {
    setCreating(true)
    setError('')
    try {
      await finishCreate(await createDirectConversation(staffId))
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setCreating(false)
    }
  }

  const createGroup = async () => {
    setCreating(true)
    setError('')
    try {
      await finishCreate(await createGroupConversation(groupTitle, groupMembers))
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setCreating(false)
    }
  }

  if (!apiOnline) return <Alert severity="info">Внутренний чат доступен после подключения к серверу клиники. Данные песочницы не отправляются сотрудникам.</Alert>
  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', p: 5 }}><CircularProgress aria-label="Загрузка сообщений" /></Box>

  return <Box>
    {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', mb: 2 }}>
      <Button variant="contained" startIcon={<PersonAddRounded />} onClick={() => setDirectOpen(true)} disabled={colleagues.length === 0}>Личный чат</Button>
      <Button variant="outlined" startIcon={<GroupAddRounded />} onClick={() => setGroupOpen(true)} disabled={colleagues.length < 2}>Создать группу</Button>
      <Chip size="small" variant="outlined" color={connected ? 'success' : 'default'} label={connected ? 'На связи' : 'Восстановление соединения'} />
      <Typography sx={{ color: 'text.secondary', fontSize: 13 }}>Только сотрудники вашей клиники</Typography>
    </Box>
    <Paper variant="outlined" sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '320px minmax(0, 1fr)' }, height: { xs: 'auto', md: 'calc(100vh - 275px)' }, minHeight: 480, overflow: 'hidden' }}>
      <Box sx={{ borderRight: { md: 1 }, borderColor: 'divider', overflowY: 'auto', display: { xs: selected ? 'none' : 'block', md: 'block' }, maxHeight: { xs: 360, md: 'none' } }}>
        {conversations.length === 0 ? <Typography sx={{ p: 3, color: 'text.secondary' }}>Бесед пока нет. Начните личный чат или создайте группу.</Typography> : <List disablePadding>
          {conversations.map((conversation) => <ListItemButton key={conversation.id} selected={conversation.id === selectedId} onClick={() => void openConversation(conversation.id)} alignItems="flex-start" sx={{ gap: 1.5, py: 1.5 }}>
            <Badge badgeContent={conversation.unreadCount} color="primary" overlap="circular"><Avatar sx={{ width: 38, height: 38, fontSize: 14 }}>{initialsOf(conversation.title)}</Avatar></Badge>
            <ListItemText primary={conversation.title} secondary={conversation.lastMessage ? `${conversation.lastMessage.own ? 'Вы: ' : ''}${conversation.lastMessage.body}` : 'Сообщений пока нет'} slotProps={{ primary: { noWrap: true, sx: { fontWeight: conversation.unreadCount > 0 ? 750 : 600 } }, secondary: { noWrap: true } }} />
            <Typography sx={{ color: 'text.secondary', fontSize: 11, whiteSpace: 'nowrap' }}>{conversation.lastMessage ? messageTime(conversation.lastMessage.createdAt) : ''}</Typography>
          </ListItemButton>)}
        </List>}
      </Box>
      <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0, minHeight: { xs: 480, md: 0 } }}>
        {selected ? <>
          <Box sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 1 }}>
            <Button size="small" sx={{ display: { md: 'none' } }} onClick={clearConversation}>К чатам</Button>
            <Box sx={{ minWidth: 0 }}><Typography noWrap sx={{ fontWeight: 750 }}>{selected.title}</Typography><Typography noWrap sx={{ color: 'text.secondary', fontSize: 12 }}>{selected.kind === 'GROUP' ? `Участников: ${selected.members.length}` : 'Личная беседа'}</Typography></Box>
          </Box>
          <Box ref={scrollRef} sx={{ flex: 1, overflowY: 'auto', p: 2, bgcolor: 'action.hover', minHeight: 0 }}>
            {messagesLoading ? <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress size={28} /></Box> : messages.length === 0 ? <Typography sx={{ color: 'text.secondary', textAlign: 'center', mt: 4 }}>Напишите первое сообщение</Typography> : messages.map((message) => <Box key={message.id} sx={{ display: 'flex', justifyContent: message.senderId === self.id ? 'flex-end' : 'flex-start', mb: 1.25 }}>
              <Box sx={{ maxWidth: '78%', px: 1.5, py: 1, borderRadius: 2, bgcolor: message.senderId === self.id ? 'primary.main' : 'background.paper', color: message.senderId === self.id ? 'primary.contrastText' : 'text.primary', boxShadow: 1 }}>
                {selected.kind === 'GROUP' && message.senderId !== self.id && <Typography sx={{ fontSize: 12, fontWeight: 700, color: 'primary.main', mb: 0.25 }}>{message.senderName}</Typography>}
                <Typography sx={{ fontSize: 14, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{message.body}</Typography>
                <Typography sx={{ fontSize: 10, opacity: 0.72, textAlign: 'right', mt: 0.25 }}>{messageTime(message.createdAt)}</Typography>
              </Box>
            </Box>)}
          </Box>
          <Box component="form" onSubmit={(event) => { event.preventDefault(); void submit() }} sx={{ display: 'flex', gap: 1, p: 1.5, borderTop: 1, borderColor: 'divider' }}>
            <TextField fullWidth size="small" multiline maxRows={4} value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="Сообщение сотрудникам" slotProps={{ htmlInput: { maxLength: 4000, 'aria-label': 'Текст сообщения' } }} />
            <IconButton type="submit" color="primary" aria-label="Отправить сообщение" disabled={sending || !draft.trim()}><SendRounded /></IconButton>
          </Box>
        </> : <Box sx={{ m: 'auto', p: 4, textAlign: 'center' }}><AddRounded color="action" sx={{ fontSize: 42 }} /><Typography sx={{ color: 'text.secondary', mt: 1 }}>Выберите беседу или создайте новую</Typography></Box>}
      </Box>
    </Paper>

    <Dialog open={directOpen} onClose={() => setDirectOpen(false)} fullWidth maxWidth="xs">
      <DialogTitle>Новый личный чат</DialogTitle>
      <DialogContent dividers sx={{ p: 0 }}><List>{colleagues.map((colleague) => <ListItemButton key={colleague.id} onClick={() => void createDirect(colleague.id)} disabled={creating}><ListItemText primary={colleague.fullName} secondary={roleLabels[colleague.role]} /></ListItemButton>)}</List></DialogContent>
      <DialogActions><Button onClick={() => setDirectOpen(false)}>Отмена</Button></DialogActions>
    </Dialog>
    <Dialog open={groupOpen} onClose={() => setGroupOpen(false)} fullWidth maxWidth="xs">
      <DialogTitle>Новая группа</DialogTitle>
      <DialogContent dividers>
        <TextField fullWidth label="Название группы" value={groupTitle} onChange={(event) => setGroupTitle(event.target.value)} slotProps={{ htmlInput: { maxLength: 100 } }} sx={{ mb: 2 }} />
        <Typography sx={{ color: 'text.secondary', fontSize: 13, mb: 1 }}>Выберите не менее двух сотрудников. Вы будете добавлены автоматически.</Typography>
        {colleagues.map((colleague) => <FormControlLabel key={colleague.id} sx={{ display: 'flex' }} control={<Checkbox checked={groupMembers.includes(colleague.id)} onChange={(_event, checked) => setGroupMembers((items) => checked ? [...items, colleague.id] : items.filter((id) => id !== colleague.id))} />} label={`${colleague.fullName} · ${roleLabels[colleague.role]}`} />)}
      </DialogContent>
      <DialogActions><Button onClick={() => setGroupOpen(false)}>Отмена</Button><Button variant="contained" onClick={() => void createGroup()} disabled={creating || !groupTitle.trim() || groupMembers.length < 2}>Создать</Button></DialogActions>
    </Dialog>
  </Box>
}
