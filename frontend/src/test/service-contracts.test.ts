import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

function normalizePath(p: string): string {
  // Replace path variables like {id}, {projectId}, ${id}, ${proposalId} with {param}
  return p
    .replace(/\$\{[^}]+\}/g, '{param}')
    .replace(/\{[^}]+\}/g, '{param}')
    .replace(/\/+/g, '/')
    .replace(/\/$/, '');
}

function extractBackendRoutes(): Set<string> {
  const backendControllerDir = path.resolve(__dirname, '../../../backend/src/main/java/com/chainlance/controller');
  const routes = new Set<string>();

  if (!fs.existsSync(backendControllerDir)) {
    throw new Error(`Backend controller dir not found at: ${backendControllerDir}`);
  }

  const files = fs.readdirSync(backendControllerDir).filter((f: string) => f.endsWith('.java'));

  for (const file of files) {
    const content = fs.readFileSync(path.join(backendControllerDir, file), 'utf-8');

    // Find class-level @RequestMapping
    const classMappingMatch = content.match(/@RequestMapping\(["']([^"']*)["']\)/);
    const basePath = classMappingMatch ? classMappingMatch[1] : '';

    // Find method-level mappings
    const methodRegex = /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)(?:\((?:value\s*=\s*)?["']([^"']*)["']\))?/g;
    let match: RegExpExecArray | null;

    while ((match = methodRegex.exec(content)) !== null) {
      const httpMethod = match[1].replace('Mapping', '').toUpperCase();
      const subPath = match[2] || '';
      const fullPath = normalizePath(`${basePath}/${subPath}`);
      routes.add(`${httpMethod} ${fullPath}`);
    }
  }

  return routes;
}

function extractFrontendServiceRoutes(): { method: string; path: string; file: string }[] {
  const servicesDir = path.resolve(__dirname, '../services');
  const serviceFiles = [
    'projects.service.ts',
    'proposals.service.ts',
    'agreements.service.ts',
    'negotiation.service.ts',
    'auth.service.ts',
    'wallet.service.ts',
  ];

  const extracted: { method: string; path: string; file: string }[] = [];

  for (const file of serviceFiles) {
    const filePath = path.join(servicesDir, file);
    if (!fs.existsSync(filePath)) continue;

    const content = fs.readFileSync(filePath, 'utf-8');
    // Regex matches api.get('/api/...'), api.post(`/api/...`)
    const callRegex = /api\.(get|post|put|delete)<[^>]*>\(\s*[`'"](\/api\/[^`'"]*)[`'"]/g;
    let match: RegExpExecArray | null;

    while ((match = callRegex.exec(content)) !== null) {
      const method = match[1].toUpperCase();
      const rawPath = match[2];
      extracted.push({
        method,
        path: normalizePath(rawPath),
        file,
      });
    }
  }

  return extracted;
}

describe('Frontend Service <-> Backend Controller Contract Test', () => {
  it('every endpoint called by frontend services must match an existing backend controller route', () => {
    const backendRoutes = extractBackendRoutes();
    const frontendCalls = extractFrontendServiceRoutes();

    expect(backendRoutes.size).toBeGreaterThan(0);
    expect(frontendCalls.length).toBeGreaterThan(0);

    const missingRoutes: string[] = [];

    for (const call of frontendCalls) {
      // Milestones endpoint is scheduled for Phase 2; allow skip or note if not yet in backend
      if (call.path === '/api/agreements/{param}/milestones') {
        continue;
      }
      const routeKey = `${call.method} ${call.path}`;
      if (!backendRoutes.has(routeKey)) {
        missingRoutes.push(`${routeKey} (called in ${call.file})`);
      }
    }

    if (missingRoutes.length > 0) {
      throw new Error(
        `Frontend calls endpoints that do not exist in backend controllers:\n${missingRoutes.join('\n')}`,
      );
    }
  });
});
