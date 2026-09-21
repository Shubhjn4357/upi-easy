const fs = require('fs');
const path = require('path');

const dashboardDir = path.resolve(__dirname, '..', 'api', 'src', 'dashboard');
const htmlPath = path.resolve(dashboardDir, 'index.html');
const tsPath = path.resolve(dashboardDir, 'html.ts');
const cssPath = path.resolve(dashboardDir, 'styles', 'theme.css');

// Static distribution output directories
const distDir = path.resolve(dashboardDir, 'dist');
const publicDir = path.resolve(__dirname, '..', 'api', 'public');
const publicDistDir = path.resolve(publicDir, 'dist');

if (!fs.existsSync(distDir)) fs.mkdirSync(distDir, { recursive: true });
if (!fs.existsSync(publicDistDir)) fs.mkdirSync(publicDistDir, { recursive: true });

// Ordered list of modules to assemble
const moduleFiles = [
  // 1. Security, Auth & React Router Libraries
  path.resolve(dashboardDir, 'src', 'lib', 'auth.js'),
  path.resolve(dashboardDir, 'src', 'lib', 'api.js'),
  path.resolve(dashboardDir, 'src', 'lib', 'router.jsx'),

  // 2. shadcn/ui Core Primitives & Lucide Icons
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'icons.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'button.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'card.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'badge.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'input.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'table.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'dialog.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'sheet.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'ui', 'components.jsx'),

  // 3. Layout Shells & Containers
  path.resolve(dashboardDir, 'src', 'components', 'Modal.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'BottomSheet.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'Navbar.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'Sidebar.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'BottomNav.jsx'),

  // 4. Extracted Focused Modals
  path.resolve(dashboardDir, 'src', 'components', 'modals', 'RecordPaymentModal.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'modals', 'UpiAccountModal.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'modals', 'QrCodeModal.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'modals', 'StaffModal.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'modals', 'BankAccountModal.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'modals', 'TableRowModal.jsx'),
  path.resolve(dashboardDir, 'src', 'components', 'modals', 'TransactionDetailModal.jsx'),

  // 5. SaaS Pages
  path.resolve(dashboardDir, 'src', 'pages', 'LoginPage.jsx'),
  path.resolve(dashboardDir, 'src', 'pages', 'OverviewPage.jsx'),
  path.resolve(dashboardDir, 'src', 'pages', 'TransactionsPage.jsx'),
  path.resolve(dashboardDir, 'src', 'pages', 'UpiPage.jsx'),
  path.resolve(dashboardDir, 'src', 'pages', 'StaffPage.jsx'),
  path.resolve(dashboardDir, 'src', 'pages', 'AccountsPage.jsx'),
  path.resolve(dashboardDir, 'src', 'pages', 'ProfilePage.jsx'),
  path.resolve(dashboardDir, 'src', 'pages', 'TablesPage.jsx'),
  path.resolve(dashboardDir, 'src', 'pages', 'HealthPage.jsx'),

  // 6. Main App Entry
  path.resolve(dashboardDir, 'src', 'App.jsx'),
];

// Read theme CSS
let cssContent = '';
if (fs.existsSync(cssPath)) {
  cssContent = fs.readFileSync(cssPath, 'utf8');
}

// Function to transform ES module export and import syntax for the self-contained static bundle
function transformModuleForBundle(code) {
  return code
    .replace(/import\s*[\s\S]*?from\s*['"][^'"]+['"];?/g, '')
    .replace(/import\s*['"][^'"]+['"];?/g, '')
    .replace(/^\s*import\s+.*?;?\s*$/gm, '')
    .replace(/export\s+default\s+function\s+/g, 'function ')
    .replace(/export\s+function\s+/g, 'function ')
    .replace(/export\s+const\s+/g, 'const ')
    .replace(/export\s+let\s+/g, 'let ')
    .replace(/export\s+var\s+/g, 'var ')
    .replace(/export\s+default\s+[A-Za-z0-9_]+;?/g, '')
    .replace(/export\s*\{[\s\S]*?\};?/g, '');
}

// Read and bundle modules
let combinedJsx = `
const { useState, useEffect, useMemo, useRef, Suspense, startTransition } = React;
`;

for (const file of moduleFiles) {
  if (fs.existsSync(file)) {
    const rawCode = fs.readFileSync(file, 'utf8');
    const bundledCode = transformModuleForBundle(rawCode);
    combinedJsx += `\n/* --- ${path.basename(file)} --- */\n` + bundledCode + '\n';
  } else {
    console.warn(`[Build Warning] File not found: ${file}`);
  }
}

// Pre-compile JSX to native production JavaScript using TypeScript compiler
const ts = require(path.resolve(__dirname, '..', 'api', 'node_modules', 'typescript'));

console.log('[UPI-Easy] Pre-compiling JSX to native production JavaScript with TypeScript...');
const transpiled = ts.transpileModule(combinedJsx, {
  compilerOptions: {
    jsx: ts.JsxEmit.React,
    target: ts.ScriptTarget.ES2020,
    module: ts.ModuleKind.None,
    removeComments: false,
  }
});
const productionJs = transpiled.outputText;

// Save standalone static JS bundle & CSS
const staticBundleJsPath = path.resolve(distDir, 'dashboard.bundle.js');
const publicBundleJsPath = path.resolve(publicDistDir, 'dashboard.bundle.js');
const staticCssPath = path.resolve(distDir, 'theme.css');
const publicCssPath = path.resolve(publicDistDir, 'theme.css');

fs.writeFileSync(staticBundleJsPath, productionJs, 'utf8');
fs.writeFileSync(publicBundleJsPath, productionJs, 'utf8');
fs.writeFileSync(staticCssPath, cssContent, 'utf8');
fs.writeFileSync(publicCssPath, cssContent, 'utf8');

// Complete standalone static HTML document with shadcn tokens and Tailwind configuration
const fullHtml = `<!DOCTYPE html>
<html lang="en" class="dark">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>UPI-Easy — Multi-Tenant SaaS Platform & Admin Console</title>

  <!-- Google Fonts: Plus Jakarta Sans & JetBrains Mono -->
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">

  <!-- Suppress CDN dev warning for clean production console -->
  <script>
    (function() {
      var _origWarn = console.warn;
      console.warn = function() {
        if (arguments[0] && typeof arguments[0] === 'string' && arguments[0].indexOf('cdn.tailwindcss.com') !== -1) return;
        _origWarn.apply(console, arguments);
      };
    })();
  </script>

  <!-- Tailwind CSS -->
  <script src="https://cdn.tailwindcss.com"></script>

  <!-- React 18 & ReactDOM (Precompiled Production UMD) -->
  <script src="https://unpkg.com/react@18/umd/react.production.min.js" crossorigin></script>
  <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js" crossorigin></script>

  <!-- Google Identity Services (GSI) One Tap Library -->
  <script src="https://accounts.google.com/gsi/client" async defer></script>

  <script>
    tailwind.config = {
      darkMode: 'class',
      theme: {
        extend: {
          fontFamily: {
            sans: ['"Plus Jakarta Sans"', 'sans-serif'],
            mono: ['"JetBrains Mono"', 'monospace'],
          },
          colors: {
            border: "hsl(var(--border))",
            input: "hsl(var(--input))",
            ring: "hsl(var(--ring))",
            background: "hsl(var(--background))",
            foreground: "hsl(var(--foreground))",
            primary: {
              DEFAULT: "hsl(var(--primary))",
              foreground: "hsl(var(--primary-foreground))",
            },
            secondary: {
              DEFAULT: "hsl(var(--secondary))",
              foreground: "hsl(var(--secondary-foreground))",
            },
            destructive: {
              DEFAULT: "hsl(var(--destructive))",
              foreground: "hsl(var(--destructive-foreground))",
            },
            muted: {
              DEFAULT: "hsl(var(--muted))",
              foreground: "hsl(var(--muted-foreground))",
            },
            accent: {
              DEFAULT: "hsl(var(--accent))",
              foreground: "hsl(var(--accent-foreground))",
              cyan: '#06B6D4',
              emerald: '#10B981',
              amber: '#F59E0B',
              rose: '#F43F5E',
              violet: '#8B5CF6'
            },
            popover: {
              DEFAULT: "hsl(var(--popover))",
              foreground: "hsl(var(--popover-foreground))",
            },
            card: {
              DEFAULT: "hsl(var(--card))",
              foreground: "hsl(var(--card-foreground))",
            },
            brand: {
              50: '#EEF2FF',
              100: '#E0E7FF',
              200: '#C7D2FE',
              300: '#A5B4FC',
              400: '#818CF8',
              500: '#6366F1',
              600: '#4F46E5',
              700: '#4338CA',
              800: '#3730A3',
              900: '#312E81',
            }
          }
        }
      }
    }
  </script>

  <style>
${cssContent}
  </style>
</head>
<body>
  <div id="root"></div>

  <script>
${productionJs}
  </script>
</body>
</html>
`;

// Write assembled HTML to index.html and public/index.html
fs.writeFileSync(htmlPath, fullHtml, 'utf8');
fs.writeFileSync(path.resolve(publicDir, 'index.html'), fullHtml, 'utf8');

// Base64 encode for html.ts
const b64 = Buffer.from(fullHtml, 'utf8').toString('base64');
const tsCode = `// Auto-generated from api/src/dashboard modular sources - Do not edit manually
const HTML_BASE64 = "${b64}";

export function renderDashboardHtml(): string {
  if (typeof atob === "function") {
    return atob(HTML_BASE64);
  }
  return Buffer.from(HTML_BASE64, "base64").toString("utf-8");
}
`;

fs.writeFileSync(tsPath, tsCode, 'utf8');
console.log(`[UPI-Easy] Successfully assembled ${moduleFiles.length} modules into:`);
console.log(`  - HTML: ${htmlPath}`);
console.log(`  - Static Public: ${path.resolve(publicDir, 'index.html')}`);
console.log(`  - Static JS Bundle: ${staticBundleJsPath}`);
console.log(`  - Compiled TypeScript: ${tsPath} (${(b64.length / 1024).toFixed(1)} KB base64)`);
