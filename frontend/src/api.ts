export type Vehicle = {
  id: string
  nickname: string
}

export type BaselineProgress = {
  sessions: number
  required: number
  ready: boolean
}

export type AlertView = {
  id: string
  metric: string
  metricLabel: string
  pidName: string
  severity: string
  zScore: number
}

export type BaselineView = {
  metric: string
  metricLabel: string
  mean: number
  std: number
  sampleCount: number
  sessionCount: number
}

export type VehicleOverview = {
  vehicleId: string
  nickname: string
  sessionCount: number
  baselineProgress: BaselineProgress
  alerts: AlertView[]
  baselines: BaselineView[]
}

export type VehicleReportBody = {
  summary: string
  urgency: 'low' | 'medium' | 'high'
  likelyCauses: string[]
  suggestedChecks: string[]
  driveAdvice: string
}

export type VehicleReportResponse = {
  degraded: boolean
  message: string | null
  report: VehicleReportBody | null
  input: {
    vehicle: { nickname: string }
    baseline_progress: BaselineProgress
    alerts: Array<{
      metric: string
      severity: string
      z_score: number
      pid: string
    }>
  }
}

export type ImportAccepted = {
  importJobId: string
  vehicleId: string
  status: string
  statusUrl: string
}

export type ImportResult = {
  sessionId: string
  source: string
  sampleCount: number
  driveStartedAt: string | null
  driveEndedAt: string | null
  parquetPath: string
}

export type ImportStatus = {
  importJobId: string
  vehicleId: string
  status: 'running' | 'completed' | 'failed'
  stage: string | null
  error: string | null
  createdAt: string
  completedAt: string | null
  result: ImportResult | null
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
    this.name = 'ApiError'
  }
}

const API_BASE = import.meta.env.VITE_API_BASE ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init)
  if (!response.ok) {
    let message = response.statusText
    if (response.status === 413) {
      message =
        'CSV file is too large. Export a shorter drive from Car Scanner or split the recording.'
    } else {
      try {
        const body = (await response.json()) as {
          detail?: string
          title?: string
          message?: string
        }
        message = body.detail ?? body.message ?? body.title ?? message
      } catch {
        // ignore parse errors
      }
    }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export async function createVehicle(nickname: string): Promise<Vehicle> {
  return request<Vehicle>('/api/v1/vehicles', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  })
}

export async function fetchOverview(vehicleId: string): Promise<VehicleOverview> {
  return request<VehicleOverview>(`/api/v1/vehicles/${vehicleId}/overview`)
}

export async function fetchReport(vehicleId: string): Promise<VehicleReportResponse> {
  return request<VehicleReportResponse>(`/api/v1/vehicles/${vehicleId}/report`)
}

export async function submitImport(vehicleId: string, file: File): Promise<ImportAccepted> {
  const form = new FormData()
  form.append('file', file)
  return request<ImportAccepted>(`/api/v1/vehicles/${vehicleId}/imports`, {
    method: 'POST',
    body: form,
  })
}

export async function fetchImportStatus(
  vehicleId: string,
  importJobId: string,
): Promise<ImportStatus> {
  return request<ImportStatus>(`/api/v1/vehicles/${vehicleId}/imports/${importJobId}`)
}

export async function pollImportUntilDone(
  vehicleId: string,
  importJobId: string,
  onUpdate?: (status: ImportStatus) => void,
  intervalMs = 1000,
  timeoutMs = 120_000,
): Promise<ImportStatus> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const status = await fetchImportStatus(vehicleId, importJobId)
    onUpdate?.(status)
    if (status.status === 'completed' || status.status === 'failed') {
      return status
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
  throw new Error('Import timed out while polling for completion')
}
