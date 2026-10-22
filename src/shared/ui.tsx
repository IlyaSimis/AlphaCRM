import type { ReactNode } from 'react'
import { Box, Typography } from '@mui/material'

export function Metric({ value, label, note, accent = false }: { value: string; label: string; note: string; accent?: boolean }) {
  return <Box className={`metric ${accent ? 'accent' : ''}`}><Typography className="metric-value">{value}</Typography><Box><Typography sx={{ fontWeight: 700 }}>{label}</Typography><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{note}</Typography></Box></Box>
}

export function Info({ label, value }: { label: string; value: string }) {
  return <Box className="info-row"><Typography sx={{ color: 'text.secondary', fontSize: 13 }}>{label}</Typography><Typography sx={{ fontWeight: 600 }}>{value}</Typography></Box>
}

export function FormSection({ number, title, children }: { number: string; title: string; children: ReactNode }) {
  return <Box className="form-section"><Box className="form-section-title"><Typography>{number}</Typography><Typography sx={{ fontWeight: 750 }}>{title}</Typography></Box>{children}</Box>
}
