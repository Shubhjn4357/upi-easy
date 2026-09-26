const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

function getJavaHome() {
  const candidates = [
    'C:\\Program Files\\Java\\jdk-17',
    'C:\\Program Files\\Eclipse Adoptium\\jdk-17',
    process.env.JAVA_HOME_17,
    process.env.JAVA_HOME
  ].filter(Boolean);

  for (const dir of candidates) {
    if (fs.existsSync(dir)) {
      return dir;
    }
  }
  return process.env.JAVA_HOME;
}

function loadEnvFile(filePath) {
  if (!fs.existsSync(filePath)) return {};
  const content = fs.readFileSync(filePath, 'utf8');
  const result = {};
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) continue;
    const idx = trimmed.indexOf('=');
    const key = trimmed.substring(0, idx).trim();
    let val = trimmed.substring(idx + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.substring(1, val.length - 1);
    }
    result[key] = val;
  }
  return result;
}

const rootEnv = loadEnvFile(path.resolve(__dirname, '..', '.env'));
const apiEnv = loadEnvFile(path.resolve(__dirname, '..', 'api', '.env'));
const mergedEnv = { ...apiEnv, ...rootEnv };

const javaHome = getJavaHome();
const env = { ...mergedEnv, ...process.env };
if (javaHome) {
  env.JAVA_HOME = javaHome;
}


const args = process.argv.slice(2);
const androidDir = path.resolve(__dirname, '..', 'android');

console.log(`[UPI-Easy] Using JAVA_HOME: ${env.JAVA_HOME || 'system default'}`);
console.log(`[UPI-Easy] Running gradle ${args.join(' ')} in ${androidDir}`);

const isWindows = process.platform === 'win32';
const gradleCmd = isWindows ? 'gradle.bat' : 'gradle';

const proc = spawn(gradleCmd, args, {
  cwd: androidDir,
  env,
  stdio: 'inherit',
  shell: isWindows
});

proc.on('close', (code) => {
  process.exit(code || 0);
});
