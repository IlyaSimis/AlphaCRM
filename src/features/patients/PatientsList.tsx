import { AddRounded, ChevronRightRounded, PersonSearchRounded, SearchRounded, ShieldOutlined } from '@mui/icons-material'
import { Avatar, Box, Button, Chip, InputAdornment, TextField, Typography } from '@mui/material'
import type { PatientItem } from '../../api/types'
import { initialsOf } from '../../shared/text'

type PatientsListProps = {
  query: string
  setQuery: (value: string) => void
  patients: PatientItem[]
  onPatient: (patient: PatientItem) => void
  onCreate: () => void
}

export function PatientsList({ query, setQuery, patients, onPatient, onCreate }: PatientsListProps) {
  return (
    <>
      <Box className="section-intro"><Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Поиск пациента</Typography><Typography sx={{ color: 'text.secondary' }}>По телефону, ФИО или дате рождения</Typography></Box><Button variant="contained" startIcon={<AddRounded />} onClick={onCreate}>Новый пациент</Button></Box>
      <TextField fullWidth value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Например, Анна Михайлова или +7 916..." slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> } }} />
      <Box className="data-boundary"><ShieldOutlined /><Box><Typography sx={{ fontWeight: 700 }}>Административное представление</Typography><Typography sx={{ fontSize: 13 }}>Здесь доступны только контакты, согласия и записи. Диагнозы и содержание приёмов не включены в этот экран.</Typography></Box></Box>
      <Box className="patient-results">
        {patients.length ? patients.map((patient) => <Box className="patient-row" key={patient.id} onClick={() => onPatient(patient)} role="button" tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter') onPatient(patient) }}>
          <Avatar>{initialsOf(patient.name)}</Avatar>
          <Box className="patient-main"><Typography sx={{ fontWeight: 750 }}>{patient.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Дата рождения: {patient.birth}</Typography></Box>
          <Box><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>Телефон</Typography><Typography sx={{ fontSize: 14 }}>{patient.phone}</Typography></Box>
          <Chip label={patient.consent} size="small" color={patient.consent === 'Действует' ? 'success' : 'warning'} variant="outlined" />
          <ChevronRightRounded color="disabled" />
        </Box>) : <Box className="empty"><PersonSearchRounded /><Typography sx={{ fontWeight: 700 }}>Пациенты не найдены</Typography><Typography sx={{ color: 'text.secondary' }}>Проверьте запрос или создайте новую карточку</Typography></Box>}
      </Box>
    </>
  )
}
