Pod::Spec.new do |spec|
    spec.name                     = 'sharedUI'
    spec.version                  = '1.0'
    spec.homepage                 = 'https://walt.id'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'Shared Compose UI for the walt.id wallet demo'
    spec.vendored_frameworks      = 'build/cocoapods/framework/sharedUI.framework'
    spec.ios.deployment_target    = '15.4'
    if !Dir.exist?('build/cocoapods/framework/sharedUI.framework') || Dir.empty?('build/cocoapods/framework/sharedUI.framework')
        raise "
        Kotlin framework 'sharedUI' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:
            ./gradlew :waltid-applications:waltid-wallet-demo-compose:sharedUI:generateDummyFramework
        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':waltid-applications:waltid-wallet-demo-compose:sharedUI',
        'PRODUCT_MODULE_NAME' => 'sharedUI',
    }
    spec.script_phases = [
        {
            :name => 'Build sharedUI',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                    echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                    exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../../../../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -PenableIosBuild=true \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
    spec.resources = ['build/compose/cocoapods/compose-resources']
    spec.libraries = 'c++', 'sqlite3'
end
