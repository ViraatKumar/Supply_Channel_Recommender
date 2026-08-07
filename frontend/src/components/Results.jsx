import { inr } from '../api.js'

const FACTOR_LABELS = {
  costEfficiency: 'Cost efficiency',
  volumeFit: 'Volume fit',
  quality: 'Quality',
  speed: 'Speed',
  skillMatch: 'Skill match',
}

const BINDING_LABELS = {
  DEMAND: 'covers your full target',
  VOLUME: 'capped by throughput',
  BUDGET: 'capped by budget',
}

function ChannelCard({ rec, weights }) {
  return (
    <article className="card rec">
      <header className="rec-head">
        <span className="rank">{rec.rank}</span>
        <div className="rec-title">
          <h3>{rec.channelName}</h3>
          <span className="pill">{rec.channelType.replace('_', ' ').toLowerCase()}</span>
        </div>
        <div className="score" title="Weighted score out of 100">
          <strong>{rec.score.toFixed(1)}</strong>
          <span>/ 100</span>
        </div>
      </header>

      <div className="metrics">
        <div>
          <span className="metric-label">Expected applicants</span>
          <strong>{rec.expectedApplicants}</strong>
          <span className="sub">{BINDING_LABELS[rec.bindingConstraint]}</span>
        </div>
        <div>
          <span className="metric-label">Expected spend</span>
          <strong>{inr.format(rec.expectedCost)}</strong>
          <span className="sub">{inr.format(rec.costPerApplicant)} / applicant</span>
        </div>
        <div>
          <span className="metric-label">Quality</span>
          <strong>{rec.qualityEstimate}/10</strong>
          <span className="sub">editorial estimate</span>
        </div>
        <div>
          <span className="metric-label">Lead time</span>
          <strong>{rec.leadTimeDays}d</strong>
          <span className="sub">before first applicant</span>
        </div>
      </div>

      <p className="reason">{rec.reason}</p>

      <details className="factors">
        <summary>Score breakdown</summary>
        <ul>
          {Object.entries(rec.factorScores).map(([factor, value]) => (
            <li key={factor}>
              <span className="factor-name">
                {FACTOR_LABELS[factor]}
                <span className="sub"> · {Math.round(weights[factor] * 100)}% weight</span>
              </span>
              <span className="bar">
                <span className="bar-fill" style={{ width: `${value * 100}%` }} />
              </span>
              <span className="factor-value">{Math.round(value * 100)}%</span>
            </li>
          ))}
        </ul>
      </details>

      {rec.limitations.length > 0 && (
        <ul className="limitations">
          {rec.limitations.map((l) => (
            <li key={l}>{l}</li>
          ))}
        </ul>
      )}
    </article>
  )
}

export default function Results({ result }) {
  const { recommendations, excluded, weightProfile, weights, weightProfileRationale } = result

  return (
    <section className="results">
      <div className="results-head">
        <h2>
          {recommendations.length} channel{recommendations.length === 1 ? '' : 's'} recommended
        </h2>
        <p className="hint">
          Weighting: <strong>{weightProfile.replace('_', ' ').toLowerCase()}</strong> —{' '}
          {weightProfileRationale}
        </p>
      </div>

      {recommendations.length === 0 && (
        <p className="notice notice-warn">
          Nothing in the catalogue can run this campaign. Every channel was ruled out below —
          the usual fixes are a bigger budget or a longer timeline.
        </p>
      )}

      {recommendations.map((rec) => (
        <ChannelCard key={rec.channelId} rec={rec} weights={weights} />
      ))}

      {excluded.length > 0 && (
        <details className="card excluded">
          <summary>
            {excluded.length} channel{excluded.length === 1 ? '' : 's'} ruled out
          </summary>
          <ul>
            {excluded.map((e) => (
              <li key={e.channelId}>
                <div>
                  <strong>{e.channelName}</strong>
                  <span className="pill pill-rule">{e.rule.replace('_', ' ').toLowerCase()}</span>
                </div>
                <p>{e.reason}</p>
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}
