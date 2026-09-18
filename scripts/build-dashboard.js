const fs = require('fs');
const path = require('path');

const htmlPath = path.resolve(__dirname, '..', 'api', 'src', 'dashboard', 'index.html');
const tsPath = path.resolve(__dirname, '..', 'api', 'src', 'dashboard', 'html.ts');

const html = fs.readFileSync(htmlPath, 'utf8');
const b64 = Buffer.from(html, 'utf8').toString('base64');

const tsCode = `// Auto-generated from api/src/dashboard/index.html - Do not edit manually
const HTML_BASE64 = "${b64}";

export function renderDashboardHtml(): string {
  if (typeof atob === "function") {
    return atob(HTML_BASE64);
  }
  return Buffer.from(HTML_BASE64, "base64").toString("utf-8");
}
`;

fs.writeFileSync(tsPath, tsCode, 'utf8');
console.log(`[UPI-Easy] Successfully compiled ${htmlPath} into ${tsPath} (${(b64.length / 1024).toFixed(1)} KB base64)`);
