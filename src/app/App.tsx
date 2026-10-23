import { Box, CircularProgress } from '@mui/material'
import { ForcePasswordPage } from '../features/auth/ForcePasswordPage'
import { LoginPage } from '../features/auth/LoginPage'
import { sandboxStaff } from './demoData'
import { useSession } from './useSession'
import { Workspace } from './Workspace'

/**
 * До входа видна только страница входа; с временным паролем — только его смена. Рабочее место
 * пересоздаётся для каждого сотрудника.
 */
function App() {
  const session = useSession()
  const { state } = session

  if (state.status === 'loading') {
    return <Box className="login-page"><CircularProgress aria-label="Проверка входа" /></Box>
  }
  if (state.status === 'signedOut') {
    return <LoginPage serverOnline={state.serverOnline} message={state.message} sandboxStaff={sandboxStaff} onSignIn={session.signIn} onDemo={session.enterDemo} onRetry={() => void session.check()} />
  }
  if (state.live && state.staff.mustChangePassword) {
    return <ForcePasswordPage staff={state.staff} onDone={() => void session.check()} onSignOut={() => void session.signOut()} />
  }
  return <Workspace key={`${state.live ? 'live' : 'demo'}-${state.staff.id}`} staff={state.staff} live={state.live} onSignOut={() => void session.signOut()} />
}

export default App
