import { Amplify } from 'aws-amplify'

// Baked in at build time, same convention as VITE_KC_URL in keycloak.ts. Populated from the
// lambda template.yaml stack outputs (UserPoolId / UserPoolClientId) once deployed.
Amplify.configure({
  Auth: {
    Cognito: {
      userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID ?? '',
      userPoolClientId: import.meta.env.VITE_COGNITO_CLIENT_ID ?? '',
    },
  },
})
