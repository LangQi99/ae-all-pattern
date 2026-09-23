"""Verify real MM recipe encoding and GuideME pages in a fresh production client directory.

Requires minecraft-launcher-lib==8.0. On Linux run under xvfb-run; no public server is opened.
The --world input must be this version's completed isolated Masterful GameTest world.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import urllib.request

from minecraft_launcher_lib.command import get_minecraft_command


def download(url, digest, destination):
    target = destination / url.rsplit('/', 1)[-1]
    with urllib.request.urlopen(url, timeout=60) as response, target.open('wb') as output:
        shutil.copyfileobj(response, output)
    if hashlib.sha1(target.read_bytes()).hexdigest() != digest:
        raise SystemExit(f'Checksum mismatch: {target.name}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft', choices=['1.20.1', '1.21.1'], required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--masterful', type=Path, required=True)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--fixture', type=Path, required=True)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--installation', type=Path, required=True, help='Installed official loader runtime/library directory')
    parser.add_argument('--version-id', required=True, help='Installed Minecraft launcher version ID')
    parser.add_argument('--java', required=True)
    parser.add_argument('--language', choices=['en_us', 'zh_cn'], default='en_us')
    args = parser.parse_args()
    root = args.directory.resolve()
    root.mkdir(parents=True, exist_ok=False)
    mods = root / 'mods'
    mods.mkdir()
    expected_mm = {
        '1.20.1': '50623481c93168f0fac58466731770ec8c866016a4cb868d01aa61b25310b69e',
        '1.21.1': '4fbac102611b8a7416c20e5f0215b3fa1a6a22c76a4ad4a6562df7585e1dbe15',
    }
    if hashlib.sha256(args.masterful.read_bytes()).hexdigest() != expected_mm[args.minecraft]:
        raise SystemExit('Unexpected Masterful Machinery artifact')
    shutil.copy2(args.jar, mods / args.jar.name)
    shutil.copy2(args.masterful, mods / 'masterful-machinery.jar')
    shutil.copytree(args.world, root / 'saves' / 'CompatibilityTest')
    shutil.copytree(args.fixture / 'datapack', root / 'saves' / 'CompatibilityTest' / 'datapacks' / 'aap_masterful_test',
                    dirs_exist_ok=True)
    shutil.copytree(args.fixture / 'config', root / 'config')
    (root / 'options.txt').write_text(f'lang:{args.language}\nrenderDistance:4\nsimulationDistance:4\nguiScale:2\n')
    dependencies = {
        '1.20.1': [
            ('https://cdn.modrinth.com/data/XxWD5pD3/versions/7KVs6HMQ/appliedenergistics2-forge-15.4.10.jar',
             'ac255a120499f79f8deade474da09e3c00ff9bad'),
            ('https://cdn.modrinth.com/data/Ck4E7v7R/versions/UD2nQxJx/guideme-20.1.7.jar',
             'fc039093c479b3632b1ab60ea503e79b76b686f9'),
            ('https://cdn.modrinth.com/data/u6dRKJwZ/versions/1L7W8nyE/jei-1.20.1-forge-15.58.0.209.jar',
             '62fd8f374f5365101e07db500fea2d5cba52f466'),
        ],
        '1.21.1': [
            ('https://cdn.modrinth.com/data/Ck4E7v7R/versions/PALpfSdf/guideme-21.1.1.jar',
             '5b0b0860be162db359e9caba32aad0e81357ea4b'),
            ('https://cdn.modrinth.com/data/XxWD5pD3/versions/kfyIqgJ6/appliedenergistics2-19.2.17.jar',
             '49c18d6a4af487957d7e5a6ad5dcbf71090b8e14'),
            ('https://cdn.modrinth.com/data/u6dRKJwZ/versions/DFIjSzbJ/jei-1.21.1-neoforge-19.25.0.322.jar',
             'f42aa17f8490dd437e75b8f4abd8360e9f0265b9'),
        ],
    }
    for url, digest in dependencies[args.minecraft]:
        download(url, digest, mods)
    options = {
        'username': 'AAPCompatTest', 'uuid': '00000000000000000000000000000002', 'token': '0',
        'executablePath': args.java, 'gameDirectory': str(root),
        'jvmArguments': ['-Xmx3G', '-Daeallpattern.clientSmokeTest=true', '-Daeallpattern.compatibilitySmokeTest=true'],
    }
    command = get_minecraft_command(args.version_id, str(args.installation.resolve()), options)
    command.extend(['--quickPlaySingleplayer', 'CompatibilityTest', '--width', '1280', '--height', '800'])
    with (root / 'production-console.log').open('w') as output:
        result = subprocess.run(command, cwd=root, stdout=output, stderr=subprocess.STDOUT, timeout=240)
    log = (root / 'logs/latest.log').read_text(errors='replace')
    if result.returncode or 'COMPATIBILITY_CLIENT_SMOKE_TEST_PASSED' not in log:
        raise SystemExit(f'Compatibility client failed: {root}/production-console.log')
    if len(list((root / 'screenshots').glob('aap-guide-*.png'))) != 5:
        raise SystemExit('Guide screenshot capture incomplete')
    print('COMPATIBILITY_PRODUCTION_CLIENT_PASSED', args.minecraft, args.language)


if __name__ == '__main__':
    main()
