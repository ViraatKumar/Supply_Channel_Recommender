const SENIORITIES = ['ENTRY', 'MID', 'SENIOR', 'EXECUTIVE']

const PROFILES = [
  ['', 'Auto (by seniority and headcount)'],
  ['BALANCED', 'Balanced — cost leads'],
  ['QUALITY_FIRST', 'Quality first'],
  ['VOLUME_FIRST', 'Volume first'],
]

export default function CampaignForm({ form, setField, onSubmit, busy }) {
  return (
    <form className="card" onSubmit={onSubmit}>
      <div className="card-head">
        <h2>Campaign</h2>
      </div>

      <div className="grid">
        <label className="span2">
          Job title
          <input required value={form.jobTitle} onChange={(e) => setField('jobTitle', e.target.value)} />
        </label>

        <label>
          Location
          <input required value={form.location} onChange={(e) => setField('location', e.target.value)} />
        </label>

        <label>
          Seniority
          <select value={form.seniority} onChange={(e) => setField('seniority', e.target.value)}>
            {SENIORITIES.map((s) => (
              <option key={s} value={s}>
                {s[0] + s.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </label>

        <label>
          Applicants needed
          <input
            type="number"
            min="1"
            required
            value={form.applicantsNeeded}
            onChange={(e) => setField('applicantsNeeded', e.target.value)}
          />
        </label>

        <label>
          Budget (₹)
          <input
            type="number"
            min="1"
            required
            value={form.budget}
            onChange={(e) => setField('budget', e.target.value)}
          />
        </label>

        <label>
          Timeline (days)
          <input
            type="number"
            min="1"
            required
            value={form.timelineDays}
            onChange={(e) => setField('timelineDays', e.target.value)}
          />
        </label>

        <label>
          Skills <span className="sub">comma separated</span>
          <input
            value={form.skills}
            placeholder="engineering, design"
            onChange={(e) => setField('skills', e.target.value)}
          />
        </label>

        <label className="span2">
          Additional constraints <span className="sub">free text, shown back in limitations</span>
          <input
            value={form.additionalConstraints}
            onChange={(e) => setField('additionalConstraints', e.target.value)}
          />
        </label>

        <label>
          Weighting
          <select
            value={form.weightProfileOverride}
            onChange={(e) => setField('weightProfileOverride', e.target.value)}
          >
            {PROFILES.map(([value, text]) => (
              <option key={value} value={value}>
                {text}
              </option>
            ))}
          </select>
        </label>

        <label className="check">
          <input
            type="checkbox"
            checked={form.remoteOk}
            onChange={(e) => setField('remoteOk', e.target.checked)}
          />
          Role is remote — ignore location limits
        </label>
      </div>

      <button type="submit" disabled={busy}>
        {busy ? 'Ranking…' : 'Rank channels'}
      </button>
    </form>
  )
}
