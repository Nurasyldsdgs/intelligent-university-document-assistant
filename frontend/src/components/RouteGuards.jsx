import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import FullScreenLoader from './FullScreenLoader'
import { pathFromLocationState } from '../utils/postLoginRedirect'

// Once logged in (password, email verification), go back to the page that required login, e.g. /join/:code.
export function PublicOnlyRoute() {
  const { isAuthenticated, loading } = useAuth()
  const location = useLocation()
  if (loading) return <FullScreenLoader />
  return isAuthenticated
    ? <Navigate to={pathFromLocationState(location.state) ?? '/dashboard'} replace />
    : <Outlet />
}
