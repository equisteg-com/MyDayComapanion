// Minimal GitHub Issues client for alerts and incidents (docs/standards/self-healing.md).
// Token: fine-grained, Issues read/write on this repository only (secret GITHUB_ISSUES_TOKEN).
// Issue text is built by code from counters only — never user data.

export type IssueEnv = { GITHUB_REPO?: string; GITHUB_ISSUES_TOKEN?: string };

async function gh(env: IssueEnv, path: string, method: string, body: unknown) {
  if (!env.GITHUB_REPO || !env.GITHUB_ISSUES_TOKEN) {
    console.log(`github-issues: not configured, skipped ${method} ${path}`);
    return null;
  }
  const res = await fetch(`https://api.github.com/repos/${env.GITHUB_REPO}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${env.GITHUB_ISSUES_TOKEN}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': '<slug>-worker',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`GitHub ${method} ${path} → ${res.status}`);
  return (await res.json()) as { number: number };
}

export async function openIssue(env: IssueEnv, title: string, body: string, labels: string[]): Promise<number | null> {
  const r = await gh(env, '/issues', 'POST', { title, body, labels });
  return r?.number ?? null;
}

/** Adds labels in a separate call so workflows listening for `labeled` (auto-rollback.yml) fire exactly once. */
export async function addLabels(env: IssueEnv, n: number, labels: string[]) {
  await gh(env, `/issues/${n}/labels`, 'POST', { labels });
}

export async function commentIssue(env: IssueEnv, n: number, body: string) {
  await gh(env, `/issues/${n}/comments`, 'POST', { body });
}

export async function closeIssue(env: IssueEnv, n: number, comment: string) {
  await commentIssue(env, n, comment);
  await gh(env, `/issues/${n}`, 'PATCH', { state: 'closed', state_reason: 'completed' });
}
