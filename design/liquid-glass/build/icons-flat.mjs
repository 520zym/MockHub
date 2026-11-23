import fs from 'fs';

const files = [
  '01-api-list.html',
  '02-api-edit.html',
  '03-logs.html',
  '04-assistant.html',
  '05-login.html'
];

const map = {
  '<path d="M9 3v4M15 3v4"/><path d="M7 7h10v3a5 5 0 0 1-5 5 5 5 0 0 1-5-5V7Z"/><path d="M12 15v6"/>':
    '<path d="M8.5 3a1.2 1.2 0 0 1 1.2 1.2v2.5h4.6V4.2a1.2 1.2 0 1 1 2.4 0v2.5h.5A2.8 2.8 0 0 1 20 9.5v1.7a7.4 7.4 0 0 1-6.2 7.3v2.3a1.2 1.2 0 1 1-2.4 0v-2.3A7.4 7.4 0 0 1 4 11.2V9.5a2.8 2.8 0 0 1 2.8-2.8h.5V4.2A1.2 1.2 0 0 1 8.5 3Z"/>',

  '<path d="M3.5 7.5a3 3 0 0 1 3-3h3.1l2.1 2.6h5.8a3 3 0 0 1 3 3v7.4a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3z"/>':
    '<path d="M3 7.2A3.2 3.2 0 0 1 6.2 4h3.1c.8 0 1.5.3 2 .9l.9 1.1h5.6A3.2 3.2 0 0 1 21 9.2v7.6a3.2 3.2 0 0 1-3.2 3.2H6.2A3.2 3.2 0 0 1 3 16.8Z"/>',

  '<path d="M9.5 4C7.3 4 6 5.3 6 7.5v2c0 1.6-.8 2.5-2.5 2.5C5.2 12 6 12.9 6 14.5v2C6 18.7 7.3 20 9.5 20"/><path d="M14.5 4C16.7 4 18 5.3 18 7.5v2c0 1.6.8 2.5 2.5 2.5C18.8 12 18 12.9 18 14.5v2C18 18.7 16.7 20 14.5 20"/>':
    '<path fill-rule="evenodd" d="M3 6.2A3.2 3.2 0 0 1 6.2 3h6.1c.9 0 1.7.3 2.3.9l5.5 5.5a3.2 3.2 0 0 1 0 4.5l-5.4 5.4a3.2 3.2 0 0 1-4.5 0l-5.5-5.5A3.2 3.2 0 0 1 3 11.5Zm5.2 3.7a1.7 1.7 0 1 0 0-3.4 1.7 1.7 0 0 0 0 3.4Z"/>',

  '<path d="M5 20v-5M11 20V9M17 20V4M3 20h18"/>':
    '<rect x="3" y="13" width="4.2" height="7" rx="1.5"/><rect x="9.9" y="8" width="4.2" height="12" rx="1.5"/><rect x="16.8" y="3" width="4.2" height="17" rx="1.5"/>',

  '<circle cx="8.5" cy="8" r="3"/><path d="M3 19c0-3 2.5-5 5.5-5s5.5 2 5.5 5"/><circle cx="16" cy="9" r="2.4"/><path d="M15.6 14.2c2.7 0 4.9 1.8 4.9 4.3"/>':
    '<circle cx="8.3" cy="7.7" r="3.2"/><path d="M2.4 18.2c0-3 2.5-5 5.9-5s5.9 2 5.9 5c0 .8-.6 1.4-1.4 1.4H3.8c-.8 0-1.4-.6-1.4-1.4Z"/><g opacity=".55"><circle cx="16.7" cy="8.8" r="2.5"/><path d="M15.3 14.2c2.9-.2 5.4 1.6 5.4 4.2 0 .7-.5 1.2-1.2 1.2h-3c.1-.4.2-.9.2-1.4 0-1.6-.5-3-1.4-4Z"/></g>',

  '<circle cx="12" cy="8" r="3.5"/><path d="M4.5 20c0-3.7 3.2-6 7.5-6s7.5 2.3 7.5 6"/>':
    '<circle cx="12" cy="8" r="3.8"/><path d="M4.2 20c0-3.9 3.4-6.3 7.8-6.3s7.8 2.4 7.8 6.3c0 .7-.6 1.2-1.3 1.2H5.5c-.7 0-1.3-.5-1.3-1.2Z"/>',

  '<path d="M9 6h11M9 12h11M9 18h11"/><circle cx="4.5" cy="6" r="1.2" style="fill:currentColor;stroke:none"/><circle cx="4.5" cy="12" r="1.2" style="fill:currentColor;stroke:none"/><circle cx="4.5" cy="18" r="1.2" style="fill:currentColor;stroke:none"/>':
    '<rect x="9" y="5" width="12" height="2.4" rx="1.2"/><rect x="9" y="10.8" width="12" height="2.4" rx="1.2"/><rect x="9" y="16.6" width="12" height="2.4" rx="1.2"/><circle cx="4.6" cy="6.2" r="1.5"/><circle cx="4.6" cy="12" r="1.5"/><circle cx="4.6" cy="17.8" r="1.5"/>',

  '<path d="M4 7h8M16 7h4M4 17h4M12 17h8"/><circle cx="14" cy="7" r="2"/><circle cx="10" cy="17" r="2"/>':
    '<rect x="3" y="5.8" width="9" height="2.4" rx="1.2"/><rect x="17" y="5.8" width="4" height="2.4" rx="1.2"/><circle cx="14.8" cy="7" r="2.7"/><rect x="3" y="15.8" width="4" height="2.4" rx="1.2"/><rect x="11" y="15.8" width="10" height="2.4" rx="1.2"/><circle cx="8.6" cy="17" r="2.7"/>'
};

for (const file of files) {
  let html = fs.readFileSync(file, 'utf8');
  for (const [oldIcon, newIcon] of Object.entries(map)) {
    html = html.replaceAll(oldIcon, newIcon);
  }
  fs.writeFileSync(file, html);
}
console.log('filled icons applied');
