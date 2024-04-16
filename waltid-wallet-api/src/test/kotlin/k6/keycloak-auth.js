import http from "k6/http";
import {check, sleep} from "k6";
import {Counter} from "k6/metrics";

export const options = {
    stages: [
        {duration: "10m", target: 10000},
        {duration: "5m", target: 200},
        {duration: "5m", target: 100},
    ],
};

export const totalUsersCreated = new Counter("total_users_created");
export const totalUsersLoggedIn = new Counter("total_users_logged_in");

export default function () {

    const tokenResponse = http.get(
            "http://localhost:7001/wallet-api/auth/keycloak/token"
        );
        check(tokenResponse, {
            "access token generated": (r) => r.status == 200,
        });
        // Loop 10,000 times to create 10,000 users
        const username = Math.random().toString(36).slice(2, 15) + "@gmail.com";
        const email = username;
        const password = "test";

        // Register user
        const registerResponse = http.post(
            "http://localhost:7001/wallet-api/auth/keycloak/create",
            JSON.stringify({
                username: username,
                email: email,
                password: password,
                type: "keycloak",
                token: tokenResponse.body,
            }),
            {headers: {"Content-Type": "application/json"}}
        );

        check(registerResponse, {
            "user created": (r) => {
                if (r.status === 201) {
                    totalUsersCreated.add(1);
                    return true;
                }
                return false;
            },
        });

        // Login user
        const loginResponse = http.post(
            "http://localhost:7001/wallet-api/auth/keycloak/login",
            JSON.stringify({
                username: username,
                password: password,
                type: "keycloak",
                token: tokenResponse.body,
            }),
            {headers: {"Content-Type": "application/json"}}
        );

        check(loginResponse, {
            "user logged in": (r) => r.status === 200,
            "wallet api returning JWT": (r) =>
                r.body.includes("token") && r.body.includes("id"),
        });
        totalUsersLoggedIn.add(1);

}
