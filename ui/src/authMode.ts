export type AuthMode = 'mock' | 'keycloak' | 'cognito'

// VITE_AUTH_MODE is the explicit switch; unset falls back to the pre-existing VITE_MOCK-based
// logic so local dev and the self-hosted docker-compose build stay unaffected.
const raw = import.meta.env.VITE_AUTH_MODE as AuthMode | undefined
export const authMode: AuthMode = raw ?? (import.meta.env.VITE_MOCK === 'true' ? 'mock' : 'keycloak')
