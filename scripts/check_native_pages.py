#!/usr/bin/env python3
"""Audit APK ELF/ZIP layout and exercise its native path library on a disposable 16 KB AVD.

RELRO's documented formula is recorded literally, never rounded into a pass.
Runtime success is scoped to x86_64; it is not an arm64 or Play certification.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import time
import traceback
import zipfile

PACKAGE = 'dev.burooj.speedbreaker'
LIBRARY = 'libandroidx.graphics.path.so'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def zip_data_offset(apk_path, entry):
    with apk_path.open('rb') as source:
        source.seek(entry.header_offset)
        local = source.read(30)
    name_size, extra_size = struct.unpack_from('<HH', local, 26)
    return entry.header_offset + 30 + name_size + extra_size


class Audit:
    def __init__(self, args):
        self.args = args
        self.output = args.output
        self.output.mkdir(parents=True, exist_ok=True)
        self.report = {'result': 'RUNNING', 'mode': args.mode,
                       'source_revision': os.environ.get('GITHUB_SHA'),
                       'run_url': os.environ.get('GITHUB_SERVER_URL', 'https://github.com') + '/' +
                       os.environ.get('GITHUB_REPOSITORY', '') + '/actions/runs/' +
                       os.environ.get('GITHUB_RUN_ID', ''),
                       'scope': 'x86_64 runtime only; all packaged ABIs inspected statically; no arm64 hardware test',
                       'apk': str(args.apk), 'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest()}
        self.index = 0

    def command(self, command, check=True, timeout=120, binary=False):
        self.index += 1
        stem = self.output / f'{self.index:03d}-{Path(command[0]).name}'
        stem.with_suffix('.command.json').write_text(json.dumps(command))
        try:
            proc = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        except subprocess.TimeoutExpired as failure:
            stem.with_suffix('.stdout').write_bytes(failure.stdout or b'')
            stem.with_suffix('.stderr').write_bytes(failure.stderr or b'')
            raise
        stem.with_suffix('.stdout').write_bytes(proc.stdout)
        stem.with_suffix('.stderr').write_bytes(proc.stderr)
        if check:
            require(proc.returncode == 0, f'Command failed ({proc.returncode}): {command}; see {stem}')
        return proc.stdout if binary else proc.stdout.decode('utf-8', errors='replace').strip()

    def adb(self, *args, **kwargs):
        return self.command([self.args.adb, '-s', 'emulator-5554', *args], **kwargs)

    def static(self):
        findings = []
        with zipfile.ZipFile(self.args.apk) as apk:
            for entry in apk.infolist():
                if not entry.filename.startswith('lib/') or not entry.filename.endswith('.so'):
                    continue
                data = apk.read(entry)
                require(data[:4] == b'\x7fELF' and data[5] == 1, 'Expected little-endian ELF')
                is64 = data[4] == 2
                require(data[4] in (1, 2), 'Unknown ELF class')
                header = struct.unpack_from('<HHIQQQIHHHHHH' if is64 else '<HHIIIIIHHHHHH', data, 16)
                phoff, phentsize, phnum = header[4], header[8], header[9]
                segments = []
                for index in range(phnum):
                    values = struct.unpack_from('<IIQQQQQQ' if is64 else '<IIIIIIII', data,
                                                phoff + index * phentsize)
                    if is64:
                        kind, flags, offset, virtual, physical, filesz, memsz, alignment = values
                    else:
                        kind, offset, virtual, physical, filesz, memsz, flags, alignment = values
                    if kind in (1, 0x6474e552):
                        segment = {'type': 'LOAD' if kind == 1 else 'GNU_RELRO',
                                   'offset': hex(offset), 'vaddr': hex(virtual),
                                   'memsize': hex(memsz), 'alignment': hex(alignment)}
                        if kind == 0x6474e552:
                            segment.update(end=hex(virtual + memsz),
                                           end_mod_16384=(virtual + memsz) % 16384,
                                           documented_formula_pass=(virtual + memsz) % 16384 == 0)
                        segments.append(segment)
                abi = entry.filename.split('/')[1]
                loads = [s for s in segments if s['type'] == 'LOAD']
                load_pass = bool(loads) and all(int(s['alignment'], 16) >= 16384 for s in loads)
                zip_offset = zip_data_offset(self.args.apk, entry)
                target = self.output / 'elf' / entry.filename
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                readelf = self.command([self.args.readelf, '-Wl', str(target)])
                target.with_suffix('.readelf.txt').write_text(readelf + '\n')
                findings.append({'path': entry.filename, 'sha256': hashlib.sha256(data).hexdigest(),
                                 'segments': segments, 'load_alignment_pass': load_pass,
                                 'zip_compression': entry.compress_type, 'zip_data_offset': zip_offset,
                                 'zip_16kb_aligned': zip_offset % 16384 == 0,
                                 'required_64bit_alignment': abi in ('arm64-v8a', 'x86_64')})
        self.report['native_libraries'] = findings
        self.report['static_gate_scope'] = '64-bit LOAD and zipalign only; RELRO formula reported separately'
        self.report['relro_formula_warnings'] = [f['path'] for f in findings if any(
            s.get('documented_formula_pass') is False for s in f['segments'])]
        self.report['documented_relro_formula_pass'] = not self.report['relro_formula_warnings']
        # Persist findings even when a subsequent gate fails.
        self.save()
        require(any(f['path'] == 'lib/x86_64/' + LIBRARY for f in findings), 'Missing x86_64 path library')
        require(all(f['load_alignment_pass'] for f in findings if f['required_64bit_alignment']),
                '64-bit ELF LOAD alignment below 16 KB')
        self.command([self.args.zipalign, '-v', '-c', '-P', '16', '4', str(self.args.apk)])
        self.report['zipalign_P16_pass'] = True

    def runtime(self):
        # Explicit serial and AVD identity prevent applying global properties to user devices.
        require(self.adb('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Not an emulator')
        require(self.adb('emu', 'avd', 'name').splitlines()[0] == 'native-pages-ci', 'Wrong disposable AVD')
        require(self.adb('shell', 'getconf', 'PAGE_SIZE') == '16384', 'Not a 16 KB emulator')
        require(self.adb('shell', 'getprop', 'ro.build.version.sdk') == '36', 'Not API 36')
        require(self.adb('shell', 'getprop', 'ro.product.cpu.abi') == 'x86_64', 'Not x86_64')
        self.report['device'] = {'serial': 'emulator-5554', 'page_size': 16384, 'api': 36,
                                 'abi': 'x86_64', 'fingerprint': self.adb('shell', 'getprop', 'ro.build.fingerprint')}
        self.report['device']['meminfo'] = self.adb('shell', 'cat', '/proc/meminfo')
        self.save()
        # adbd can close the root request while the newly booted emulator reconnects.
        # Its process exit code is not the root-state assertion; verified UID 0 is.
        rooted = False
        for attempt in range(3):
            self.adb('root', check=False, timeout=30)
            self.adb('wait-for-device', timeout=30)
            if self.adb('shell', 'id', '-u', check=False) == '0':
                rooted = True
                break
            time.sleep(2)
        require(rooted, 'Disposable emulator root unavailable after bounded reconnects')
        props = {'bionic.linker.16kb.app_compat.enabled': 'false',
                 'pm.16kb.app_compat.disabled': 'true'}
        for key, value in props.items():
            self.adb('shell', 'setprop', key, value)
            actual = self.adb('shell', 'getprop', key)
            self.report.setdefault('compat_properties', {})[key] = actual
            require(actual == value, f'Compat property did not stick: {key}')
        self.save()
        self.adb('logcat', '-c')
        self.adb('install', '-r', str(self.args.apk))
        self.adb('install', '-r', str(self.args.fixture))
        self.adb('shell', 'pm', 'clear', PACKAGE)
        with zipfile.ZipFile(self.args.apk) as apk:
            native_offset = zip_data_offset(self.args.apk, apk.getinfo('lib/x86_64/' + LIBRARY))
        self.report['native_zip_offset'] = native_offset
        instrumentation = self.adb('shell', 'am', 'instrument', '-w', '-r',
                                   '-e', 'native_zip_offset', str(native_offset),
                                   'dev.burooj.speedbreaker.fixture/.PageSizeRegression', timeout=180)
        (self.output / 'instrumentation.txt').write_text(instrumentation + '\n')
        # Collect Java failure evidence before interpreting am's result (its exit code is insufficient).
        native = self.adb('shell', 'run-as', PACKAGE, 'cat', 'files/native-pages.json', check=False)
        (self.output / 'native-pages.json').write_text(native + '\n')
        maps = self.adb('shell', 'run-as', PACKAGE, 'cat', 'files/native-pages-maps.txt', check=False)
        (self.output / 'native-pages-maps.txt').write_text(maps + '\n')
        require(re.search(r'^INSTRUMENTATION_RESULT: result=PASS$', instrumentation, re.M),
                'Native instrumentation did not explicitly pass')
        evidence = json.loads(native)
        self.report['instrumentation'] = evidence
        require(evidence['result'] == 'PASS' and evidence['page_size'] == 16384
                and evidence['native_library_mapped'], 'Incomplete native evidence')
        mapped = False
        for line in maps.splitlines():
            columns = line.split(maxsplit=5)
            if len(columns) != 6 or 'x' not in columns[1]:
                continue
            if ((columns[5] == evidence['source_apk'] and int(columns[2], 16) == native_offset)
                    or columns[5] == evidence['native_library_dir'] + '/' + LIBRARY):
                mapped = True
        require(mapped and evidence['native_zip_offset'] == native_offset,
                'Missing executable target-library mapping at audited APK offset')
        for key in ('public_iterator', 'native_conic_iterator'):
            require(evidence[key]['segments'] > 0 and evidence[key]['cubics'] > 0
                    and evidence[key]['finite_coordinates'], 'Missing finite cubic iteration')
        require(evidence['public_iterator']['conics'] > 0
                and evidence['native_conic_iterator']['conics'] == 0
                and evidence['native_conic_iterator']['quadratics'] > 0,
                'Supported JNI conic conversion was not exercised')
        self.adb('shell', 'am', 'force-stop', PACKAGE)
        launch = self.adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
        require('Status: ok' in launch, 'App launch failed')
        deadline = time.monotonic() + 45
        ui = ''
        while time.monotonic() < deadline:
            self.adb('shell', 'uiautomator', 'dump', '/sdcard/native-pages-ui.xml', check=False)
            ui = self.adb('shell', 'cat', '/sdcard/native-pages-ui.xml', check=False)
            (self.output / 'welcome.xml').write_text(ui)
            if 'Make the next tap intentional.' in ui and PACKAGE in ui:
                break
            time.sleep(2)
        (self.output / 'welcome.png').write_bytes(self.adb('exec-out', 'screencap', '-p', binary=True))
        require('Make the next tap intentional.' in ui and PACKAGE in ui, 'Welcome screen absent')
        time.sleep(3)
        pid = self.adb('shell', 'pidof', PACKAGE)
        require(bool(re.fullmatch(r'\d+', pid)), 'App did not remain alive')
        self.report['welcome_pid'] = pid
        self.report['welcome_visible'] = True
        for key, expected in props.items():
            require(self.adb('shell', 'getprop', key) == expected, 'Compat property changed during test')
        self.check_crashes()

    def check_crashes(self):
        log = self.adb('logcat', '-b', 'all', '-d')
        (self.output / 'logcat.txt').write_text(log + '\n')
        crash = self.adb('logcat', '-b', 'crash', '-d')
        (self.output / 'crash.txt').write_text(crash + '\n')
        # Fresh disposable AVD, log cleared before installation: any fatal signal is a failure.
        require(not re.search(r'Fatal signal|SIGSEGV|FATAL EXCEPTION|>>> dev\.burooj\.speedbreaker',
                              log + '\n' + crash), 'Crash detected; inspect retained logcat')
        self.report['no_crash_observed'] = True

    def save(self):
        (self.output / 'summary.json').write_text(json.dumps(self.report, indent=2) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--fixture', type=Path)
    parser.add_argument('--mode', choices=('static', 'runtime'), required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--readelf', default='readelf')
    parser.add_argument('--zipalign', default='zipalign')
    args = parser.parse_args()
    audit = Audit(args)
    try:
        if args.mode == 'static':
            audit.static()
        else:
            require(args.fixture is not None, '--fixture required for runtime')
            audit.runtime()
        audit.report['result'] = 'PASS'
    except Exception as failure:
        audit.report.update(result='FAIL', failure=str(failure), traceback=traceback.format_exc())
        raise
    finally:
        if args.mode == 'runtime':
            try:
                audit.check_crashes()
                audit.adb('shell', 'dumpsys', 'activity', 'activities')
                audit.adb('shell', 'dumpsys', 'package', PACKAGE)
            except Exception as failure:
                audit.report.update(result='FAIL', collection_failure=str(failure))
        audit.save()
        print(json.dumps(audit.report, indent=2))
    # A late failure in finally must fail CI even when runtime() returned successfully.
    require(audit.report['result'] == 'PASS', 'Evidence collection or crash gate failed')


if __name__ == '__main__':
    main()
