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


def gh(path):
    return json.loads(subprocess.check_output(['gh', 'api', path], text=True))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def archive(artifact, destination):
    """Check the host's archive digest before extracting any input."""
    assert not artifact['expired'], 'Expired artifact: rerun CI'
    expected = artifact.get('digest', '')
    assert expected.startswith('sha256:'), 'GitHub artifact lacks SHA-256 evidence'
    with tempfile.TemporaryDirectory() as temporary:
        zipped = Path(temporary) / 'artifact.zip'
        with zipped.open('wb') as output:
            subprocess.run(['gh', 'api', f'repos/{REPOSITORY}/actions/artifacts/{artifact["id"]}/zip'],
                           stdout=output, check=True)
        assert sha(zipped) == expected.removeprefix('sha256:'), 'Artifact archive digest mismatch'
        with zipfile.ZipFile(zipped) as source:
            assert sum(item.file_size for item in source.infolist()) < 512 * 1024 * 1024, 'Oversized archive'
            for item in source.infolist():
                name = PurePosixPath(item.filename)
                assert not name.is_absolute() and '..' not in name.parts and '\\' not in item.filename, 'Unsafe archive path'
                assert (item.external_attr >> 16) & 0o170000 != 0o120000, 'Symlink in artifact'
            source.extractall(destination)
    return {'artifactId': artifact['id'], 'name': artifact['name'], 'archiveSha256': expected[7:]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', type=int, required=True)
    parser.add_argument('--commit', help='Expected source commit; defaults to local HEAD')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'build/store-inputs')
    args = parser.parse_args()
    commit = subprocess.check_output(['git', 'rev-parse', args.commit or 'HEAD'], cwd=ROOT, text=True).strip()
    tree = subprocess.check_output(['git', 'rev-parse', commit + '^{tree}'], cwd=ROOT, text=True).strip()
    run = gh(f'repos/{REPOSITORY}/actions/runs/{args.run_id}')
    assert run['status'] == 'completed' and run['conclusion'] == 'success', 'CI must finish successfully'
    assert run['head_sha'] == commit, 'Run does not verify the expected source commit'
    workflow = gh(f'repos/{REPOSITORY}/actions/workflows/{run["workflow_id"]}')
    assert workflow['path'] == '.github/workflows/android.yml', 'Unexpected build workflow'
    artifacts = gh(f'repos/{REPOSITORY}/actions/runs/{args.run_id}/artifacts?per_page=100')['artifacts']
    out = args.output_dir.expanduser().resolve() / str(args.run_id)
    assert not out.exists(), 'Output exists; use a fresh directory'
    out.mkdir(parents=True)
    evidence = []
    for channel in ('community', 'play'):
        inventory = [a for a in artifacts if a['name'].startswith('store-inventory-' + channel + '-')]
        assert len(inventory) == 1, 'Missing/ambiguous release inventory; rerun current CI'
        inventory_dir = out / channel / 'inventory'
        evidence.append(archive(inventory[0], inventory_dir))
        receipts = list(inventory_dir.rglob('provenance.json'))
        assert len(receipts) == 1, 'Missing/ambiguous provenance'
        provenance = json.loads(receipts[0].read_text())
        assert provenance['sourceTree'] == tree, 'Built source tree differs from requested commit'
        assert str(provenance['githubRunId']) == str(args.run_id), 'Provenance belongs to another run'
        built_head = provenance['sourceHead']
        # Pull requests build a public merge checkout; validate its exact tree too.
        host_tree = gh(f'repos/{REPOSITORY}/git/commits/{built_head}')['tree']['sha']
        assert host_tree == tree, 'Host/source tree mismatch'
        for kind, prefix, extension in [('apk', 'unsigned-' + channel + '-release-', '.apk'),
                                         *([('aab', 'unsigned-bundle-', '.aab')] if channel == 'play' else [])]:
            found = [a for a in artifacts if a['name'] == prefix + built_head]
            assert len(found) == 1, 'Missing/ambiguous unsigned input'
            location = out / channel / kind
            receipt = archive(found[0], location)
            files = list(location.rglob('*' + extension))
            assert len(files) == 1, 'Missing/ambiguous payload'
            digest = sha(files[0])
            assert digest == provenance['artifactSha256'][kind], 'Payload/provenance hash mismatch'
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
