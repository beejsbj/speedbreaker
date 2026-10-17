#!/usr/bin/env python3
"""Fetch verified unsigned CI inputs to the existing signing host; never signs/uploads."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = 'beejsbj/speedbreaker'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def gh(path):
    return json.loads(subprocess.check_output(['gh', 'api', path], text=True))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def archive(artifact, destination):
    """Check the host's archive digest before extracting any input."""
    require(not artifact['expired'], 'Expired artifact: rerun CI')
    expected = artifact.get('digest', '')
    require(expected.startswith('sha256:'), 'GitHub artifact lacks SHA-256 evidence')
    with tempfile.TemporaryDirectory() as temporary:
        zipped = Path(temporary) / 'artifact.zip'
        with zipped.open('wb') as output:
            subprocess.run(['gh', 'api', f'repos/{REPOSITORY}/actions/artifacts/{artifact["id"]}/zip'],
                           stdout=output, check=True)
        require(sha(zipped) == expected.removeprefix('sha256:'), 'Artifact archive digest mismatch')
        with zipfile.ZipFile(zipped) as source:
            require(sum((item.file_size for item in source.infolist())) < 512 * 1024 * 1024, 'Oversized archive')
            for item in source.infolist():
                name = PurePosixPath(item.filename)
                require(not name.is_absolute() and '..' not in name.parts and ('\\' not in item.filename), 'Unsafe archive path')
                require(item.external_attr >> 16 & 61440 != 40960, 'Symlink in artifact')
            source.extractall(destination)
    return {'artifactId': artifact['id'], 'name': artifact['name'], 'archiveSha256': expected[7:]}


def verify_signing_input(receipt, unsigned, commit, tree):
    """Re-establish trust from GitHub, never from editable local receipt hashes.

    Only the GitHub-channel APK is signed for sharing; the community and Play inputs are
    fetched for store work but never signed here.
    """
    run_id = int(receipt['run_id'])
    run = gh(f'repos/{REPOSITORY}/actions/runs/{run_id}')
    require(run.get('status') == 'completed' and run.get('conclusion') == 'success', 'CI must finish successfully')
    require(run.get('head_sha') == commit, 'Run does not verify the current source commit')
    workflow = gh(f'repos/{REPOSITORY}/actions/workflows/{run["workflow_id"]}')
    require(workflow['path'] == '.github/workflows/android.yml', 'Unexpected build workflow')
    artifacts = gh(f'repos/{REPOSITORY}/actions/runs/{run_id}/artifacts?per_page=100')['artifacts']
    inventory = [a for a in artifacts if a['name'].startswith('store-inventory-github-')]
    require(len(inventory) == 1, 'Missing/ambiguous GitHub inventory')
    with tempfile.TemporaryDirectory() as temporary:
        location = Path(temporary)
        archive(inventory[0], location / 'inventory')
        provenance_files = list((location / 'inventory').rglob('provenance.json'))
        require(len(provenance_files) == 1, 'Missing/ambiguous provenance')
        provenance = json.loads(provenance_files[0].read_text())
        require(provenance['sourceTree'] == tree and str(provenance['githubRunId']) == str(run_id), 'Hosted provenance mismatch')
        built_head = provenance['sourceHead']
        require(gh(f'repos/{REPOSITORY}/git/commits/{built_head}')['tree']['sha'] == tree, 'Host/source tree mismatch')
        found = [a for a in artifacts if a['name'] == 'unsigned-github-release-' + built_head]
        require(len(found) == 1, 'Missing/ambiguous GitHub APK')
        host_receipt = archive(found[0], location / 'apk')
        apks = list((location / 'apk').rglob('*.apk'))
        require(len(apks) == 1, 'Missing/ambiguous APK payload')
        digest = sha(apks[0])
        require(digest == provenance['artifactSha256']['apk'], 'Hosted payload/provenance mismatch')
        require(sha(unsigned) == digest, 'Local APK differs from the verified hosted artifact')
        trusted = {'repository': REPOSITORY, 'source_commit': commit, 'source_tree': tree,
                   'build_checkout': built_head, 'run_id': run_id, 'run_url': run['html_url'],
                   'input_sha256': digest, 'channel': 'github', 'kind': 'apk', **host_receipt}
        require(receipt == trusted, 'Local input receipt differs from hosted evidence')
        return trusted


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', type=int, required=True)
    parser.add_argument('--commit', help='Expected source commit; defaults to local HEAD')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'build/store-inputs')
    args = parser.parse_args()
    commit = subprocess.check_output(['git', 'rev-parse', args.commit or 'HEAD'], cwd=ROOT, text=True).strip()
    tree = subprocess.check_output(['git', 'rev-parse', commit + '^{tree}'], cwd=ROOT, text=True).strip()
    run = gh(f'repos/{REPOSITORY}/actions/runs/{args.run_id}')
    require(run['status'] == 'completed' and run['conclusion'] == 'success', 'CI must finish successfully')
    require(run['head_sha'] == commit, 'Run does not verify the expected source commit')
    workflow = gh(f'repos/{REPOSITORY}/actions/workflows/{run["workflow_id"]}')
    require(workflow['path'] == '.github/workflows/android.yml', 'Unexpected build workflow')
    artifacts = gh(f'repos/{REPOSITORY}/actions/runs/{args.run_id}/artifacts?per_page=100')['artifacts']
    out = args.output_dir.expanduser().resolve() / str(args.run_id)
    require(not out.exists(), 'Output exists; use a fresh directory')
    out.mkdir(parents=True)
    evidence = []
    for channel in ('github', 'community', 'play'):
        inventory = [a for a in artifacts if a['name'].startswith('store-inventory-' + channel + '-')]
        require(len(inventory) == 1, 'Missing/ambiguous release inventory; rerun current CI')
        inventory_dir = out / channel / 'inventory'
        evidence.append(archive(inventory[0], inventory_dir))
        receipts = list(inventory_dir.rglob('provenance.json'))
        require(len(receipts) == 1, 'Missing/ambiguous provenance')
        provenance = json.loads(receipts[0].read_text())
        require(provenance['sourceTree'] == tree, 'Built source tree differs from requested commit')
        require(str(provenance['githubRunId']) == str(args.run_id), 'Provenance belongs to another run')
        built_head = provenance['sourceHead']
        # Pull requests build a public merge checkout; validate its exact tree too.
        host_tree = gh(f'repos/{REPOSITORY}/git/commits/{built_head}')['tree']['sha']
        require(host_tree == tree, 'Host/source tree mismatch')
        for kind, prefix, extension in [('apk', 'unsigned-' + channel + '-release-', '.apk'),
                                         *([('aab', 'unsigned-bundle-', '.aab')] if channel == 'play' else [])]:
            found = [a for a in artifacts if a['name'] == prefix + built_head]
            require(len(found) == 1, 'Missing/ambiguous unsigned input')
            location = out / channel / kind
            receipt = archive(found[0], location)
            files = list(location.rglob('*' + extension))
            require(len(files) == 1, 'Missing/ambiguous payload')
            digest = sha(files[0])
            require(digest == provenance['artifactSha256'][kind], 'Payload/provenance hash mismatch')
            input_receipt = {'repository': REPOSITORY, 'source_commit': commit, 'source_tree': tree,
                             'build_checkout': built_head, 'run_id': args.run_id,
                             'run_url': run['html_url'], 'input_sha256': digest, 'channel': channel,
                             'kind': kind, **receipt}
            files[0].with_suffix(extension + '.input.json').write_text(json.dumps(input_receipt, indent=2) + '\n')
            evidence.append(input_receipt)
            print(f'Verified {channel} {kind}: {files[0]} SHA-256 {digest}')
    (out / 'download-receipt.json').write_text(json.dumps(evidence, indent=2) + '\n')


if __name__ == '__main__':
    main()
