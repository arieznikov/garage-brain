import { useState } from 'react'
import {
  ApiError,
  createVehicle,
  fetchOverview,
  fetchReport,
  pollImportUntilDone,
  submitImport,
  type ImportStatus,
  type VehicleOverview,
  type VehicleReportResponse,
} from './api'
import './App.css'

const STORAGE_KEY = 'garagebrain.vehicleId'
const STORAGE_NICKNAME_KEY = 'garagebrain.vehicleNickname'
const CELEBRATION_KEY_PREFIX = 'garagebrain.baselineCelebrated.'

function stageLabel(stage: string | null): string {
  switch (stage) {
    case 'parsing':
      return 'Parsing CSV'
    case 'writing_parquet':
      return 'Writing Parquet archive'
    case 'persisting':
      return 'Computing segments and baselines'
    default:
      return stage ?? 'Starting'
  }
}

function formatNumber(value: number, digits = 3): string {
  if (!Number.isFinite(value)) {
    return '—'
  }
  return value.toLocaleString(undefined, {
    maximumFractionDigits: digits,
    minimumFractionDigits: 0,
  })
}

const TRACKED_METRICS = [
  { id: 'coolant_warmup_slope', label: 'Coolant warmup slope' },
  { id: 'stft_drift', label: 'STFT drift (cruise)' },
  { id: 'ltft_drift', label: 'LTFT drift (cruise)' },
  { id: 'battery_at_crank', label: 'Battery at crank' },
  { id: 'idle_rpm_variance', label: 'Idle RPM variance' },
] as const

function metricIsTracking(metricId: string, baselines: VehicleOverview['baselines']): boolean {
  return baselines.some((b) => b.metric === metricId && b.sampleCount > 0)
}

function urgencyClass(urgency: string): string {
  switch (urgency) {
    case 'high':
      return 'urgency-high'
    case 'medium':
      return 'urgency-medium'
    default:
      return 'urgency-low'
  }
}

function App() {
  const [vehicleId, setVehicleId] = useState(() => localStorage.getItem(STORAGE_KEY) ?? '')
  const [nickname, setNickname] = useState(
    () => localStorage.getItem(STORAGE_NICKNAME_KEY) ?? '',
  )
  const [overview, setOverview] = useState<VehicleOverview | null>(null)
  const [importStatus, setImportStatus] = useState<ImportStatus | null>(null)
  const [report, setReport] = useState<VehicleReportResponse | null>(null)
  const [reportBusy, setReportBusy] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showBaselineCelebration, setShowBaselineCelebration] = useState(false)

  async function loadOverview(id: string) {
    const data = await fetchOverview(id)
    setOverview(data)
    setReport(null)
    setNickname(data.nickname)
    localStorage.setItem(STORAGE_NICKNAME_KEY, data.nickname)
    const celebrated = localStorage.getItem(CELEBRATION_KEY_PREFIX + id) === '1'
    setShowBaselineCelebration(data.baselineProgress.ready && !celebrated)
  }

  function dismissBaselineCelebration() {
    if (vehicleId) {
      localStorage.setItem(CELEBRATION_KEY_PREFIX + vehicleId, '1')
    }
    setShowBaselineCelebration(false)
  }

  function scrollToInsights() {
    document.getElementById('insights-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  async function handleGenerateReport() {
    if (!vehicleId) {
      return
    }
    setError(null)
    setReportBusy(true)
    try {
      const data = await fetchReport(vehicleId)
      setReport(data)
    } catch (err) {
      setReport(null)
      setError(err instanceof Error ? err.message : 'Failed to generate report')
    } finally {
      setReportBusy(false)
    }
  }

  async function handleCreateVehicle(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const trimmed = nickname.trim()
      if (!trimmed) {
        throw new Error('Enter a vehicle nickname')
      }
      const vehicle = await createVehicle(trimmed)
      setVehicleId(vehicle.id)
      localStorage.setItem(STORAGE_KEY, vehicle.id)
      localStorage.setItem(STORAGE_NICKNAME_KEY, vehicle.nickname)
      await loadOverview(vehicle.id)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create vehicle')
    } finally {
      setBusy(false)
    }
  }

  async function handleUseExistingVehicle(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const id = vehicleId.trim()
      if (!id) {
        throw new Error('Paste a vehicle ID')
      }
      await loadOverview(id)
      localStorage.setItem(STORAGE_KEY, id)
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setError('Vehicle not found. Create a new one or check the ID.')
      } else {
        setError(err instanceof Error ? err.message : 'Failed to load vehicle')
      }
      setOverview(null)
    } finally {
      setBusy(false)
    }
  }

  async function handleUpload(file: File) {
    if (!vehicleId) {
      setError('Select or create a vehicle first')
      return
    }
    setError(null)
    setBusy(true)
    setImportStatus(null)
    try {
      const accepted = await submitImport(vehicleId, file)
      const finalStatus = await pollImportUntilDone(
        vehicleId,
        accepted.importJobId,
        setImportStatus,
      )
      setImportStatus(finalStatus)
      if (finalStatus.status === 'failed') {
        setError(finalStatus.error ?? 'Import failed')
      } else {
        await loadOverview(vehicleId)
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('This CSV was already imported for this vehicle.')
      } else if (err instanceof ApiError && err.status === 413) {
        setError(err.message)
      } else if (err instanceof Error && err.message.includes('timed out')) {
        setError(
          'Import is taking longer than expected. Check import status below or try again in a moment.',
        )
      } else {
        setError(err instanceof Error ? err.message : 'Upload failed')
      }
    } finally {
      setBusy(false)
    }
  }

  const progress = overview?.baselineProgress
  const progressPct = progress
    ? Math.min(100, Math.round((progress.sessions / progress.required) * 100))
    : 0
  const showOnboarding = !overview || overview.sessionCount === 0

  return (
    <div className="app">
      <header className="hero">
        <p className="eyebrow">Garage Brain</p>
        <h1>SCAN / TREND</h1>
        <p className="lede">
          Import Car Scanner CSV #2 exports, build per-vehicle baselines over multiple drives,
          and surface early trend warnings.
        </p>
      </header>

      {showOnboarding && (
        <section className="onboarding" aria-label="Getting started">
          <h2 className="onboarding-title">Get started in three steps</h2>
          <ol className="onboarding-steps">
            <li className="onboarding-step">
              <span className="step-num">1</span>
              <div>
                <strong>Export from Car Scanner</strong>
                <p className="muted">
                  Data recording → Share → <strong>CSV #2</strong>. One file per drive works best.
                </p>
              </div>
            </li>
            <li className={`onboarding-step ${overview ? 'step-done' : 'step-current'}`}>
              <span className="step-num">2</span>
              <div>
                <strong>Create your vehicle and upload</strong>
                <p className="muted">
                  {overview
                    ? 'Upload your first CSV below — we parse PIDs and start your baseline.'
                    : 'Pick a nickname, then choose your first CSV export.'}
                </p>
              </div>
            </li>
            <li className={`onboarding-step ${progress?.ready ? 'step-done' : ''}`}>
              <span className="step-num">3</span>
              <div>
                <strong>Import {progress?.required ?? 5} drives to unlock alerts</strong>
                <p className="muted">
                  {overview
                    ? `${progress?.sessions ?? 0} / ${progress?.required ?? 5} drives logged — trend alerts need a stable baseline first.`
                    : 'Each new drive refines what “normal” looks like for your car.'}
                </p>
              </div>
            </li>
          </ol>
        </section>
      )}

      {showBaselineCelebration && overview?.baselineProgress.ready && (
        <div className="banner banner-success" role="status">
          <div className="celebration-copy">
            <strong>Baseline ready.</strong> Your car has enough drive history for trend alerts.
          </div>
          <div className="celebration-actions">
            <button type="button" className="secondary" onClick={scrollToInsights}>
              Review insights
            </button>
            <button
              type="button"
              disabled={reportBusy}
              onClick={() => void handleGenerateReport()}
            >
              {reportBusy ? 'Generating…' : 'Generate report'}
            </button>
            <button type="button" className="secondary" onClick={dismissBaselineCelebration}>
              Dismiss
            </button>
          </div>
        </div>
      )}

      {error && (
        <div className="banner banner-error" role="alert">
          {error}
        </div>
      )}

      <section className="panel">
        <h2>Vehicle</h2>
        {!overview ? (
          <div className="vehicle-forms">
            <form className="stack" onSubmit={handleCreateVehicle}>
              <label>
                Nickname
                <input
                  value={nickname}
                  onChange={(event) => setNickname(event.target.value)}
                  placeholder="Daily Civic"
                  disabled={busy}
                />
              </label>
              <button type="submit" disabled={busy}>
                Create vehicle
              </button>
            </form>
            <div className="divider">or</div>
            <form className="stack" onSubmit={handleUseExistingVehicle}>
              <label>
                Existing vehicle ID
                <input
                  value={vehicleId}
                  onChange={(event) => setVehicleId(event.target.value)}
                  placeholder="uuid"
                  disabled={busy}
                />
              </label>
              <button type="submit" className="secondary" disabled={busy}>
                Load vehicle
              </button>
            </form>
          </div>
        ) : (
          <div className="vehicle-summary">
            <div>
              <p className="label">Nickname</p>
              <p className="value">{overview.nickname}</p>
            </div>
            <div>
              <p className="label">Sessions imported</p>
              <p className="value">{overview.sessionCount}</p>
            </div>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() => {
                setOverview(null)
                setImportStatus(null)
                setReport(null)
                setError(null)
                setShowBaselineCelebration(false)
              }}
            >
              Switch vehicle
            </button>
            <details className="advanced-disclosure">
              <summary>Advanced</summary>
              <div className="advanced-body">
                <p className="label">Vehicle ID</p>
                <code>{overview.vehicleId}</code>
                <p className="muted">
                  Use this UUID to reload this vehicle on another device or browser.
                </p>
              </div>
            </details>
          </div>
        )}
      </section>

      {overview && (
        <>
          <section className="panel">
            <h2>Baseline progress</h2>
            {!progress?.ready && (
              <p className="muted">
                Trend alerts compare each new drive to your personal baseline — we need enough
                sessions before calling a drift significant.
              </p>
            )}
            {progress?.ready ? (
              <p className="muted">
                Baseline ready — alerts are active for supported metrics.
              </p>
            ) : (
              <p className="muted">
                Building baseline — import {progress?.required ?? 5} drives before trend alerts
                unlock.
              </p>
            )}
            <div
              className="progress-track"
              role="progressbar"
              aria-valuenow={progress?.sessions ?? 0}
              aria-valuemin={0}
              aria-valuemax={progress?.required ?? 5}
              aria-label="Baseline drive progress"
            >
              <div className="progress-fill" style={{ width: `${progressPct}%` }} />
            </div>
            <p className="progress-caption">
              {progress?.sessions ?? 0} / {progress?.required ?? 5} drives
            </p>
            {!progress?.ready && (
              <ul className="metric-checklist">
                {TRACKED_METRICS.map((metric) => {
                  const tracking = metricIsTracking(metric.id, overview.baselines)
                  return (
                    <li key={metric.id} className={tracking ? 'metric-tracking' : 'metric-waiting'}>
                      <span className="metric-status" aria-hidden="true">
                        {tracking ? '●' : '○'}
                      </span>
                      <span>{metric.label}</span>
                      <span className="metric-state">{tracking ? 'Tracking' : 'Waiting for data'}</span>
                    </li>
                  )
                })}
              </ul>
            )}
          </section>

          <section className="panel">
            <h2>Import CSV</h2>
            <p className="muted">
              Export as <strong>CSV #2</strong> from Car Scanner → Data recording → Share.
            </p>
            <label className={`upload ${busy ? 'disabled' : ''}`}>
              <input
                type="file"
                accept=".csv,text/csv"
                disabled={busy}
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  if (file) {
                    void handleUpload(file)
                  }
                  event.target.value = ''
                }}
              />
              <span>{busy ? 'Working…' : 'Choose Car Scanner CSV'}</span>
            </label>
            {importStatus && (
              <div
                className={`import-status status-${importStatus.status}`}
                role="status"
                aria-live="polite"
                aria-atomic="true"
              >
                <p className="label">Import {importStatus.status}</p>
                {importStatus.status === 'running' && (
                  <p>{stageLabel(importStatus.stage)}</p>
                )}
                {importStatus.status === 'completed' && importStatus.result && (
                  <p>
                    Imported {importStatus.result.sampleCount.toLocaleString()} samples from{' '}
                    {importStatus.result.source}
                  </p>
                )}
                {importStatus.status === 'failed' && (
                  <p>{importStatus.error ?? 'Import failed'}</p>
                )}
              </div>
            )}
          </section>

          <section className="panel insights-panel" id="insights-panel">
            <h2>Insights</h2>
            <p className="muted">
              Active trend alerts and rolling baselines for this vehicle.
            </p>

            <div className="insights-block">
              <h3 className="insights-subhead">Alerts</h3>
              {overview.alerts.length === 0 ? (
                <p className="muted">
                  {progress?.ready
                    ? 'No active trend alerts.'
                    : 'Alerts unlock after the baseline gate.'}
                </p>
              ) : (
                <ul className="alert-list">
                  {overview.alerts.map((alert) => (
                    <li key={alert.id} className="alert-item">
                      <div>
                        <strong>{alert.metricLabel}</strong>
                        <span className="pill">{alert.severity}</span>
                      </div>
                      <p className="muted">
                        z = {formatNumber(alert.zScore, 2)} · PID {alert.pidName}
                      </p>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="insights-block">
              <h3 className="insights-subhead">Trend baselines</h3>
              {overview.baselines.length === 0 ? (
                <p className="muted">Import a drive to start building baselines.</p>
              ) : (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th scope="col">Metric</th>
                        <th scope="col">Mean</th>
                        <th scope="col">Std dev</th>
                        <th scope="col">Sessions in window</th>
                      </tr>
                    </thead>
                    <tbody>
                      {overview.baselines.map((baseline) => (
                        <tr key={baseline.metric}>
                          <td>{baseline.metricLabel}</td>
                          <td>{formatNumber(baseline.mean)}</td>
                          <td>{formatNumber(baseline.std)}</td>
                          <td>{baseline.sampleCount}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </section>

          <section className="panel report-panel">
            <div className="report-header">
              <div>
                <h2>BYOM narrative</h2>
                <p className="muted">
                  Structured alerts and baseline progress are sent to your local LLM — never raw CSV.
                </p>
              </div>
              <button
                type="button"
                disabled={busy || reportBusy}
                onClick={() => void handleGenerateReport()}
              >
                {reportBusy ? 'Generating…' : 'Generate report'}
              </button>
            </div>

            {report?.degraded && (
              <div className="banner banner-warn" role="status">
                <strong>Degraded mode.</strong> {report.message}
              </div>
            )}

            {report && !report.degraded && report.report && (
              <article className="report-body">
                <div className="report-meta">
                  <span className={`pill urgency-pill ${urgencyClass(report.report.urgency)}`}>
                    {report.report.urgency} urgency
                  </span>
                </div>
                <p className="report-summary">{report.report.summary}</p>
                <div className="report-grid">
                  <div>
                    <h3>Likely causes</h3>
                    <ul>
                      {report.report.likelyCauses.map((item) => (
                        <li key={item}>{item}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <h3>Suggested checks</h3>
                    <ul>
                      {report.report.suggestedChecks.map((item) => (
                        <li key={item}>{item}</li>
                      ))}
                    </ul>
                  </div>
                </div>
                <div className="drive-advice">
                  <h3>Drive advice</h3>
                  <p>{report.report.driveAdvice}</p>
                </div>
              </article>
            )}

            {!report && !reportBusy && (
              <p className="muted">
                Point the API at Ollama or LM Studio (see README), then generate a narrative from
                your trend data.
              </p>
            )}
          </section>
        </>
      )}
    </div>
  )
}

export default App
