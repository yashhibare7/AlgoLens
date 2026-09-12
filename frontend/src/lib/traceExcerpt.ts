import type { ExecutionTrace } from '../types';

/** How many steps of context to send with an AI request, ending at the current step. */
const WINDOW_BEFORE = 14;

/**
 * Renders a window of the trace as plain text for the AI endpoint.
 *
 * <p>Sending rendered lines rather than the raw trace objects is a deliberate choice: it is
 * roughly an order of magnitude fewer tokens than the JSON, it is exactly the information the
 * model needs, and because the client builds it, the assistant works for anonymous runs that
 * were never persisted server-side.
 *
 * <p>The current step is always last, which is what the prompt tells the model to expect.
 */
export function buildTraceExcerpt(trace: ExecutionTrace, currentIndex: number): string[] {
  const start = Math.max(0, currentIndex - WINDOW_BEFORE);
  const lines: string[] = [];

  for (let i = start; i <= currentIndex && i < trace.steps.length; i++) {
    const step = trace.steps[i];
    const parts = [
      `step ${step.step}`,
      `line ${step.line}`,
      step.action,
      step.message,
    ];

    const structures: string[] = [];
    for (const visualization of step.visualizations ?? []) {
      if (visualization.elements) {
        structures.push(
          `${visualization.name}=[${visualization.elements
            .map((element) => String(element.value))
            .join(', ')}]`,
        );
      } else if (visualization.entries) {
        structures.push(
          `${visualization.name}={${visualization.entries
            .map((entry) => `${String(entry.key)}=${String(entry.value)}`)
            .join(', ')}}`,
        );
      } else if (visualization.nodes) {
        // Reference structures are described as an edge list. It is compact, and it states the
        // shape unambiguously -- including a cycle, which a flattened "1 -> 2 -> 3" rendering
        // would either lose or unroll forever.
        const payloads = visualization.nodes
          .map((node) => `${node.id}(${String(node.value)})`)
          .join(' ');
        const links = (visualization.edges ?? [])
          .map((edge) => `${edge.from}.${edge.label}->${edge.to ?? 'null'}`)
          .join(' ');
        structures.push(`${visualization.name}: ${payloads} | ${links}`);
      }
    }
    if (structures.length > 0) {
      parts.push(structures.join(' '));
    }

    const scalars = (step.variables ?? [])
      .map((variable) => `${variable.name}=${String(variable.value)}`)
      .join(' ');
    if (scalars) {
      parts.push(scalars);
    }

    // Bounded per line so a wide array or a large graph cannot blow up the request.
    lines.push(parts.join(' | ').slice(0, 500));
  }

  if (currentIndex >= trace.steps.length - 1) {
    const { metrics } = trace;
    lines.push(
      `run summary | status ${trace.status} | ${metrics.comparisons} data comparisons | ` +
        `${metrics.swaps} swaps | ${metrics.arrayReads} array reads | ` +
        `${metrics.arrayWrites} array writes | ${metrics.fieldReads} field reads | ` +
        `${metrics.fieldWrites} field writes | ${metrics.objectsCreated} objects created | ` +
        `${metrics.calls} calls`,
    );
  }

  return lines;
}
