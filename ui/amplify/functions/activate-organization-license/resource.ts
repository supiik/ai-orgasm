import { defineFunction } from '@aws-amplify/backend'

export const activateOrganizationLicenseFn = defineFunction({
  name: 'activate-organization-license',
  entry: './handler.ts',
})
