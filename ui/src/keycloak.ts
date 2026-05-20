import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KC_URL ?? 'http://localhost:8180',
  realm: 'proxima-service',
  clientId: 'proxima-service-ui',
})

export default keycloak
