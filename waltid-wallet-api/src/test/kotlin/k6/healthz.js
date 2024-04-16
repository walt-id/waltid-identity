import http from "k6/http";
import {check, sleep} from "k6";
import {Counter} from "k6/metrics";

/**
 * This test simulates 10k users/processes that are all hitting the health-check once a second.
 *
 */

export const options = {
    // target defines the parallel processes that call the system
    stages: [
        {duration: "30s", target: 10000}, // ramp up
        {duration: "5m", target: 10000}, // stable
        {duration: "30s", target: 0}, // ram-down to 0 users
    ],
    thresholds: {
        http_req_duration: ['p(99)<100'] // 99% of requests must complete within 100ms
    },
};

export const successCounter = new Counter("success_count");

export default function () {

    const resp = http.get("http://localhost:7001/wallet-api/healthz");

    check(resp, {
        "success": (r) => {
            if (r.status === 200) {
                successCounter.add(1);
                return true;
            }
            return false;
        },
    });
    sleep(1); // one request iteration per second
}
