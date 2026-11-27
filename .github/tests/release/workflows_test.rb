require 'json'
require 'minitest/autorun'
require 'open3'
require 'tmpdir'
require 'fileutils'
require 'yaml'

# Run the actual workflow shell with deterministic responses from GitHub and the registry.
class ReleaseWorkflowsTest < Minitest::Test
  def setup
    @dir = Dir.mktmpdir
    @env = { 'PATH' => "#{@dir}:#{ENV.fetch('PATH')}", 'RUNNER_TEMP' => @dir,
             'GITHUB_OUTPUT' => "#{@dir}/output", 'VERSION' => '1.3.0',
             'RUN_URL' => 'https://api.github.com/repos/example/test/actions/runs/1' }
  end

  def teardown
    FileUtils.remove_entry(@dir)
  end

  def steps(workflow, job)
    file = File.expand_path("../../workflows/#{workflow}.yml", __dir__)
    YAML.safe_load(File.read(file), aliases: true).fetch('jobs').fetch(job).fetch('steps')
  end

  def stub(name, script)
    path = File.join(@dir, name)
    File.write(path, "#!/bin/bash\nset -eu\n#{script}\n")
    File.chmod(0o700, path)
  end

  def run_step(step, env = {})
    output, status = Open3.capture2e(@env.merge(env), 'bash', '-e', '-o', 'pipefail', '-c', step.fetch('run'), chdir: @dir)
    [status.success?, output]
  end

  def test_dispatch_waits_for_the_child_and_propagates_its_conclusion
    wait = steps('release-candidate', 'integration-test-aws').find { |step| step['name'] == 'Require the dispatched run to succeed' }
    stub('sleep', ':')
    stub('gh', <<~'SH')
      [[ "$1" == api && "$2" == "$RUN_URL" ]]
      if [[ ! -f polled ]]; then touch polled; echo in_progress/; else echo "completed/$RESULT"; fi
    SH
    %w[success failure cancelled].each do |result|
      FileUtils.rm_f(File.join(@dir, 'polled'))
      ok, output = run_step(wait, 'RESULT' => result)
      assert_equal(result == 'success', ok, output)
      assert File.exist?(File.join(@dir, 'polled'))
    end
  end

  def test_jib_digest_must_match_the_published_image
    record = steps('api-docker', 'docker').find { |step| step['id'] == 'provenance' }
    digest = 'sha256:' + 'd' * 64
    File.write(File.join(@dir, 'jib-image.digest'), digest)
    stub('git', "echo #{'a' * 40}")
    stub('docker', %q{echo "{\"digest\":\"$PUBLISHED\"}"})
    env = { 'IMAGE' => 'docker.io/example/api', 'RELEASE_VERSION' => '1.3.0', 'JIB_TASK' => ':enterprise:jib' }
    [digest, 'sha256:' + 'e' * 64].each do |published|
      ok, output = run_step(record, env.merge('PUBLISHED' => published))
      assert_equal(digest == published, ok, output)
    end
  end

  def test_promotion_requires_provenance_for_the_selected_digest_and_tagged_inputs
    verify = steps('promote', 'retag').find { |step| step['id'] == 'jib' }
    sha = 'a' * 40
    refs = %w[unified identity enterprise enterprise_license].to_h { |key| [key, sha] }
    digest = 'sha256:' + 'b' * 64
    image = 'waltid-enterprise-api'
    fixture = '.github/workflows/data/api-docker-matrix.json'
    FileUtils.mkdir_p(File.join(@dir, File.dirname(fixture)))
    File.write(File.join(@dir, fixture), [{ image: image, commandPath: ':enterprise' }].to_json)
    stub('docker', %q{[[ "$*" == *"docker.io/waltid/waltid-enterprise-api:1.3.0"* ]]; echo "{\"digest\":\"$DIGEST\"}"})
    stub('gh', <<~'SH')
      [[ "$1 $2" == "attestation verify" && "$3" == "oci://docker.io/waltid/waltid-enterprise-api@$DIGEST" ]]
      [[ " $* " == *" --source-digest $IDENTITY_SHA "* && " $* " == *" --signer-workflow walt-id/waltid-identity/.github/workflows/api-docker.yml "* ]]
      if [[ "$*" == *'--format json'* ]]; then cat attestation.json; fi
    SH
    env = { 'DIGEST' => digest, 'CHECKOUT_REFS' => refs.to_json, 'IDENTITY_SHA' => sha }
    [refs, refs.merge('enterprise' => 'c' * 40)].each do |signed|
      predicate = { version: '1.3.0', gradleTask: ':enterprise:jib', checkoutRefs: signed }
      File.write(File.join(@dir, 'attestation.json'), [{ verificationResult: { statement: { predicate: predicate } } }].to_json)
      ok, output = run_step(verify, env)
      assert_equal(signed == refs, ok, output)
      assert_includes File.read(@env.fetch('GITHUB_OUTPUT')), "enterprise-api-digest=#{digest}" if ok
    end
  end
end
