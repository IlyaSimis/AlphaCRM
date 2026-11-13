import { AddRounded, ChevronRightRounded, CloudDownloadRounded, PersonSearchRounded, SearchRounded, ShieldOutlined } from '@mui/icons-material'
import { Avatar, Box, Button, Chip, InputAdornment, Paper, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Typography } from '@mui/material'
import type { PatientItem } from '../../api/types'
import { initialsOf } from '../../shared/text'
import { consentLabels } from './consentLabels'

type PatientsListProps = {
  query: string
  setQuery: (value: string) => void
  patients: PatientItem[]
  onPatient: (patient: PatientItem) => void
  onCreate: () => void
  onImport?: () => void
}

export function PatientsList({ query, setQuery, patients, onPatient, onCreate, onImport }: PatientsListProps) {
  return (
    <>
      <Box className="section-intro">
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 750 }}>Пациенты</Typography>
          <Typography sx={{ color: 'text.secondary' }}>Все пациенты, доступные вашей роли</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          {onImport && <Button variant="outlined" startIcon={<CloudDownloadRounded />} onClick={onImport}>Импорт PostgreSQL</Button>}
          <Button variant="contained" startIcon={<AddRounded />} onClick={onCreate}>Новый пациент</Button>
        </Stack>
      </Box>
      <TextField
        fullWidth
        label="Поиск по имени или телефону"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Например, Анна Михайлова или +7 916..."
        slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> } }}
      />
      <Box className="data-boundary"><ShieldOutlined /><Box><Typography sx={{ fontWeight: 700 }}>Административное представление</Typography><Typography sx={{ fontSize: 13 }}>Здесь доступны только контакты, согласия и записи. Диагнозы и содержание приёмов не включены в этот экран.</Typography></Box></Box>
      <TableContainer component={Paper} variant="outlined" className="patient-results">
        {patients.length ? (
          <Table aria-label="Пациенты" sx={{ minWidth: 760 }}>
            <TableHead>
              <TableRow>
                <TableCell>Пациент</TableCell>
                <TableCell>Дата рождения</TableCell>
                <TableCell>Телефон</TableCell>
                <TableCell>Согласие</TableCell>
                <TableCell align="right" aria-label="Открыть карту" />
              </TableRow>
            </TableHead>
            <TableBody>
              {patients.map((patient) => (
                <TableRow hover key={patient.id} onClick={() => onPatient(patient)} sx={{ cursor: 'pointer' }}>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Avatar>{initialsOf(patient.name)}</Avatar>
                      <Button variant="text" color="inherit" sx={{ px: 0, fontWeight: 750, textAlign: 'left', textTransform: 'none' }}>{patient.name}</Button>
                    </Box>
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{patient.birth}</TableCell>
                  <TableCell>
                    <Typography sx={{ fontSize: 14, whiteSpace: 'nowrap' }}>{patient.phone}</Typography>
                    {patient.secondPhone && <Typography sx={{ fontSize: 12, color: 'text.secondary', whiteSpace: 'nowrap' }}>Доп.: {patient.secondPhone}</Typography>}
                  </TableCell>
                  <TableCell><Chip label={consentLabels[patient.consent]} size="small" color={patient.consent === 'ACTIVE' ? 'success' : 'warning'} variant="outlined" /></TableCell>
                  <TableCell align="right" width={48}><ChevronRightRounded color="disabled" /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        ) : (
          <Box className="empty"><PersonSearchRounded /><Typography sx={{ fontWeight: 700 }}>Пациенты не найдены</Typography><Typography sx={{ color: 'text.secondary' }}>Проверьте запрос или создайте новую карточку</Typography></Box>
        )}
      </TableContainer>
    </>
  )
}
