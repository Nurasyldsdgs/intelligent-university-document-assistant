import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import FullScreenLoader from './FullScreenLoader'

export function PublicOnlyRoute() {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return <FullScreenLoader />
  return isAuthenticated ? <Navigate to="/dashboard" replace /> : <Outlet />
}
