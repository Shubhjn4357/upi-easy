const fs = require('fs');
const path = require('path');
const esbuild = require(path.resolve(__dirname, '..', 'api', 'node_modules', 'esbuild'));

const dashboardDir = path.resolve(__dirname, '..', 'api', 'src', 'dashboard');
const entryPoint = path.resolve(dashboardDir, 'src', 'App.jsx');
const htmlPath = path.resolve(dashboardDir, 'index.html');
const tsPath = path.resolve(dashboardDir, 'html.ts');
const cssPath = path.resolve(dashboardDir, 'styles', 'theme.css');

// Static distribution output directories
const distDir = path.resolve(dashboardDir, 'dist');
const publicDir = path.resolve(__dirname, '..', 'api', 'public');
const publicDistDir = path.resolve(publicDir, 'dist');

if (!fs.existsSync(distDir)) fs.mkdirSync(distDir, { recursive: true });
if (!fs.existsSync(publicDistDir)) fs.mkdirSync(publicDistDir, { recursive: true });

// Read theme CSS
let cssContent = '';
if (fs.existsSync(cssPath)) {
  cssContent = fs.readFileSync(cssPath, 'utf8');
}

// React & ReactDOM globals mapping plugin for standalone browser bundle
const globalsPlugin = {
  name: 'globals',
  setup(build) {
    build.onResolve({ filter: /^react$/ }, args => ({
      path: args.path,
      namespace: 'react-global',
    }));
    build.onLoad({ filter: /.*/, namespace: 'react-global' }, () => ({
      contents: 'module.exports = window.React;',
      loader: 'js',
    }));
    build.onResolve({ filter: /^react-dom$/ }, args => ({
      path: args.path,
      namespace: 'react-dom-global',
    }));
    build.onLoad({ filter: /.*/, namespace: 'react-dom-global' }, () => ({
      contents: 'module.exports = window.ReactDOM;',
      loader: 'js',
    }));
  }
};

async function build() {
  console.log('[UPI-Easy] Compiling React Dashboard with esbuild from entry:', entryPoint);

  const result = await esbuild.build({
    entryPoints: [entryPoint],
    bundle: true,
    write: false,
    format: 'iife',
    globalName: 'UpiEasyApp',
    plugins: [globalsPlugin],
    loader: {
      '.js': 'jsx',
      '.jsx': 'jsx',
    },
    target: ['es2020'],
  });

  const productionJs = result.outputFiles[0].text;

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
  <title>UPI-Easy Admin Panel</title>

  <!-- Google Fonts: Plus Jakarta Sans & JetBrains Mono -->
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">

  <!-- Clean Console: Suppress Tailwind CDN production advisory -->
  <script>
    (function() {
      var _origWarn = console.warn;
      console.warn = function() {
        if (arguments[0] && typeof arguments[0] === 'string' && arguments[0].indexOf('cdn.tailwindcss.com') !== -1) return;
        _origWarn.apply(console, arguments);
      };
    })();
  </script>

  <!-- Tailwind CSS Core Engine -->
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

  <!-- Pre-compiled Native Production Bundle (Zero Runtime Transpilation) -->
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
  const tsCode = `// Auto-generated from api/src/dashboard modular sources via esbuild - Do not edit manually
const HTML_BASE64 = "${b64}";

export function renderDashboardHtml(): string {
  if (typeof atob === "function") {
    return atob(HTML_BASE64);
  }
  return Buffer.from(HTML_BASE64, "base64").toString("utf-8");
}
`;

  fs.writeFileSync(tsPath, tsCode, 'utf8');
  console.log(`[UPI-Easy] Successfully bundled dashboard into:`);
  console.log(`  - HTML: ${htmlPath}`);
  console.log(`  - Static Public: ${path.resolve(publicDir, 'index.html')}`);
  console.log(`  - Static JS Bundle: ${staticBundleJsPath} (${(productionJs.length / 1024).toFixed(1)} KB)`);
  console.log(`  - Compiled TypeScript: ${tsPath} (${(b64.length / 1024).toFixed(1)} KB base64)`);
}

build().catch((err) => {
  console.error('[UPI-Easy] Build failed:', err);
  process.exit(1);
});
