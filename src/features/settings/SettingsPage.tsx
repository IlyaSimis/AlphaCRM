import { Alert, Box, Typography } from '@mui/material'
import type { ServicePricing } from '../../api/schedule'
import type { Practitioner, ServiceItem } from '../../api/types'
import type { NewService } from '../../app/useClinicData'
import { PriceForm } from './PriceForm'
import { ServiceForm } from './ServiceForm'
import { WorkingHoursForm } from './WorkingHoursForm'

type SettingsPageProps = {
  practitioners: Practitioner[]
  services: ServiceItem[]
  onSaveHours: (practitioner: Practitioner, shifts: Practitioner['workingHours']) => Promise<string | null>
  onSaveCard: (practitioner: Practitioner, input: { specialty: string; room: string }) => Promise<string | null>
  onSavePrice: (service: ServiceItem, pricing: ServicePricing) => Promise<string | null>
  onCreateService: (input: NewService) => Promise<string | null>
  onToggleService: (service: ServiceItem) => Promise<string | null>
  /** Цены и услуги меняют администраторы и владелец; менеджер видит их без правки. */
  canEditPrices?: boolean
  /** «График врачей» — в базе пациентов, «Услуги и цены» — в финансах. */
  section: 'hours' | 'prices'
}

/** Недельные смены врачей или справочник услуг. Цены — только администраторам и владельцу. */
export function SettingsPage({ practitioners, services, onSaveHours, onSaveCard, onSavePrice, onCreateService, onToggleService, canEditPrices = true, section }: SettingsPageProps) {
  if (section === 'hours') return (
    <>
      <Box className="section-intro">
        <Box><Typography sx={{ color: 'text.secondary' }}>Специальность, кабинет и смены врачей по дням недели. Новый врач заводится в «Дашборды → Сотрудники».</Typography></Box>
      </Box>
      <Box className="settings-grid">
        {practitioners.map((practitioner) => <WorkingHoursForm key={practitioner.id} practitioner={practitioner} onSave={(shifts) => onSaveHours(practitioner, shifts)} onSaveCard={(input) => onSaveCard(practitioner, input)} />)}
      </Box>
    </>
  )
  return (
    <>
      <Box className="section-intro">
        <Box><Typography sx={{ color: 'text.secondary' }}>Справочник услуг: длительность, фиксированная или свободная цена.</Typography></Box>
      </Box>
      <Box className="panel" sx={{ mb: 2 }}>
        <Typography className="panel-label">УСЛУГИ И ЦЕНЫ</Typography>
        <Alert severity="info" sx={{ my: 1 }}>Новая цена и выключение услуги действуют для новых записей. В уже созданных записях остаются название и цена на момент записи. Свободная цена: при записи суммы нет, её вводят в кассе в пределах «от – до».</Alert>
        {services.length === 0 && <Typography sx={{ color: 'text.secondary', my: 1 }}>Услуг пока нет. Добавьте первую — она сразу появится в записи на приём.</Typography>}
        {services.map((service) => <PriceForm key={`${service.id}-${service.priceMode}-${service.priceMinor}-${service.minMinor}-${service.maxMinor}`} service={service} editable={canEditPrices} onSave={(pricing) => onSavePrice(service, pricing)} onToggle={() => onToggleService(service)} />)}
        {canEditPrices && <ServiceForm services={services} onCreate={onCreateService} />}
      </Box>
    </>
  )
}
