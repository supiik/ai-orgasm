import { defineFunction } from '@aws-amplify/backend'

export const adminExportOrganizationFn = defineFunction({
  name: 'admin-export-organization',
  entry: './handler.ts',
  // Reads eight whole tenant partitions in one go; the default 3 s is tight for a large tenant.
  timeoutSeconds: 30,
})
