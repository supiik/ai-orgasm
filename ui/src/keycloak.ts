import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KC_URL ?? 'http://localhost:8180',
  realm: 'proxima-sal',
  clientId: 'proxima-sal-ui',
})

export default keycloak
