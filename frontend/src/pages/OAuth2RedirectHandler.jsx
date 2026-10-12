import { useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import FullScreenLoader from '../components/FullScreenLoader'

export default function OAuth2RedirectHandler() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { loginWithToken } = useAuth()
  // StrictMode runs effects twice in dev; make sure the token is processed only once.
  const handled = useRef(false)

  useEffect(() => {
    if (handled.current) return
    handled.current = true

    const token = searchParams.get('token')
    const error = searchParams.get('error')

    if (!token) {
      navigate('/login', { replace: true, state: { oauthError: error ?? 'oauth2_failed' } })
      return
    }

    loginWithToken(token)
      .then(() => navigate('/dashboard', { replace: true }))
      .catch(() => navigate('/login', { replace: true, state: { oauthError: 'profile_failed' } }))
  }, [searchParams, navigate, loginWithToken])

  return <FullScreenLoader text="Завершаем вход через Google…" />
}
