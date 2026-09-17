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

const javaHome = getJavaHome();
const env = { ...process.env };
if (javaHome) {
  env.JAVA_HOME = javaHome;
}

const androidDir = path.resolve(__dirname, '..', 'android');
const isWindows = process.platform === 'win32';
const gradleCmd = isWindows ? 'gradle.bat' : 'gradle';

console.log('--------------------------------------------------');
console.log(' UPI-Easy Android Keystore Fingerprints Inspector ');
console.log('--------------------------------------------------');

const proc = spawn(gradleCmd, ['signingReport', '--no-daemon'], {
  cwd: androidDir,
  env,
  shell: isWindows
});

let output = '';

proc.stdout.on('data', (data) => {
  const text = data.toString();
  output += text;
  process.stdout.write(text);
});

proc.stderr.on('data', (data) => {
  process.stderr.write(data);
});

proc.on('close', (code) => {
  if (code === 0) {
    const sha1Match = output.match(/SHA1:\s+([A-F0-9:]+)/i);
    const sha256Match = output.match(/SHA-256:\s+([A-F0-9:]+)/i);

    console.log('\n==================================================');
    console.log(' QUICK COPY FOR FIREBASE & GOOGLE CLOUD CONSOLE:');
    console.log('==================================================');
    console.log('Package Name: com.aerospace.upieasy');
    if (sha1Match) {
      console.log(`SHA-1:        ${sha1Match[1]}`);
    }
    if (sha256Match) {
      console.log(`SHA-256:      ${sha256Match[1]}`);
    }
    console.log('==================================================\n');
  }
  process.exit(code || 0);
});
