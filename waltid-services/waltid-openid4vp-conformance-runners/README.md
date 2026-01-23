<div align="center">
<h1>walt.id OpenID4VP Conformance Runners</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Utilities and instructions to run OpenID4VP 1.0 conformance tests against walt.id services</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

## What This Folder Contains

This folder documents how to set up and run the official **OpenID Conformance Suite** against walt.id implementations for **OpenID4VP 1.0** (Verifier and Wallet flows). It provides:
- Step‑by‑step setup for the OpenID Conformance Suite using `nix` + `devenv`
- Configuration snippets and example profiles in `config/`
- A curated list of test plans to exercise Verifier and Wallet scenarios

## Main Purpose

Help developers validate walt.id OpenID4VP implementations against the official conformance suite by:
- Installing and running the suite locally with trusted certificates and hosts
- Pointing the suite to walt.id Verifier/Wallet endpoints
- Executing a representative matrix of OpenID4VP 1.0 test cases

## Key Concepts

- **OpenID Conformance Suite**: The official test harness for OpenID/OAuth specs
- **OpenID4VP 1.0**: Final specification for verifiable presentations; used here for both Verifier and Wallet roles
- **Request Object Delivery**: `request_uri` (unsigned/signed) consumed by wallets
- **Response Modes**: `direct_post` and `direct_post.jwt` are typical for cross‑device flows
- **Client ID Schemes**: `x509_san_dns`, `redirect_uri`, `did`, `web-origin`, etc., used to authenticate verifiers

## Test Plans

OpenID Compliance — OpenID4VP 1.0

- Verifier
  - sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post
  - sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post.jwt
  - iso_mdl + x509_san_dns + request_uri_signed + direct_post
  - iso_mdl + x509_san_dns + request_uri_signed + direct_post.jwt
- Wallet (for all below run both unsigned/signed + direct_post/direct_post.jwt)
  - sd_jwt_vc: `did`, `pre_registered`, `redirect_uri`, `web-origin`, `x509_san_dns`
  - iso_mdl: `did`, `pre_registered`, `redirect_uri`, `web-origin`, `x509_san_dns`

Note: See `config/` for example configuration files you can adapt for specific runs.

## Install & run OpenID Conformance Suite
To setup the official conformance suite with devenv (official recommended way), install nix with your package manager (create nix build users on your machine, enable nix-daemon, etc.) and install devenv with nix.

NOTE: This process will install a custom CA + add certificates to your keychain and browser certificate stores, and update your /etc/hosts (handled by hostctl)! 

## Setup the OpenID Conformance Suite

### Clone the OpenID Conformance Suite repository
```shell
git clone git@gitlab.com:openid/conformance-suite.git
```

Enter the directory
```shell
cd conformance-suite
```

### Install nix

#### Native
If your native package manager has it, you can install it using that:
```shell
sudo pacman -S nix
```

#### Nixos
Alternatively, you can run this multi-user installation script from Nixos:
```shell
sh <(curl --proto '=https' --tlsv1.2 -L https://nixos.org/nix/install) --daemon
```
Keep in mind that you must be able to authenticate with sudo, your Linux must be running systemd, and SELinux has to be disabled!

#### Third-party
Alternatively, there is a **third-party** company (Determinate Systems) script: https://zero-to-nix.com/
```shell
curl --proto '=https' --tlsv1.2 -sSf -L https://install.determinate.systems/nix -o nix-install.sh
less ./nix-install.sh
sh ./nix-install.sh install
```

### Setup nix
#### Enable nix service
```shell
sudo systemctl start nix-daemon.service && sudo systemctl enable nix-daemon.service
```

#### Setup nix channels
```shell
nix-channel --add https://nixos.org/channels/nixpkgs-unstable
nix-channel --update
```

#### Test
Check if nix is installed correctly:
```shell
nix-env -iA nixpkgs.hello
```
This command should have installed the hello command at `/nix/store/[hash]-hello-[version]/bin/hello`.
Run it:
```shell
hello
```

If you cannot run the `hello` program, then likely your path is not setup correctly.
Add `~/.nix-profile/bin` to your PATH variable.
```shell
PATH=$PATH:~/.nix-profile/bin
```

### Install devenv
After you have successfully installed & setup nix, you can now use nix to install Cachix devenv.

#### Usual
Make sure you have setup nix correctly and the **nix daemon is running**!
```shell
nix-env --install --attr devenv -f https://github.com/NixOS/nixpkgs/tarball/nixpkgs-unstable
```

#### Nix profiles
This requires experimental flags!
```shell
nix profile install nixpkgs#devenv
```

#### NixOS/nix-darwin
`configuration.nix`
```nix
environment.systemPackages = [
  pkgs.devenv
];
```

#### home-manager
`home.nix`
```nix
home.packages = [
  pkgs.devenv
];
```

### Devenv optional step: Configure a GitHub access token
> The Nix ecosystem is heavily dependent on GitHub for hosting and distributing source code, like the source for nixpkgs.
> This means that Nix will make a lot of un-authenticated requests to the GitHub API and you may encounter rate-limiting.

Create a new token with no extra permissions at https://github.com/settings/personal-access-tokens/new

Add token to `~/.config/nix/nix.conf`:
```shell
access-tokens = github.com=<GITHUB_TOKEN>
```

### Setup conformance suite with devenv
As you have now installed nix to install devenv you may now use devenv to install conformance suite components.
Make sure you are in the repository directory!

Run:
```shell
devenv up
```

Devenv will now:
- download the toolchain
- change your `/etc/hosts` to include `localhost.emobix.co.uk`
- create a CA
- create a certificate
- add the certificate to your keychain + browser certificate store

### Run compliance suite application
- Open another terminal
- cd into the repository directory again
- in the second terminal, run `mvn spring-boot:run`

### Visit conformance suite site
You can now direct your browser to `https://localhost.emobix.co.uk:8443/`
NOTE: This page has a non-trusted CA certificate, your browser might show
a small warning, e.g. "Connection verified by a certificate issuer that is not recognized by Mozilla."


### Further information
See https://gitlab.com/openid/conformance-suite/-/wikis/Developers/Build-&-Run#intellij to learn
how to compile the conformance suite yourself.


## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)
<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
