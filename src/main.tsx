import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './app/App.tsx'
import { ClinicThemeProvider } from './app/theme.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ClinicThemeProvider>
      <App />
    </ClinicThemeProvider>
  </StrictMode>,
)
