// Karma's default port (9876) collides when multiple browser test tasks run concurrently under Gradle's
// parallel build - waltid-crypto2's own js and wasmJs targets each launch a Karma server from this same
// config, and other crypto modules (waltid-crypto2-kms, waltid-crypto2-examples, waltid-jose) launch their
// own concurrently too. The losing server's ChromeHeadless then crashes with "bind() failed: Address already
// in use". Derive the port from this Karma instance's own Node process id so concurrent instances don't
// collide.
config.set({
    port: 20000 + (process.pid % 10000)
});
