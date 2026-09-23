// The software-key algorithm support matrix generates a key per specification, which outlasts the 2s Mocha
// default in Karma-driven browser runs.
config.set({
    client: {
        mocha: {
            timeout: 600000
        }
    }
});
