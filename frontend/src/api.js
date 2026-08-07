const BASE = import.meta.env.VITE_API_BASE_URL ?? '';

export async function getRecommendations(campaign) {
  const response = await fetch(`${BASE}/api/recommendations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(campaign),
  })
  if (!response.ok) {
    throw new Error(
      response.status === 400
        ? 'Some of those values are out of range — check the numbers and try again.'
        : `The server returned ${response.status}.`,
    )
  }
  return response.json()
}

/**
 * Never throws. The parser is an accelerator: if it is down, unconfigured, or slow, the caller
 * gets `{ parsed: false }` and the manual form carries on working.
 */
export async function parseBrief(brief) {
  try {
    const response = await fetch(`${BASE}/api/parse-brief`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ brief }),
    })
    if (!response.ok) {
      return { parsed: false, message: 'The parser is unavailable. Fill the form in manually.' }
    }
    return response.json()
  } catch {
    return { parsed: false, message: 'Could not reach the parser. Fill the form in manually.' }
  }
}

export const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})
