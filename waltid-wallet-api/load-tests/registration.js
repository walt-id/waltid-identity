import http from 'k6/http';
import { sleep, check } from 'k6';

export let options = {
    stages: [
        { duration: '1m', target: 24 },
    ],
    thresholds: {
        checks: ['rate==1.00'],
        http_req_duration: [{threshold: 'p(95) < 500', abortOnFail: true}], // terminate the process if the response time increases to more than 1s for more than 5% of the requests
    },
};

function randomIntBetween(min, max) {
    return Math.floor(Math.random() * (max - min + 1) + min);
}

function generateRandomAsciiString(length) {
    let result = '';
    for (let i = 0; i < length; i++) {
        // ASCII codes 32 to 126 cover printable characters
        const asciiCode = randomIntBetween(32, 126);
        result += String.fromCharCode(asciiCode);
    }
    return result;
}

export default function () {
    const url = 'http://127.0.0.1:7001/wallet-api/auth/create';
    const payload = JSON.stringify({
        type: 'email',
        name: 'my name',
        email: generateRandomAsciiString(16) + '@email.com',
        password: 'password',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    let response = http.post(url, payload, params);

    const isSuccess = check(response, {
            'status is 201': (r) => r.status === 201,
        },
        {statusCodeTag: 'httpOk'}
    );

    if (!isSuccess) {
    fail(`- Response returned error code: ${response.status}, ${response.body}`)
    }
}
