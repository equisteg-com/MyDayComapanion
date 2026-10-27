// Zod 4 probes for eval support with `Function("")` to speed up object parsing. The web CSP never allows eval
// ('unsafe-eval' is absent on purpose), so the probe only produced a CSP violation report on every page.
// jitless skips the probe everywhere (web, Worker, Android); parsing stays correct and the cost is negligible here.
import { z } from 'zod';

z.config({ jitless: true });
