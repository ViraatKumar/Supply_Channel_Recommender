import { useState } from 'react'
import { parseBrief } from '../api.js'

const SAMPLE = `We need to hire around 25 backend engineers for our Bengaluru office over the
next 6 weeks. Budget is about 8 lakh. Mid-level, mostly Java and Go. Onsite only.`

/**
 * The AI entry point. Everything it produces lands in the form for the user to check — nothing
 * here reaches the scoring engine directly.
 */
export default function BriefBox({ onDraft }) {
  const [brief, setBrief] = useState('')
  const [status, setStatus] = useState(null)
  const [busy, setBusy] = useState(false)

  async function handleParse() {
    setBusy(true)
    setStatus(null)
    const result = await parseBrief(brief)
    setBusy(false)

    if (!result.parsed) {
      setStatus({ tone: 'warn', text: result.message })
      return
    }
    onDraft(result.draft)
    const missing = result.draft.missingFields ?? []
    setStatus({
      tone: 'ok',
      text: missing.length
        ? `Form pre-filled. Still needed: ${missing.join(', ')}.`
        : 'Form pre-filled. Review the values before running.',
    })
  }

  return (
    <section className="card brief">
      <div className="card-head">
        <h2>Paste a hiring brief</h2>
        <span className="pill pill-ai">optional · AI</span>
      </div>
      <p className="hint">
        Fills the form below for you to check and edit. The ranking itself never uses the model —
        if parsing is unavailable, just fill the form in by hand.
      </p>
      <textarea
        rows={5}
        value={brief}
        placeholder={SAMPLE}
        onChange={(e) => setBrief(e.target.value)}
      />
      <div className="brief-actions">
        <button type="button" className="secondary" onClick={handleParse} disabled={busy || !brief.trim()}>
          {busy ? 'Parsing…' : 'Parse with AI'}
        </button>
        <button type="button" className="link" onClick={() => setBrief(SAMPLE)}>
          Use the sample brief
        </button>
      </div>
      {status && <p className={`notice notice-${status.tone}`}>{status.text}</p>}
    </section>
  )
}
