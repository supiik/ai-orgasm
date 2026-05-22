import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KC_URL ?? 'http://localhost:8180',
  realm: 'anchor',
  clientId: 'anchor-ui',
})

export default keycloak
