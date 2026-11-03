# Waltid Helm Chart

This Helm chart deploys the Waltid stack, which consists of the issuer API2, verifier API2, web portal2, wallet API2 and the Compose web wallet.

## Prerequisites

- Kubernetes 1.12+
- Helm 3.x


## Installation

#### Using the repository

Clone the repository:
```bash
git clone https://github.com/walt-id/waltid-identity.git
``` 

To install the Waltid stack, run the following commands:

```bash
cd helm-charts/waltid
helm dependency build
``` 
Update the values.yaml file and run

```bash
helm install waltid ../waltid
``` 
