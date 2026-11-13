import { useLayoutEffect, useRef, useState } from 'react'
import { AddReactionRounded, AddRounded, AttachFileRounded, CloseRounded, DeleteOutlineRounded, DoneAllRounded, DoneRounded, EditOutlined, GroupAddRounded, PersonAddRounded, SendRounded, SettingsRounded } from '@mui/icons-material'
import { Alert, Avatar, Badge, Box, Button, Checkbox, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, List, ListItemButton, ListItemText, Paper, Popover, Switch, TextField, Tooltip, Typography } from '@mui/material'
import { createDirectConversation, createGroupConversation, deleteStaffMessage, editStaffMessage, reactStaffMessage, sendStaffMessage, type StaffChatMember, type StaffChatMessage, type StaffChatPreferences, type StaffConversation } from '../../api/staffChat'
import type { StaffItem } from '../../api/types'
import { initialsOf } from '../../shared/text'
import { roleLabels } from '../../shared/roles'
import type { StaffChatState } from './useStaffChat'

const REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🙏']

function messageTime(value: string) {
  return new Date(value).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}

function fileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} Б`
  if (bytes < 1024 * 1024) return `${Math.ceil(bytes / 1024)} КБ`
  return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`
}

function mentionAt(text: string, caret: number) {
  const match = /(^|\s)@([^\s@]*)$/.exec(text.slice(0, caret))
  return match ? { start: match.index + match[1].length, query: match[2] } : null
}

type Props = { self: StaffItem; apiOnline: boolean; chat: StaffChatState }

/** Внутренний чат сотрудников использует общее состояние рабочего места. */
export function StaffMessagesPage({ self, apiOnline, chat }: Props) {
  const {
    conversations, colleagues, selectedId, messages, loading, messagesLoading, historyLoading, hasMore,
    connected, error, preferences, setError, reloadConversations, openConversation, clearConversation,
    appendMessage, loadEarlier,
  } = chat
  const [draft, setDraft] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [mentionIds, setMentionIds] = useState<string[]>([])
  const [mentionCaret, setMentionCaret] = useState(0)
  const [mentionQuery, setMentionQuery] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [actionBusy, setActionBusy] = useState(false)
  const [editing, setEditing] = useState<StaffChatMessage | null>(null)
  const [editDraft, setEditDraft] = useState('')
  const [deleting, setDeleting] = useState<StaffChatMessage | null>(null)
  const [reactionMessageId, setReactionMessageId] = useState<number | null>(null)
  const [reactionAnchor, setReactionAnchor] = useState<HTMLElement | null>(null)
  const [directOpen, setDirectOpen] = useState(false)
  const [groupOpen, setGroupOpen] = useState(false)
  const [preferencesOpen, setPreferencesOpen] = useState(false)
  const [preferencesSaving, setPreferencesSaving] = useState(false)
  const [groupTitle, setGroupTitle] = useState('')
  const [groupMembers, setGroupMembers] = useState<string[]>([])
  const [creating, setCreating] = useState(false)
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const draftInputRef = useRef<HTMLInputElement | HTMLTextAreaElement | null>(null)
  const historyPositionRef = useRef<{ height: number; top: number } | null>(null)
  const previousConversationRef = useRef<string | null>(null)
  const previousLastIdRef = useRef<number | null>(null)
  const selected = conversations.find((item) => item.id === selectedId) ?? null

  useLayoutEffect(() => {
    const viewport = scrollRef.current
    if (!viewport) return
    const lastId = messages.at(-1)?.id ?? null
    const conversationChanged = previousConversationRef.current !== selectedId
    const historyPosition = historyPositionRef.current
    if (historyPosition) {
      viewport.scrollTop = viewport.scrollHeight - historyPosition.height + historyPosition.top
      historyPositionRef.current = null
    } else if (conversationChanged || previousLastIdRef.current !== lastId) {
      viewport.scrollTop = viewport.scrollHeight
    }
    previousConversationRef.current = selectedId
    previousLastIdRef.current = lastId
  }, [messages, selectedId])

  const resetComposer = () => {
    setDraft('')
    setFiles([])
    setMentionIds([])
    setMentionQuery(null)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const selectConversation = async (conversationId: string) => {
    resetComposer()
    await openConversation(conversationId)
  }

  const clearSelectedConversation = () => {
    resetComposer()
    clearConversation()
  }

  const updateDraft = (text: string, caret: number) => {
    setDraft(text)
    setMentionCaret(caret)
    setMentionQuery(mentionAt(text, caret)?.query ?? null)
    setMentionIds((current) => current.filter((id) => {
      const member = selected?.members.find((item) => item.id === id)
      return member ? text.includes(`@${member.fullName}`) : false
    }))
  }

  const chooseMention = (member: StaffChatMember) => {
    const match = mentionAt(draft, mentionCaret)
    if (!match) return
    const insertion = `@${member.fullName} `
    const next = `${draft.slice(0, match.start)}${insertion}${draft.slice(mentionCaret)}`
    const caret = match.start + insertion.length
    setDraft(next)
    setMentionIds((current) => current.includes(member.id) ? current : [...current, member.id])
    setMentionQuery(null)
    window.setTimeout(() => {
      draftInputRef.current?.focus()
      draftInputRef.current?.setSelectionRange(caret, caret)
      setMentionCaret(caret)
    }, 0)
  }

  const submit = async () => {
    if (!selectedId || (!draft.trim() && files.length === 0)) return
    setSending(true)
    setError('')
    try {
      const message = await sendStaffMessage(selectedId, draft, mentionIds, files)
      appendMessage(message)
      resetComposer()
      await reloadConversations()
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setSending(false)
    }
  }

  const chooseFiles = (selectedFiles: FileList | null) => {
    const added = Array.from(selectedFiles ?? [])
    if (files.length + added.length > 5) {
      setError('Можно прикрепить не более 5 файлов к одному сообщению')
      return
    }
    if (added.some((file) => file.size === 0 || file.size > 10 * 1024 * 1024)) {
      setError('Каждый файл должен быть от 1 байта до 10 МБ')
      return
    }
    setError('')
    setFiles((current) => [...current, ...added])
  }

  const loadHistory = async () => {
    const viewport = scrollRef.current
    if (!viewport || !selectedId || !hasMore || historyLoading) return
    historyPositionRef.current = { height: viewport.scrollHeight, top: viewport.scrollTop }
    await loadEarlier(selectedId)
  }

  const runMessageAction = async (action: () => Promise<StaffChatMessage>) => {
    setActionBusy(true)
    setError('')
    try {
      appendMessage(await action())
      await reloadConversations()
      return true
    } catch (reason) {
      setError((reason as Error).message)
      return false
    } finally {
      setActionBusy(false)
    }
  }

  const saveEdit = async () => {
    if (!editing || !editDraft.trim()) return
    if (await runMessageAction(() => editStaffMessage(editing.id, editDraft))) setEditing(null)
  }

  const confirmDelete = async () => {
    if (!deleting) return
    if (await runMessageAction(() => deleteStaffMessage(deleting.id))) setDeleting(null)
  }

  const toggleReaction = async (messageId: number, emoji: string) => {
    setReactionAnchor(null)
    setReactionMessageId(null)
    await runMessageAction(() => reactStaffMessage(messageId, emoji))
  }

  const changePreference = async (key: keyof StaffChatPreferences, enabled: boolean) => {
    const next = { ...preferences, [key]: enabled }
    setPreferencesSaving(true)
    setError('')
    try {
      if (key === 'browserEnabled' && enabled) {
        if (!('Notification' in window)) throw new Error('Этот браузер не поддерживает системные уведомления')
        const permission = Notification.permission === 'default' ? await Notification.requestPermission() : Notification.permission
        if (permission !== 'granted') throw new Error('Разрешите уведомления в настройках браузера')
      }
      await chat.updatePreferences(next)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setPreferencesSaving(false)
    }
  }

  const finishCreate = async (conversation: StaffConversation) => {
    setDirectOpen(false)
    setGroupOpen(false)
    setGroupTitle('')
    setGroupMembers([])
    await reloadConversations()
    await selectConversation(conversation.id)
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

  const mentionMembers = selected?.members.filter((member) => member.active && member.id !== self.id && member.fullName.toLocaleLowerCase('ru-RU').includes((mentionQuery ?? '').toLocaleLowerCase('ru-RU'))) ?? []

  if (!apiOnline) return <Alert severity="info">Внутренний чат доступен после подключения к серверу клиники. Данные песочницы не отправляются сотрудникам.</Alert>
  if (loading) return <Box sx={{ display: 'flex', justifyContent: 'center', p: 5 }}><CircularProgress aria-label="Загрузка сообщений" /></Box>

  return <Box>
    {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError('')}>{error}</Alert>}
    <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center', mb: 2 }}>
      <Button variant="contained" startIcon={<PersonAddRounded />} onClick={() => setDirectOpen(true)} disabled={colleagues.length === 0}>Личный чат</Button>
      <Button variant="outlined" startIcon={<GroupAddRounded />} onClick={() => setGroupOpen(true)} disabled={colleagues.length < 2}>Создать группу</Button>
      <IconButton aria-label="Настройки уведомлений" onClick={() => setPreferencesOpen(true)}><SettingsRounded /></IconButton>
      <Chip size="small" variant="outlined" color={connected ? 'success' : 'default'} label={connected ? 'На связи' : 'Восстановление соединения'} />
      <Typography sx={{ color: 'text.secondary', fontSize: 13 }}>Только сотрудники вашей клиники</Typography>
    </Box>
    <Paper variant="outlined" sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '320px minmax(0, 1fr)' }, height: { xs: 'auto', md: 'calc(100vh - 275px)' }, minHeight: 480, overflow: 'hidden' }}>
      <Box sx={{ borderRight: { md: 1 }, borderColor: 'divider', overflowY: 'auto', display: { xs: selected ? 'none' : 'block', md: 'block' }, maxHeight: { xs: 360, md: 'none' } }}>
        {conversations.length === 0 ? <Typography sx={{ p: 3, color: 'text.secondary' }}>Бесед пока нет. Начните личный чат или создайте группу.</Typography> : <List disablePadding>
          {conversations.map((conversation) => <ListItemButton key={conversation.id} selected={conversation.id === selectedId} onClick={() => void selectConversation(conversation.id)} alignItems="flex-start" sx={{ gap: 1.5, py: 1.5 }}>
            <Badge badgeContent={conversation.unreadCount} color="primary" overlap="circular"><Avatar sx={{ width: 38, height: 38, fontSize: 14 }}>{initialsOf(conversation.title)}</Avatar></Badge>
            <ListItemText primary={conversation.title} secondary={conversation.lastMessage ? `${conversation.lastMessage.own ? 'Вы: ' : ''}${conversation.lastMessage.body}` : 'Сообщений пока нет'} slotProps={{ primary: { noWrap: true, sx: { fontWeight: conversation.unreadCount > 0 ? 750 : 600 } }, secondary: { noWrap: true } }} />
            <Typography sx={{ color: 'text.secondary', fontSize: 11, whiteSpace: 'nowrap' }}>{conversation.lastMessage ? messageTime(conversation.lastMessage.createdAt) : ''}</Typography>
          </ListItemButton>)}
        </List>}
      </Box>
      <Box sx={{ display: 'flex', flexDirection: 'column', minWidth: 0, minHeight: { xs: 480, md: 0 } }}>
        {selected ? <>
          <Box sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider', display: 'flex', alignItems: 'center', gap: 1 }}>
            <Button size="small" sx={{ display: { md: 'none' } }} onClick={clearSelectedConversation}>К чатам</Button>
            <Box sx={{ minWidth: 0 }}><Typography noWrap sx={{ fontWeight: 750 }}>{selected.title}</Typography><Typography noWrap sx={{ color: 'text.secondary', fontSize: 12 }}>{selected.kind === 'GROUP' ? `Участников: ${selected.members.length}` : 'Личная беседа'}</Typography></Box>
          </Box>
          <Box ref={scrollRef} aria-label="История сообщений" onScroll={(event) => { if (event.currentTarget.scrollTop <= 80) void loadHistory() }} sx={{ flex: 1, overflowY: 'auto', p: 2, bgcolor: 'action.hover', minHeight: 0 }}>
            {!messagesLoading && hasMore && <Box sx={{ display: 'flex', justifyContent: 'center', mb: 1 }}>
              <Button size="small" onClick={() => void loadHistory()} disabled={historyLoading} startIcon={historyLoading ? <CircularProgress size={14} /> : undefined}>Загрузить предыдущие сообщения</Button>
            </Box>}
            {messagesLoading ? <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress size={28} /></Box> : messages.length === 0 ? <Typography sx={{ color: 'text.secondary', textAlign: 'center', mt: 4 }}>Напишите первое сообщение</Typography> : messages.map((message) => <Box key={message.id} sx={{ display: 'flex', justifyContent: message.own ? 'flex-end' : 'flex-start', mb: 1.25 }}>
              <Box sx={{ maxWidth: '78%', minWidth: 120, px: 1.5, py: 1, borderRadius: 2, bgcolor: message.own ? 'primary.main' : 'background.paper', color: message.own ? 'primary.contrastText' : 'text.primary', boxShadow: 1, border: message.mentioned ? 2 : 0, borderColor: 'secondary.main' }}>
                {selected.kind === 'GROUP' && !message.own && <Typography sx={{ fontSize: 12, fontWeight: 700, color: 'primary.main', mb: 0.25 }}>{message.senderName}</Typography>}
                {message.mentioned && <Chip size="small" color="secondary" label="Вас упомянули" sx={{ mb: 0.5 }} />}
                {message.deleted ? <Typography sx={{ fontSize: 14, fontStyle: 'italic', opacity: 0.72 }}>Сообщение удалено</Typography> : <>
                  {message.body && <Typography sx={{ fontSize: 14, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{message.body}</Typography>}
                  {message.attachments.map((attachment) => <Button key={attachment.id} component="a" href={attachment.downloadUrl} download={attachment.name} startIcon={<AttachFileRounded />} size="small" color="inherit" sx={{ display: 'flex', justifyContent: 'flex-start', textTransform: 'none', px: 0, maxWidth: '100%' }}>
                    <Box component="span" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{attachment.name} · {fileSize(attachment.sizeBytes)}</Box>
                  </Button>)}
                  {message.reactions.length > 0 && <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap', mt: 0.75 }}>
                    {message.reactions.map((reaction) => <Chip key={reaction.emoji} size="small" clickable disabled={actionBusy} color={reaction.own ? 'secondary' : 'default'} variant={reaction.own ? 'filled' : 'outlined'} onClick={() => void toggleReaction(message.id, reaction.emoji)} label={`${reaction.emoji} ${reaction.count}`} aria-label={`Реакция ${reaction.emoji}, ${reaction.count}`} />)}
                  </Box>}
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 0.25, mt: 0.25, opacity: 0.82 }}>
                    {message.editedAt && <Typography sx={{ fontSize: 10 }}>изменено</Typography>}
                    <Typography sx={{ fontSize: 10 }}>{messageTime(message.createdAt)}</Typography>
                    {message.own && <Tooltip title={message.readByAll ? 'Прочитано всеми участниками' : 'Доставлено на сервер'}><Box component="span" sx={{ display: 'inline-flex' }} aria-label={message.readByAll ? 'Прочитано всеми участниками' : 'Доставлено на сервер'}>{message.readByAll ? <DoneAllRounded sx={{ fontSize: 16 }} /> : <DoneRounded sx={{ fontSize: 16 }} />}</Box></Tooltip>}
                    <Tooltip title="Добавить реакцию"><IconButton size="small" color="inherit" aria-label={`Реакции к сообщению ${message.id}`} disabled={actionBusy} onClick={(event) => { setReactionMessageId(message.id); setReactionAnchor(event.currentTarget) }}><AddReactionRounded sx={{ fontSize: 16 }} /></IconButton></Tooltip>
                    {message.own && <><Tooltip title="Редактировать"><IconButton size="small" color="inherit" aria-label={`Редактировать сообщение ${message.id}`} disabled={actionBusy} onClick={() => { setEditing(message); setEditDraft(message.body) }}><EditOutlined sx={{ fontSize: 16 }} /></IconButton></Tooltip>
                      <Tooltip title="Удалить"><IconButton size="small" color="inherit" aria-label={`Удалить сообщение ${message.id}`} disabled={actionBusy} onClick={() => setDeleting(message)}><DeleteOutlineRounded sx={{ fontSize: 16 }} /></IconButton></Tooltip></>}
                  </Box>
                </>}
              </Box>
            </Box>)}
          </Box>
          <Box component="form" onSubmit={(event) => { event.preventDefault(); void submit() }} sx={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 0.75, p: 1.5, borderTop: 1, borderColor: 'divider' }}>
            {mentionQuery !== null && mentionMembers.length > 0 && <Paper elevation={6} sx={{ position: 'absolute', left: 12, right: 12, bottom: 'calc(100% - 4px)', maxHeight: 240, overflowY: 'auto', zIndex: 2 }}>
              <List dense disablePadding>{mentionMembers.map((member) => <ListItemButton key={member.id} onClick={() => chooseMention(member)}><ListItemText primary={`Упомянуть ${member.fullName}`} secondary={roleLabels[member.role]} /></ListItemButton>)}</List>
            </Paper>}
            {files.length > 0 && <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>{files.map((file, index) => <Chip key={`${file.name}-${file.lastModified}-${index}`} size="small" label={`${file.name} · ${fileSize(file.size)}`} onDelete={() => setFiles((current) => current.filter((_item, itemIndex) => itemIndex !== index))} deleteIcon={<CloseRounded />} />)}</Box>}
            <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-end' }}>
              <input ref={fileInputRef} type="file" multiple aria-label="Файлы сообщения" style={{ display: 'none' }} onChange={(event) => { chooseFiles(event.target.files); event.target.value = '' }} />
              <Tooltip title="До 5 файлов, не более 10 МБ каждый"><IconButton aria-label="Прикрепить файлы" disabled={sending || files.length >= 5} onClick={() => fileInputRef.current?.click()}><AttachFileRounded /></IconButton></Tooltip>
              <TextField
                fullWidth
                size="small"
                multiline
                maxRows={4}
                value={draft}
                inputRef={draftInputRef}
                onChange={(event) => updateDraft(event.target.value, event.target.selectionStart ?? event.target.value.length)}
                onSelect={() => {
                  const caret = draftInputRef.current?.selectionStart ?? draft.length
                  setMentionCaret(caret)
                  setMentionQuery(mentionAt(draft, caret)?.query ?? null)
                }}
                onKeyDown={(event) => { if (event.key === 'Escape') setMentionQuery(null) }}
                placeholder="Сообщение сотрудникам; @ — упомянуть"
                slotProps={{ htmlInput: { maxLength: 4000, 'aria-label': 'Текст сообщения' } }}
              />
              <IconButton type="submit" color="primary" aria-label="Отправить сообщение" disabled={sending || (!draft.trim() && files.length === 0)}><SendRounded /></IconButton>
            </Box>
          </Box>
        </> : <Box sx={{ m: 'auto', p: 4, textAlign: 'center' }}><AddRounded color="action" sx={{ fontSize: 42 }} /><Typography sx={{ color: 'text.secondary', mt: 1 }}>Выберите беседу или создайте новую</Typography></Box>}
      </Box>
    </Paper>

    <Popover open={reactionAnchor !== null} anchorEl={reactionAnchor} onClose={() => { setReactionAnchor(null); setReactionMessageId(null) }} anchorOrigin={{ vertical: 'top', horizontal: 'center' }} transformOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
      <Box sx={{ display: 'flex', p: 0.5 }}>{REACTIONS.map((emoji) => <IconButton key={emoji} aria-label={`Поставить реакцию ${emoji}`} disabled={actionBusy || reactionMessageId === null} onClick={() => { if (reactionMessageId !== null) void toggleReaction(reactionMessageId, emoji) }} sx={{ fontSize: 22 }}>{emoji}</IconButton>)}</Box>
    </Popover>
    <Dialog open={editing !== null} onClose={() => { if (!actionBusy) setEditing(null) }} fullWidth maxWidth="sm">
      <DialogTitle>Редактировать сообщение</DialogTitle>
      <DialogContent dividers><TextField fullWidth multiline minRows={3} label="Текст сообщения" value={editDraft} onChange={(event) => setEditDraft(event.target.value)} slotProps={{ htmlInput: { maxLength: 4000 } }} /></DialogContent>
      <DialogActions><Button onClick={() => setEditing(null)} disabled={actionBusy}>Отмена</Button><Button variant="contained" onClick={() => void saveEdit()} disabled={actionBusy || !editDraft.trim()}>Сохранить</Button></DialogActions>
    </Dialog>
    <Dialog open={deleting !== null} onClose={() => { if (!actionBusy) setDeleting(null) }} fullWidth maxWidth="xs">
      <DialogTitle>Удалить сообщение?</DialogTitle>
      <DialogContent dividers><Typography>Текст, реакции и доступ к вложениям будут удалены. В переписке останется отметка об удалении.</Typography></DialogContent>
      <DialogActions><Button onClick={() => setDeleting(null)} disabled={actionBusy}>Отмена</Button><Button color="error" variant="contained" onClick={() => void confirmDelete()} disabled={actionBusy}>Удалить</Button></DialogActions>
    </Dialog>
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
    <Dialog open={preferencesOpen} onClose={() => setPreferencesOpen(false)} fullWidth maxWidth="xs">
      <DialogTitle>Настройки уведомлений</DialogTitle>
      <DialogContent dividers>
        <FormControlLabel sx={{ display: 'flex', ml: 0 }} control={<Switch checked={preferences.notificationsEnabled} disabled={preferencesSaving} onChange={(_event, checked) => void changePreference('notificationsEnabled', checked)} />} label="Уведомления о новых сообщениях" />
        <Typography sx={{ color: 'text.secondary', fontSize: 13, mb: 2 }}>Отключение не влияет на счётчик непрочитанных сообщений.</Typography>
        <FormControlLabel sx={{ display: 'flex', ml: 0 }} control={<Switch checked={preferences.soundEnabled} disabled={preferencesSaving} onChange={(_event, checked) => void changePreference('soundEnabled', checked)} />} label="Звуковой сигнал" />
        <FormControlLabel sx={{ display: 'flex', ml: 0 }} control={<Switch checked={preferences.browserEnabled} disabled={preferencesSaving} onChange={(_event, checked) => void changePreference('browserEnabled', checked)} />} label="Уведомления браузера" />
        <Typography sx={{ color: 'text.secondary', fontSize: 13, mt: 1 }}>Системное уведомление появляется, когда вкладка CRM скрыта или не в фокусе. Нажатие открывает нужную беседу.</Typography>
      </DialogContent>
      <DialogActions><Button variant="contained" onClick={() => setPreferencesOpen(false)}>Готово</Button></DialogActions>
    </Dialog>
  </Box>
}
