import { performance } from 'node:perf_hooks';
import { writeFile } from 'node:fs/promises';

const args = new Map();
for (let index = 2; index < process.argv.length; index += 2) {
  args.set(process.argv[index], process.argv[index + 1]);
}

const baseUrl = (args.get('--base-url') ?? process.env.BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const endpoint = args.get('--endpoint') ?? '/books?page=0&size=10';
const requestCount = Number(args.get('--requests') ?? 200);
const concurrency = Number(args.get('--concurrency') ?? 20);
const maxP95Ms = Number(args.get('--max-p95-ms') ?? 1500);
const output = args.get('--output');

if (!Number.isInteger(requestCount) || requestCount < 1 || !Number.isInteger(concurrency) || concurrency < 1) {
  throw new Error('requests and concurrency must be positive integers');
}

const url = new URL(endpoint, `${baseUrl}/`).toString();
for (let index = 0; index < Math.min(5, requestCount); index += 1) {
  const response = await fetch(url, { signal: AbortSignal.timeout(5000) });
  if (!response.ok) throw new Error(`Warm-up failed with HTTP ${response.status}`);
  await response.arrayBuffer();
}

let cursor = 0;
const latencies = [];
const statuses = {};
const errors = [];
const startedAt = performance.now();

async function worker() {
  while (true) {
    const requestIndex = cursor++;
    if (requestIndex >= requestCount) return;
    const started = performance.now();
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(10000) });
      await response.arrayBuffer();
      statuses[response.status] = (statuses[response.status] ?? 0) + 1;
      if (!response.ok) errors.push(`request ${requestIndex}: HTTP ${response.status}`);
    } catch (error) {
      errors.push(`request ${requestIndex}: ${error.message}`);
    } finally {
      latencies.push(performance.now() - started);
    }
  }
}

await Promise.all(Array.from({ length: Math.min(concurrency, requestCount) }, () => worker()));
latencies.sort((left, right) => left - right);
const percentile = (ratio) => latencies[Math.min(latencies.length - 1, Math.ceil(latencies.length * ratio) - 1)];
const elapsedMs = performance.now() - startedAt;
const report = {
  generatedAt: new Date().toISOString(),
  url,
  requestCount,
  concurrency,
  statuses,
  errorCount: errors.length,
  sampleErrors: errors.slice(0, 10),
  latencyMs: {
    min: Number(latencies[0].toFixed(2)),
    p50: Number(percentile(0.5).toFixed(2)),
    p95: Number(percentile(0.95).toFixed(2)),
    max: Number(latencies.at(-1).toFixed(2)),
  },
  throughputRequestsPerSecond: Number((requestCount / (elapsedMs / 1000)).toFixed(2)),
  acceptance: {
    maxP95Ms,
    passed: errors.length === 0 && percentile(0.95) <= maxP95Ms,
  },
};

const json = `${JSON.stringify(report, null, 2)}\n`;
process.stdout.write(json);
if (output) await writeFile(output, json, 'utf8');
if (!report.acceptance.passed) process.exitCode = 1;
