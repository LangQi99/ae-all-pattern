"""Run the reobfuscated release JAR in an installed Forge client (use xvfb-run on CI)."""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

from minecraft_launcher_lib.command import get_minecraft_command
from minecraft_launcher_lib.forge import install_forge_version

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jar', type=Path, required=True)
parser.add_argument('--directory', type=Path, required=True)
parser.add_argument('--forge', default='47.4.20')
parser.add_argument('--ae2', choices=['15.4.10', '15.4.11', '15.5.5-uelm'], default='15.4.10')
parser.add_argument('--java', help='Java 17 executable; defaults to java on PATH')
parser.add_argument('--world', type=Path, help='Copy a test save and exercise the crafting confirmation UI')
parser.add_argument('--reuse-installation', action='store_true', help='Use an already installed Forge runtime')
parser.add_argument('--jei', action='store_true', help='Include the public JEI release')
args = parser.parse_args()
root = args.directory.resolve()
root.mkdir(parents=True, exist_ok=True)
# A new Minecraft 1.20.1 directory otherwise stops at accessibility onboarding,
# never reaching the title screen that the smoke-test completion gate requires.
options_path = root / 'options.txt'
if not options_path.exists():
    options_path.write_text('onboardAccessibility:false\n', encoding='utf-8')
mods = root / 'mods'
mods.mkdir(exist_ok=True)
# Refuse to mix artifacts from earlier tests with this exact release JAR.
existing = list(mods.glob('aeallpattern*.jar'))
if existing and (len(existing) != 1 or existing[0].name != args.jar.name
                 or existing[0].read_bytes() != args.jar.read_bytes()):
    raise SystemExit('Use a fresh game directory: a different AE All Pattern JAR already exists')
shutil.copy2(args.jar, mods / args.jar.name)
java = args.java or shutil.which('java')
if not java:
    raise SystemExit('Java 17 is required')

dependencies = [
    ('https://cdn.modrinth.com/data/Ck4E7v7R/versions/UD2nQxJx/guideme-20.1.7.jar',
     'fc039093c479b3632b1ab60ea503e79b76b686f9'),
]
# Maven artifacts are development jars; use checksum-pinned public releases.
ae2_releases = {
    '15.4.10': (
        'https://cdn.modrinth.com/data/XxWD5pD3/versions/7KVs6HMQ/appliedenergistics2-forge-15.4.10.jar',
        'ac255a120499f79f8deade474da09e3c00ff9bad'),
    '15.4.11': (
        'https://cdn.modrinth.com/data/XxWD5pD3/versions/jYgSnxye/appliedenergistics2-forge-15.4.11.jar',
        'sha256:f2ff9ee5122b037d4e93537425ab9fbe49860fc9c3977db13b20a50ca7d30f9e'),
    '15.5.5-uelm': (
        'https://github.com/AE2-Unofficial-Extended-Life-Modern/AE2-UELM/releases/download/forge/v15.5.5-uelm/appliedenergistics2-forge-15.5.5-uelm.jar',
        'sha256:1228c6fbf6b9ca8d586ab626b07cf762631b898d40886ef1ff6700fc095b788f'),
}
dependencies.append(ae2_releases[args.ae2])
if args.jei:
    dependencies.append((
        'https://cdn.modrinth.com/data/u6dRKJwZ/versions/1L7W8nyE/jei-1.20.1-forge-15.58.0.209.jar',
        '62fd8f374f5365101e07db500fea2d5cba52f466'))
for url, digest in dependencies:
    target = mods / url.rsplit('/', 1)[1]
    algorithm, expected = digest.split(':', 1) if ':' in digest else ('sha1', digest)
    if not target.exists() or hashlib.new(algorithm, target.read_bytes()).hexdigest() != expected:
        with urllib.request.urlopen(url, timeout=60) as response, target.open('wb') as output:
            shutil.copyfileobj(response, output)
    if hashlib.new(algorithm, target.read_bytes()).hexdigest() != expected:
        raise SystemExit(f'Dependency checksum mismatch: {target.name}')

for attempt in range(0 if args.reuse_installation else 3):
    try:
        install_forge_version(f'1.20.1-{args.forge}', str(root), java=java,
                              callback={'setStatus': lambda status: print(status, flush=True)})
        break
    except Exception:
        if attempt == 2:
            raise
        time.sleep(2)

options = {
    'username': 'ProductionTest',
    'uuid': '00000000000000000000000000000001',
    'token': '0',
    'executablePath': java,
    'gameDirectory': str(root),
    'jvmArguments': ['-Xmx3G', '-Daeallpattern.clientSmokeTest=true'],
}
command = get_minecraft_command(f'1.20.1-forge-{args.forge}', str(root), options)
if args.world:
    shutil.copytree(args.world, root / 'saves' / 'ProductionTest')
    command.insert(1, '-Daeallpattern.clientScreenSmokeTest=true')
    command.extend(['--quickPlaySingleplayer', 'ProductionTest'])
with (root / 'production-console.log').open('w') as output:
    result = subprocess.run(command, cwd=root, stdout=output, stderr=subprocess.STDOUT, timeout=180)
log = (root / 'logs/latest.log').read_text(errors='replace')
if result.returncode or 'CLIENT_SMOKE_TEST_PASSED' not in log:
    raise SystemExit(f'Production client failed; inspect {root}/production-console.log')
if args.world and 'PRODUCTION_SCREEN_SMOKE_TEST_PASSED' not in log:
    raise SystemExit('Production screen smoke test did not complete')
print('PRODUCTION_CLIENT_SMOKE_TEST_PASSED', flush=True)
