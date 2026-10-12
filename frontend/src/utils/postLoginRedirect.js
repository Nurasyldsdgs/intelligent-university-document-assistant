// Where to go after logging in, e.g. back to an invitation link /join/:code that sent the user to /login.
// Only same-site paths are accepted ("/x", never "//host" or "https://..."), so no link can redirect users away.

const STORAGE_KEY = 'postLoginRedirect'

export function safeInternalPath(path) {
  if (typeof path !== 'string' || !path.startsWith('/') || path.startsWith('//') || path.startsWith('/\\')) return null
  return path === '/login' ? null : path
}

/** The page ProtectedRoute sent to /login (location.state.from), or null. */
export function pathFromLocationState(state) {
  const from = state?.from
  return from ? safeInternalPath(`${from.pathname ?? ''}${from.search ?? ''}`) : null
}

/** For the Google login, which leaves the app: the target survives the round trip in sessionStorage. */
export function rememberPostLoginRedirect(path) {
  const safe = safeInternalPath(path)
  if (!safe) return
  try {
    sessionStorage.setItem(STORAGE_KEY, safe)
  } catch {
    // Storage blocked: the user simply lands on the dashboard.
  }
}

export function takePostLoginRedirect() {
  try {
    const path = sessionStorage.getItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KEY)
    return safeInternalPath(path)
  } catch {
    return null
  }
}
