import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import * as a from './screens-a.mjs';
import * as b from './screens-b.mjs';
import * as c from './screens-c.mjs';
import * as d from './screens-d.mjs';
import * as e from './screens-e.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const pages = {
  '00-overview.html': e.overview,
  '01-assistant-entry.html': a.entry,
  '02-assistant-chat.html': a.chat,
  '03-response-generate.html': b.generate,
  '04-conditions-generate.html': b.conditions,
  '05-settings-ai.html': c.settingsAI,
  '06-settings-mcp.html': c.mcp,
  '07-match-explain.html': d.matchExplain,
  '08-states.html': d.states
};

for (const [name, html] of Object.entries(pages)) {
  writeFileSync(join(root, name), html);
  console.log('wrote', name, html.length);
}
