import { useState } from 'react'
import { getRecommendations } from './api.js'
import BriefBox from './components/BriefBox.jsx'
import CampaignForm from './components/CampaignForm.jsx'
import Results from './components/Results.jsx'

const INITIAL = {
  jobTitle: 'Backend Engineer',
  location: 'Bengaluru',
  applicantsNeeded: '40',
  budget: '60000',
  timelineDays: '21',
  skills: 'engineering',
  seniority: 'MID',
  remoteOk: false,
  additionalConstraints: '',
  weightProfileOverride: '',
}

/** Form strings → the API's typed shape. Kept here so the form stays plain controlled inputs. */
function toCampaignRequest(form) {
  return {
    jobTitle: form.jobTitle.trim(),
    location: form.location.trim(),
    applicantsNeeded: Number(form.applicantsNeeded),
    budget: Number(form.budget),
    timelineDays: Number(form.timelineDays),
    skills: form.skills
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
    seniority: form.seniority,
    remoteOk: form.remoteOk,
    additionalConstraints: form.additionalConstraints.trim() || null,
    weightProfileOverride: form.weightProfileOverride || null,
  }
}

export default function App() {
  const [form, setForm] = useState(INITIAL)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const setField = (key, value) => setForm((f) => ({ ...f, [key]: value }))

  /** Only overwrite fields the model actually found — a null must not wipe what the user typed. */
  function applyDraft(draft) {
    setForm((f) => ({
      ...f,
      jobTitle: draft.jobTitle ?? f.jobTitle,
      location: draft.location ?? f.location,
      applicantsNeeded: draft.applicantsNeeded != null ? String(draft.applicantsNeeded) : f.applicantsNeeded,
      budget: draft.budget != null ? String(draft.budget) : f.budget,
      timelineDays: draft.timelineDays != null ? String(draft.timelineDays) : f.timelineDays,
      skills: draft.skills?.length ? draft.skills.join(', ') : f.skills,
      seniority: draft.seniority ?? f.seniority,
      remoteOk: draft.remoteOk ?? f.remoteOk,
      additionalConstraints: draft.additionalConstraints ?? f.additionalConstraints,
    }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setResult(await getRecommendations(toCampaignRequest(form)))
    } catch (e) {
      setError(e.message)
      setResult(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page">
      <header className="masthead">
        <h1>Supply Channel Recommender</h1>
        <p>
          Rank applicant supply channels for a hiring campaign. Ranking is deterministic — the same
          campaign always produces the same list, and every channel that is ruled out says why.
        </p>
      </header>

      <div className="layout">
        <div className="controls">
          <BriefBox onDraft={applyDraft} />
          <CampaignForm form={form} setField={setField} onSubmit={handleSubmit} busy={busy} />
        </div>

        <div className="output">
          {error && <p className="notice notice-error">{error}</p>}
          {result ? (
            <Results result={result} />
          ) : (
            !error && (
              <p className="placeholder">
                Fill in the campaign and hit <strong>Rank channels</strong>.
              </p>
            )
          )}
        </div>
      </div>
    </div>
  )
}
