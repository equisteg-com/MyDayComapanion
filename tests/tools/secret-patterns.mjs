// One list of what counts as a secret file or a secret value, shared by:
//   .claude/hooks/guard-secrets.mjs      (stops Claude reading, printing or sending secrets)
//   tests/tools/check-forbidden-files.mjs (pre-commit hook and CI: stops secrets being committed)
// Keep it short and precise: a false block costs a founder time, a missed one costs a leak.

// Files that hold secrets. Example/template files (".env.example", ".dev.vars.example") are allowed.
export const SECRET_FILE_PATTERNS = [
  /(^|[\\/])\.env(\.[\w.-]+)?$/i, // .env, .env.local, .env.production …
  /(^|[\\/])\.dev\.vars(\.[\w.-]+)?$/i, // Wrangler local secrets
  /\.(pem|key|p8|p12|pfx|jks|keystore|mobileprovision)$/i, // private keys, signing keys
  /(^|[\\/])id_(rsa|dsa|ecdsa|ed25519)(\.pub)?$/i, // SSH keys
  /(^|[\\/])\.ssh([\\/]|$)/i,
  /(^|[\\/])\.aws[\\/]credentials$/i,
  /(^|[\\/])\.git-credentials$/i,
  /(^|[\\/])\.netrc$/i,
  /(^|[\\/])\.config[\\/]gh[\\/]hosts\.ya?ml$/i, // GitHub CLI token
  /(^|[\\/])\.credentials\.json$/i, // Claude Code login
  /(^|[\\/])\.wrangler[\\/]config[\\/]/i, // Wrangler login
  /service[-_]?account[^\\/]*\.json$/i, // Google / Play service-account keys
];

const ALLOWED_TEMPLATE = /\.(example|sample|template)$/i;

export function isSecretPath(p) {
  if (!p || typeof p !== 'string') return false;
  const clean = p.trim().replace(/^["']|["']$/g, '');
  if (ALLOWED_TEMPLATE.test(clean)) return false;
  return SECRET_FILE_PATTERNS.some((re) => re.test(clean));
}

// Values that look like live credentials. Used on commands, URLs and staged file contents.
export const SECRET_VALUE_PATTERNS = [
  { name: 'GitHub token', re: /\b(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{30,}\b|\bgithub_pat_[A-Za-z0-9_]{40,}\b/ },
  { name: 'Stripe live key', re: /\b(sk|rk)_live_[A-Za-z0-9]{16,}\b/ },
  { name: 'Razorpay live key', re: /\brzp_live_[A-Za-z0-9]{10,}\b/ },
  { name: 'AWS access key', re: /\bAKIA[0-9A-Z]{16}\b/ },
  { name: 'Google API key', re: /\bAIza[0-9A-Za-z_-]{35}\b/ },
  { name: 'Anthropic key', re: /\bsk-ant-[A-Za-z0-9_-]{20,}\b/ },
  { name: 'OpenAI-style key', re: /\bsk-(proj-)?[A-Za-z0-9]{32,}\b/ },
  { name: 'Slack token', re: /\bxox[abprs]-[A-Za-z0-9-]{10,}\b/ },
  { name: 'Resend key', re: /\bre_[A-Za-z0-9]{8,}_[A-Za-z0-9]{16,}\b/ },
  { name: 'Private key block', re: /-----BEGIN (RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----/ },
];

export function findSecretValue(text) {
  if (!text || typeof text !== 'string') return null;
  for (const { name, re } of SECRET_VALUE_PATTERNS) if (re.test(text)) return name;
  return null;
}
