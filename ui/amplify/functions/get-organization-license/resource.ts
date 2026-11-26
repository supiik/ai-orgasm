import { defineFunction } from '@aws-amplify/backend'

export const getOrganizationLicenseFn = defineFunction({
  name: 'get-organization-license',
  entry: './handler.ts',
})
