import http from 'k6/http';
import {check} from 'k6';

export let options = {
    stages: [
        { duration: '60s', target: 20 },  // Stay at 20 users for 1 minute
    ],
    thresholds: {
        'http_req_duration': ['p(95)<500'], // 95% of requests must complete below 500ms
    },
};

export default function () {
    const url = 'http://127.0.0.1:7001/wallet-api/auth/login';
    const payload = JSON.stringify({
        type: 'email',
        email: 'user@email.com',
        password: 'password',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    let response = http.post(url, payload, params);

    check(response, {
        'is status 200': (r) => r.status === 200,
        'is response correct': (r) => r.json().hasOwnProperty('token') && r.json().username === 'user@email.com',
    });
}
