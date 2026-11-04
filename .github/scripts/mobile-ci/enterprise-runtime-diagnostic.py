#!/usr/bin/env python3
"""Temporary diagnostics only: observe the unchanged Enterprise test command."""
import datetime
import json
import os
import pathlib
import signal
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

OUTPUT = pathlib.Path(os.environ['GITHUB_WORKSPACE']) / 'diagnostic-evidence'
OUTPUT.mkdir(exist_ok=True)
JAVA_BIN = pathlib.Path(os.environ['JAVA_HOME']) / 'bin'
MAIN = 'id.walt.enterprise.test.integration.mobile.EnterpriseMobileFixtureServerKt'


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def read(command, timeout=5):
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=timeout)
        return {'exit': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {'error': type(error).__name__}


def safe_profile():
    profile = ET.parse(JAVA_BIN.parent / 'lib/jfr/profile.jfc')
    # JVM defaults record environment values and properties, which may include CI secrets.
    excluded = {'jdk.InitialEnvironmentVariable', 'jdk.InitialSystemProperty',
                'jdk.JVMInformation', 'jdk.ProcessStart', 'jdk.SystemProcess',
                'jdk.JavaExceptionThrow', 'jdk.JavaErrorThrow'}
    for event in profile.getroot().findall('event'):
        if event.get('name') in excluded:
            setting = next((s for s in event.findall('setting') if s.get('name') == 'enabled'), None)
            if setting is None:
                setting = ET.SubElement(event, 'setting', {'name': 'enabled'})
            setting.attrib.pop('control', None)
            setting.text = 'false'
    path = OUTPUT / 'fixture-safe-profile.jfc'
    profile.write(path, encoding='utf-8', xml_declaration=True)
    return path


def monitor(stop, profile):
    observed = set()
    last_stack = {}
    last_pulse = 0
    with (OUTPUT / 'runtime-samples.jsonl').open('w') as samples:
        while not stop.is_set():
            instant = time.monotonic()
            # Arguments are inspected only to identify this fixture; never retained.
            listing = read(['ps', '-axo', 'pid=,args=']).get('stdout', '')
            pids = {int(parts[0]) for line in listing.splitlines()
                    if len(parts := line.split()) > 2
                    and pathlib.Path(parts[1]).name == 'java' and MAIN in parts[2:]}
            record = {'at': now(), 'fixture_pids': sorted(pids),
                      'processes': read(['ps', '-axo', 'pid,ppid,%cpu,rss,etime,comm']),
                      'vm_stat': read(['vm_stat']),
                      'swap': read(['sysctl', 'vm.swapusage']),
                      'memory_pressure': read(['memory_pressure', '-Q'])}
            for pid in sorted(pids):
                if pid not in observed:
                    record[f'jfr_start_{pid}'] = read([
                        str(JAVA_BIN / 'jcmd'), str(pid), 'JFR.start',
                        'name=enterprise-ci', f'settings={profile}',
                        f'filename={OUTPUT / f"fixture-{pid}.jfr"}',
                        'dumponexit=true', 'maxsize=64m'], timeout=15)
                    observed.add(pid)
                    print(f'[diagnostic] {now()} observing fixture PID {pid}', flush=True)
                record[f'gc_{pid}'] = read([str(JAVA_BIN / 'jstat'), '-gcutil', str(pid)])
                if instant - last_stack.get(pid, 0) >= 15:
                    stack = read([str(JAVA_BIN / 'jcmd'), str(pid), 'Thread.print', '-l'], timeout=10)
                    with (OUTPUT / f'fixture-{pid}-threads.log').open('a') as output:
                        output.write(now() + '\n' + json.dumps(stack) + '\n')
                    last_stack[pid] = instant
            samples.write(json.dumps(record) + '\n')
            samples.flush()
            if instant - last_pulse >= 60:
                print(f'[diagnostic] {now()} sampling host; active fixture PIDs {sorted(pids)}', flush=True)
                last_pulse = instant
            stop.wait(5)


def main():
    command = sys.argv[1:]
    if command and command[0] == '--':
        command = command[1:]
    if not command:
        raise SystemExit('A test command is required')
    profile = safe_profile()
    (OUTPUT / 'host.json').write_text(json.dumps({
        'started': now(), 'hardware': read(['sysctl', 'hw.memsize', 'hw.ncpu']),
        'os': read(['sw_vers']), 'java': read([str(JAVA_BIN / 'java'), '-version']),
        'instrumented_diagnostic_only': True,
    }, indent=2))
    child = subprocess.Popen(command, start_new_session=True)
    stop = threading.Event()
    sampler = threading.Thread(target=monitor, args=(stop, profile), daemon=True)
    sampler.start()

    def terminate(signum, _frame):
        if child.poll() is None:
            os.killpg(child.pid, signum)

    signal.signal(signal.SIGTERM, terminate)
    signal.signal(signal.SIGINT, terminate)
    try:
        status = child.wait()
    finally:
        stop.set()
        sampler.join(timeout=35)
    (OUTPUT / 'diagnostic-command.json').write_text(json.dumps({'finished': now(), 'exit_code': status}))
    raise SystemExit(status if status >= 0 else 128 - status)


if __name__ == '__main__':
    main()
