import random
import requests
import string
import unittest


class WalletAPI2Test(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.wallet_url = "http://localhost:7006"
        cls.headers = {"Content-Type": "application/json"}
        cls.name = ''.join(random.choices(string.ascii_letters + string.digits, k=8))
        cls.email = f"{cls.name}@example.com"
        cls.password = "password"

    def register_user(self):
        print("register user")
        register_url = f"{self.wallet_url}/auth/register"
        payload = {"email": self.email, "password": self.password}
        response = requests.post(register_url, json=payload, headers=self.headers)
        assert response.status_code == 201, f"Expected status code 201 but got {response.status_code}"
        print(response.content)

    def login_user(self):
        print("\nLogin user")
        login_url = f"{self.wallet_url}/auth/emailpass"
        payload = {"email": self.email, "password": self.password}
        response = requests.post(login_url, json=payload, headers=self.headers)
        assert response.status_code == 200, f"Expected status code 200 but got {response.status_code}"
        self.token = response.json().get("token")
        assert self.token, "Login succeeded but no token was returned"
        self.headers["Authorization"] = f"Bearer {self.token}"
        print(self.token)

    def create_wallet(self):
        print("\nCreate wallet")
        create_wallet_url = f"{self.wallet_url}/wallet"
        response = requests.post(create_wallet_url, json={}, headers=self.headers)
        assert response.status_code == 201, f"Expected status code 201 but got {response.status_code}"
        self.wallet = response.json()["walletId"]
        print(f"Created wallet ID: {self.wallet}")

    def list_wallets(self):
        print("\nList account wallets")
        list_wallets_url = f"{self.wallet_url}/auth/account/wallets"
        response = requests.get(list_wallets_url, headers=self.headers)
        assert response.status_code == 200, f"Expected status code 200 but got {response.status_code}"
        wallets = response.json()
        assert self.wallet in wallets, f"Expected {self.wallet} in {wallets}"
        print(f"Account wallets: {wallets}")

    def test_wallet_api2(self):
        self.register_user()
        self.login_user()
        self.create_wallet()
        self.list_wallets()


if __name__ == "__main__":
    unittest.main()
