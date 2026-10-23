import { Alert, Box, Typography } from '@mui/material'
import type { Practitioner, ServiceItem } from '../../api/types'
import { PriceForm } from './PriceForm'
import { ServiceForm } from './ServiceForm'
import { WorkingHoursForm } from './WorkingHoursForm'

type SettingsPageProps = {
  practitioners: Practitioner[]
  services: ServiceItem[]
  onSaveHours: (practitioner: Practitioner, shifts: Practitioner['workingHours']) => Promise<string | null>
  onSavePrice: (service: ServiceItem, priceMinor: number) => Promise<string | null>
  onCreateService: (input: { name: string; durationMinutes: number; priceMinor: number }) => Promise<string | null>
  onToggleService: (service: ServiceItem) => Promise<string | null>
}

/** Только администратор: недельные смены врачей и справочник услуг. */
export function SettingsPage({ practitioners, services, onSaveHours, onSavePrice, onCreateService, onToggleService }: SettingsPageProps) {
  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>График и цены</Typography><Typography sx={{ color: 'text.secondary' }}>Смены врачей по дням недели и справочник услуг.</Typography></Box>
      </Box>
      <Box className="panel" sx={{ mb: 2 }}>
        <Typography className="panel-label">УСЛУГИ И ЦЕНЫ</Typography>
        <Alert severity="info" sx={{ my: 1 }}>Новая цена и выключение услуги действуют для новых записей. В уже созданных записях остаются название и цена на момент записи.</Alert>
        {services.length === 0 && <Typography sx={{ color: 'text.secondary', my: 1 }}>Услуг пока нет. Добавьте первую — она сразу появится в записи на приём.</Typography>}
        {services.map((service) => <PriceForm key={`${service.id}-${service.priceMinor}`} service={service} onSave={(priceMinor) => onSavePrice(service, priceMinor)} onToggle={() => onToggleService(service)} />)}
        <ServiceForm services={services} onCreate={onCreateService} />
      </Box>
      <Box className="settings-grid">
        {practitioners.map((practitioner) => <WorkingHoursForm key={practitioner.id} practitioner={practitioner} onSave={(shifts) => onSaveHours(practitioner, shifts)} />)}
      </Box>
    </>
  )
}
