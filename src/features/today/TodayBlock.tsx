import type { ReactNode } from 'react'
import { Alert, Box, Button, Typography } from '@mui/material'

type TodayBlockProps = {
  title: string
  /** Строка, когда показывать нечего: «Просроченных задач нет». */
  empty: string
  count: number
  error?: string
  loading?: boolean
  /** Переход на полную страницу раздела. */
  more?: { label: string; onClick: () => void }
  children: ReactNode
}

export function TodayBlock({ title, empty, count, error, loading = false, more, children }: TodayBlockProps) {
  return (
    <Box className="panel today-block" component="section" aria-label={title}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1, mb: 1 }}>
        <Typography className="panel-label">{title.toLocaleUpperCase('ru-RU')}{count > 0 ? ` · ${count}` : ''}</Typography>
        {more && <Button size="small" onClick={more.onClick}>{more.label}</Button>}
      </Box>
      {error
        ? <Alert severity="error">{error}</Alert>
        : loading
          ? <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>Загружаем…</Typography>
          : count === 0 ? <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>{empty}</Typography> : children}
    </Box>
  )
}
