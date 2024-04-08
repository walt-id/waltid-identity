import http from "k6/http";
import {check} from "k6";
import {Counter} from "k6/metrics";
import {SharedArray} from 'k6/data';
import {scenario} from 'k6/execution';

/**
 * This test case assumes that the database is pre-populated with 10k user accounts, where each will be used for running
 * the login-scenario. 10 parallel process will call the login endpoint once a second. 95% of the calls must return
 * within 1second, otherwise the test will terminate with a failure. Furthermore, 99.9% of the http-response codes must
 * be 200.
 */
const registerdUsers = 10000;

let arraySize = registerdUsers;

function generateArray() {
    const arr = new Array(arraySize);
    for (let i = 0; i < arraySize; i++) {

        const username = "user" + i;
        const email = username + "@gmail.com";

        arr[i] = {email: email, password: 'test', type: 'email'};
    }
    return arr;
}

let data = new SharedArray('user data', generateArray);
export const successCounter = new Counter("success_count");

export const options = {
    // target defines the parallel processes that call the system
    scenarios: {
        'register-user-data': {
            executor: 'shared-iterations',
            vus: 10,
            iterations: registerdUsers,
            maxDuration: '30m',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'], // http errors should be less than 1%
        http_req_duration: [{threshold: 'p(95) < 1000', abortOnFail: true}], // terminate the process if the response time increases to more than 1s for more than 1% of the requests
        'checks{statusCodeTag:httpOk}': ['rate>0.99'], // HTTP status code must be OK for more than 99%
    },
};

export default function () {

    //const user = data[randomIntBetween(0, 10000)];
    const user = data[scenario.iterationInTest];

    // Login user
    const resp = http.post(
        "http://localhost:7001/wallet-api/auth/login",
        JSON.stringify(user),
        {headers: {"Content-Type": "application/json"}}
    );

    if (resp.status != 200) {
        console.log(resp.status);
        console.log(resp);
    }

    check(resp, {
            'status is 200': (r) => r.status == 200,
        },
        {statusCodeTag: 'httpOk'}
    );
    //sleep(0.1); // one request iteration per second
}
