# Community Stack Integration Test

This module replaces _waltid-e2e-test_

## How it works

The tests are based on JUnit. 

To start the Ktor application before a test run, a TestExecutionListener is implemented
(`id.walt.test.integration.junit.IntegrationTestRunListener`). To be sure, the listener is applied to the test execution,
the listener is registered as Java Service (`resources/META-INF/services/org.junit.platform.launcher.TestExecutionListener`)

## Write tests

All tests should be located in the source root folder `src/main/kotlin`. 
They should be located in the  `id.walt.test.integration.tests` package or a sub package. 

Test should be written in the way, that they can be executed independently of each other
so the order or test execution doesn't matter. 

All tests should inherit from `id.walt.test.integration.tests.AbstractIntegrationTest`, which
deletes all credentials and categories in the _setup_ and _teardown_

## Execute tests

### Execute with gradle
Execute
```
./gradlew :waltid-services:waltid-integration-tests:test
```
in the project root folder

### Execute with IntelliJ IDE

To run all tests, right-click on the folder `src/main/kotlin`
and select _Run Tests_ in the context menu.

If you want to run a single test, right-click on the test class
end select _Run ..._ from the context menu

If configuration cannot be loaded, you need to update the run-configuration
and set the working directory to the module root directory