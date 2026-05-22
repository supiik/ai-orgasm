import {
  Configuration,
  SamplesApi,
  type CreateSampleRequest,
  type UpdateSampleRequest,
  type SampleStatus,
} from '@pi2/anchor-client'

async function getAccessToken(): Promise<string> {
  if (import.meta.env.VITE_MOCK === 'true') return ''
  const keycloak = (await import('./keycloak')).default
  if (!keycloak.authenticated) return ''
  try {
    await keycloak.updateToken(60)
  } catch {
    await keycloak.login()
  }
  return keycloak.token ?? ''
}

const config = new Configuration({
  basePath: '',
  accessToken: getAccessToken,
})

const _samples = new SamplesApi(config)

const sampleClient = {
  list:   (page?: number, size?: number, sort?: string, name?: string, status?: SampleStatus) =>
    _samples.findAllSamples(name, status, page, size, sort),
  get:    (id: string) => _samples.getSample(id),
  create: (body: CreateSampleRequest) => _samples.createSample(body),
  update: (id: string, body: UpdateSampleRequest) => _samples.updateSample(id, body),
  delete: (id: string) => _samples.deleteSample(id),
}

export const api = {
  samples: () => sampleClient,
}
