import http from "k6/http";
import {check, sleep} from "k6";
import {Counter} from "k6/metrics";
import {SharedArray} from 'k6/data';
import {scenario} from 'k6/execution';

/**
 * This test registers 10k users whereas the test will fail if more than 5% of the requests will take more than 1s.
 */

let arraySize = 100000;

function generateArray() {
    const arr = new Array(arraySize);
    for (let i = 0; i < arraySize; i++) {

        const username = "user" + i;
        const email = username + "@gmail.com";

        arr[i] = {name: username, email: email, password: 'test', type: 'email'};
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
            vus: 8,
            iterations: data.length,
            maxDuration: '30m',
        },

    },
    thresholds: {
        http_req_failed: ['rate<0.01'], // http errors should be less than 1%        
        //http_req_duration: ['p(90) < 400', 'p(95) < 800', 'p(99.9) < 2000'], // 90% of requests must finish within 400ms, 95% within 800, and 99.9% within 2s.
        http_req_duration: [{threshold: 'p(95) < 1000', abortOnFail: true}], // terminate the process if the response time increases to more than 1s for more than 5% of the requests
        'checks{statusCodeTag:httpOk}': ['rate>0.99'], // HTTP status code must return 201 for more than 99%
    },
};

export default function () {

    const user = data[scenario.iterationInTest];

    // Register user
    const resp = http.post(
        "http://localhost:7001/wallet-api/auth/create",
        JSON.stringify(user),
        {headers: {"Content-Type": "application/json"}}
    );

    check(resp, {
            'status is 201': (r) => r.status == 201,
        },
        {statusCodeTag: 'httpOk'}
    );
    sleep(0.5); // one request iteration per second
}