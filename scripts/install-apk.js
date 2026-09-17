const { spawn, execSync } = require('child_process');
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
const apkPath = path.resolve(androidDir, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const isWindows = process.platform === 'win32';
const gradleCmd = isWindows ? 'gradle.bat' : 'gradle';

console.log('[UPI-Easy] Building assembleDebug...');
const buildProc = spawn(gradleCmd, ['assembleDebug', '--no-daemon'], {
  cwd: androidDir,
  env,
  stdio: 'inherit',
  shell: isWindows
});

buildProc.on('close', (code) => {
  if (code !== 0) {
    console.error(`[UPI-Easy] Build failed with code ${code}`);
    process.exit(code);
  }

  console.log(`[UPI-Easy] Build succeeded! Installing ${apkPath}...`);
  try {
    execSync(`adb install -r "${apkPath}"`, { stdio: 'inherit' });
    console.log('[UPI-Easy] Installation complete on connected device!');
  } catch (err) {
    console.error('[UPI-Easy] ADB install failed. Make sure a device or emulator is connected with USB Debugging enabled.');
  }
});
