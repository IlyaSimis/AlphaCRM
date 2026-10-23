import { CssBaseline } from '@mui/material'
import { createTheme, ThemeProvider } from '@mui/material/styles'
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { readStored, storageKeys, writeStored } from '../shared/storage'

export type ColorMode = 'light' | 'dark'

const ColorModeContext = createContext<{ mode: ColorMode; toggle: () => void } | null>(null)

function initialMode(): ColorMode {
  const stored = readStored(storageKeys.colorMode, (value) => value)
  if (stored === 'light' || stored === 'dark') return stored
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function ClinicThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ColorMode>(initialMode)

  useEffect(() => {
    document.documentElement.dataset.theme = mode
    document.documentElement.style.colorScheme = mode
    writeStored(storageKeys.colorMode, mode)
  }, [mode])

  const theme = useMemo(
    () =>
      createTheme({
        palette: {
          mode,
          primary: { main: mode === 'dark' ? '#67c9b2' : '#176b5b' },
          success: { main: mode === 'dark' ? '#69c7a9' : '#2f8068' },
          warning: { main: mode === 'dark' ? '#e1b35f' : '#b47a24' },
          text:
            mode === 'dark'
              ? { primary: '#e7f1ee', secondary: '#a8bbb6' }
              : { primary: '#19312d', secondary: '#687b77' },
          background:
            mode === 'dark'
              ? { default: '#0d1514', paper: '#141e1c' }
              : { default: '#f5f7f6', paper: '#ffffff' },
          divider: mode === 'dark' ? '#2a3b38' : '#e1e8e5',
        },
        shape: { borderRadius: 10 },
        typography: {
          fontFamily: 'Inter, "Segoe UI", Arial, sans-serif',
          button: { textTransform: 'none', fontWeight: 700 },
        },
        components: {
          MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
          MuiButton: { defaultProps: { disableElevation: true } },
        },
      }),
    [mode],
  )

  const value = useMemo(
    () => ({ mode, toggle: () => setMode((current) => (current === 'light' ? 'dark' : 'light')) }),
    [mode],
  )

  return (
    <ColorModeContext.Provider value={value}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  )
}

// oxlint-disable-next-line react/only-export-components
export function useColorMode() {
  const value = useContext(ColorModeContext)
  if (!value) throw new Error('useColorMode must be used inside ClinicThemeProvider')
  return value
}
