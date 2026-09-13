import { CognitoIdentityProviderClient, ListUsersCommand } from '@aws-sdk/client-cognito-identity-provider'

// One client per cold start, like lib/dynamodb.ts. Only the admin-* functions that need it are
// granted `cognito-idp:ListUsers` on the pool (functions.ts `userPoolActions`); for every other
// function this module is simply never imported.
const client = new CognitoIdentityProviderClient({})

const USER_POOL_ID = () => process.env.COGNITO_USER_POOL_ID ?? ''

/**
 * Resolves the Cognito `sub` of an already signed-up user by email, so an administrator can
 * link a Contributor to an existing account without waiting for that user to go through the
 * link flow. Undefined when nobody has signed up with that address (yet).
 */
export async function findCognitoSubByEmail(email: string): Promise<string | undefined> {
  // Cognito's filter syntax takes a double-quoted string literal; the email is validated by the
  // caller (services/admin.ts) but escape quotes/backslashes anyway so it can never break out.
  const literal = email.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
  const res = await client.send(
    new ListUsersCommand({ UserPoolId: USER_POOL_ID(), Filter: `email = "${literal}"`, Limit: 1 }),
  )
  const user = res.Users?.[0]
  return user?.Attributes?.find((a) => a.Name === 'sub')?.Value
}
