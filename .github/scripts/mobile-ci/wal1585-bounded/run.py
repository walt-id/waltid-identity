#!/usr/bin/env python3
"""One authorized native execution or one original/candidate Compose comparison."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

H = Path(__file__).resolve().parent
ROOT = Path(os.environ['GITHUB_WORKSPACE']).resolve()
I = ROOT / 'waltid-identity'
E = ROOT / 'waltid-identity-enterprise'
OUT = ROOT / 'bounded-repair-evidence' / sys.argv[1]
OUT.mkdir(parents=True, exist_ok=True)
MANIFEST = json.loads((H / 'manifest.json').read_text())
FRAMEWORK = Path(os.environ['RUNNER_TEMP']) / 'wallet-core-artifact'


def save(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2) + '\n')


def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def run(name, args, cwd=ROOT, *, check=True):
    started = time.time()
    with (OUT / (name + '.log')).open('wb') as log:
        code = subprocess.run(args, cwd=cwd, stdout=log, stderr=subprocess.STDOUT).returncode
    save(name + '.json', {'argv': args, 'cwd': str(cwd), 'started': started, 'ended': time.time(), 'exit_code': code})
    print(f'{name}: exit {code}', flush=True)
    if check and code:
        raise RuntimeError(f'{name} failed before proceeding; inspect retained log')
    return code


def output(args, cwd=ROOT):
    return subprocess.check_output(args, cwd=cwd, text=True).strip()


def apply(repo, name):
    path = H / name
    assert sha(path) == MANIFEST['patches'][name]
    subprocess.run(['git', 'apply', '--check', str(path)], cwd=repo, check=True)
    subprocess.run(['git', 'apply', str(path)], cwd=repo, check=True)
    shutil.copyfile(path, OUT / name)


def reverse(repo, name):
    subprocess.run(['git', 'apply', '--reverse', str(H / name)], cwd=repo, check=True)


def common():
    assert os.environ['GITHUB_RUN_ATTEMPT'] == '1', 'No automatic repeat of this authorized execution'
    assert os.environ['WAL1585_AUTHORIZATION'] == 'bounded-20261027-224628'
    roots = {'identity': I, 'enterprise': E, 'unified': ROOT, 'enterprise_license': ROOT / 'waltid-identity-enterprise-license'}
    actual = {k: output(['git', 'rev-parse', 'HEAD'], v) for k, v in roots.items()}
    save('actual-checkouts.json', actual)
    assert actual == MANIFEST['tuple'], (actual, MANIFEST['tuple'])
    version = output(['xcodebuild', '-version'])
    assert 'Xcode 27.0\n' in version and '27A266a' in version, version
    save('toolchain.json', {'xcode': version, 'developer_dir': os.environ['DEVELOPER_DIR'], 'platform': 'iPhone 17 / iOS 27.0'})
    tar = FRAMEWORK / 'WalletCore.xcframework.tar.gz'
    # The manifest names the tar; discover one tar only, never substitute a new framework.
    if not tar.exists():
        candidates = list(FRAMEWORK.glob('*.tar.gz'))
        assert len(candidates) == 1, candidates
        tar = candidates[0]
    assert sha(tar) == MANIFEST['framework']['tar_sha256']
    run('framework-verify', ['python3', '.github/scripts/mobile-ci/wallet-core-artifact.py', 'verify', '--artifact-dir', str(FRAMEWORK)], I)
    shutil.copyfile(FRAMEWORK / 'wallet-core.json', OUT / 'wallet-core.json')
    shutil.copyfile(H / 'manifest.json', OUT / 'harness-manifest.json')


def native():
    apply(I, 'native-observation.patch')
    apply(E, 'native-selection.patch')
    method = I / 'waltid-applications/waltid-wallet-demo-ios/iosApp/iosAppUITests/BatchIssuanceE2ETests.swift'
    pristine = subprocess.check_output(['git', 'show', 'HEAD:' + str(method.relative_to(I))], cwd=I)
    assert method.read_bytes() == pristine
    save('original-method.json', {'sha256': sha(method), 'unchanged': True, 'selection': 'iosAppUITests/BatchIssuanceE2ETests/testExplicitCopiesSurviveRelaunch', 'executions': 1})
    result = run('native-enterprise', ['./gradlew', ':waltid-enterprise-integration-tests:enterpriseIosMobileIntegrationTest', '--no-configuration-cache', '-Penterprise.ios.destination=platform=iOS Simulator,name=iPhone 17,OS=27.0', '-Penterprise.ios.collectTestDiagnostics=on-failure', '-Penterprise.ios.walletCoreArtifact=' + str(FRAMEWORK)], check=False)
    reports = E / 'waltid-enterprise-integration-tests/build/ios-test-results/enterprise-mobile'
    if reports.exists():
        shutil.copytree(reports, OUT / 'enterprise-results')
    save('completion.json', {'exit_code': result, 'execution_marker_present': (reports / 'execution-started.json').exists(), 'no_runtime_extension': True})
    return result


def product_receipt(derived, xctestrun):
    products = derived / 'Build/Products'
    binaries = {}
    for path in products.rglob('*'):
        if not path.is_file():
            continue
        if (path.parent.suffix in ('.app', '.xctest') and path.name == path.parent.stem) or (path.name == 'WalletCore' and path.parent.suffix == '.framework'):
            binaries[str(path.relative_to(products))] = sha(path)
    return {'xctestrun': str(xctestrun), 'xctestrun_sha256': sha(xctestrun), 'built_binary_hashes': binaries}


def compose():
    project = I / 'waltid-applications/waltid-wallet-demo-compose/iosApp'
    selection = 'iosAppUITests/PublicDemoBackendE2ETests/testAutomaticallyAttestsEudiIssuanceBeforeAndAfterRestart'
    test_file = project / 'iosAppUITests/PublicDemoBackendE2ETests.swift'
    pristine = subprocess.check_output(['git', 'show', 'HEAD:' + str(test_file.relative_to(I))], cwd=I)
    assert test_file.read_bytes() == pristine
    save('original-method.json', {'sha256': sha(test_file), 'unchanged': True, 'selection': selection})
    prepared = []
    cleanup_errors = []
    try:
        # Build both arms before consuming either runtime budget. Two fresh simulators,
        # same exact product source, framework and test body; only helper differs.
        for arm in ('original', 'candidate'):
            simulator = output(['xcrun', 'simctl', 'create', 'WAL1585 Compose ' + arm, 'iPhone 17', 'iOS 27.0'])
            derived = Path(os.environ['RUNNER_TEMP']) / ('wal1585-compose-' + arm)
            item = {'arm': arm, 'simulator': simulator, 'derived': derived}
            prepared.append(item)
            patch = 'compose-' + arm + '.patch'
            apply(I, patch)
            try:
                run('compose-' + arm + '-build', ['xcodebuild', 'build-for-testing', '-project', 'iosApp.xcodeproj', '-scheme', 'iosApp', '-configuration', 'Debug', '-destination', 'platform=iOS Simulator,id=' + simulator, '-derivedDataPath', str(derived), '-only-testing:' + selection, '-enableCodeCoverage', 'NO', '-parallel-testing-enabled', 'NO', 'GRADLE_OPTS=' + os.environ.get('GRADLE_OPTS', '') + ' -Dorg.gradle.daemon=false'], project)
                specs = list((derived / 'Build/Products').glob('*.xctestrun'))
                assert len(specs) == 1, specs
                item['xctestrun'] = specs[0]
                save('compose-' + arm + '-products.json', product_receipt(derived, specs[0]))
            finally:
                reverse(I, patch)
        results = {}
        for item in prepared:
            arm, simulator = item['arm'], item['simulator']
            run('compose-' + arm + '-boot', ['xcrun', 'simctl', 'bootstatus', simulator, '-b'])
            marker = OUT / ('execution-' + arm + '-started.json')
            with marker.open('x') as stream:
                json.dump({'arm': arm, 'selection': selection, 'time': time.time(), 'maximum_case_executions': 1, 'repeat_options': []}, stream)
            results[arm] = run('compose-' + arm + '-test', ['xcodebuild', 'test-without-building', '-xctestrun', str(item['xctestrun']), '-destination', 'platform=iOS Simulator,id=' + simulator, '-only-testing:' + selection, '-parallel-testing-enabled', 'NO', '-enableCodeCoverage', 'NO', '-collect-test-diagnostics', 'on-failure', '-resultBundlePath', str(OUT / (arm + '.xcresult'))], project, check=False)
            run('compose-' + arm + '-shutdown', ['xcrun', 'simctl', 'shutdown', simulator], check=False)
        save('completion.json', {'exit_codes': results, 'one_comparison_only': True, 'no_runtime_extension': True})
        return int(any(results.values()))
    finally:
        for item in prepared:
            code = run('compose-' + item['arm'] + '-delete', ['xcrun', 'simctl', 'delete', item['simulator']], check=False)
            if code:
                cleanup_errors.append(item['simulator'])
        save('owned-simulator-cleanup.json', {'failed_deletions': cleanup_errors})
        if cleanup_errors:
            raise RuntimeError('Owned simulator cleanup failed')


if __name__ == '__main__':
    common()
    if sys.argv[1] == 'native':
        sys.exit(native())
    if sys.argv[1] == 'compose':
        sys.exit(compose())
    raise ValueError('Unknown bounded scope')
