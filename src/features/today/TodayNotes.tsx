import { useEffect, useState } from 'react'
import { Box, Typography } from '@mui/material'
import { loadWorkspaceItems } from '../../api/workspace'
import type { WorkspaceItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { offlineStore } from '../workspace/workspace'
import { TodayBlock } from './TodayBlock'

type TodayNotesProps = {
  apiOnline: boolean
  selfId: string
  onOpen: () => void
}

/** Последние личные заметки сотрудника; полное редактирование остаётся в «Моём пространстве». */
export function TodayNotes({ apiOnline, selfId, onOpen }: TodayNotesProps) {
  const [items, setItems] = useState<WorkspaceItem[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    loadWorkspaceItems()
      .then((value) => { if (!cancelled) { setItems(value); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline, selfId])

  const source = apiOnline ? items ?? [] : offlineStore(selfId).items
  const notes = source
    .filter((item) => !item.archivedAt && item.type.trim().toLocaleLowerCase('ru-RU') === 'заметка')
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))

  return (
    <TodayBlock title="Заметки" empty="Заметок пока нет" count={notes.length} error={error} loading={apiOnline && items === null && !error} more={{ label: 'Все заметки', onClick: onOpen }}>
      {notes.slice(0, 5).map((note) => (
        <Box component="button" type="button" className="today-row" key={note.id} onClick={onOpen} aria-label={`Открыть заметку: ${note.title}`}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography sx={{ fontWeight: 650 }}>{note.title}</Typography>
            {note.body && <Typography sx={{ fontSize: 13, color: 'text.secondary', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{note.body}</Typography>}
          </Box>
          <Typography sx={{ fontSize: 13, color: 'text.secondary', whiteSpace: 'nowrap' }}>{formatClinicMoment(note.updatedAt)}</Typography>
        </Box>
      ))}
    </TodayBlock>
  )
}
