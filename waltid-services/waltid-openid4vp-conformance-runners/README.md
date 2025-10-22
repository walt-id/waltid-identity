# waltid-openid4vp-conformance-runners

This service is built to work with the [OpenID Conformance Suite](https://gitlab.com/openid/conformance-suite).

Test plans (TODO):

- OpenID Compliance - OpenID4VP 1.0
    - **Verifier**
        - sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post
        - sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post.jwt
        - iso_mdl + x509_san_dns + request_uri_signed + direct_post
        - iso_mdl + x509_san_dns + request_uri_signed + direct_post.jwt
    - **Wallet**
        - FOR ALL:
            - request_uri_unsigned + direct_post
            - request_uri_signed + direct_post
            - request_uri_unsigned + direct_post.jwt
            - request_uri_signed + direct_post.jwt
        - sd_jwt_vc:
            - did
            - pre_registered
            - redirect_uri
            - web-origin
            - x509_san_dns
        - iso_mdl:
            - did
            - pre_registered
            - redirect_uri
            - web-origin
            - x509_san_dns


## Install & run OpenID Conformance Suite
To setup the official conformance suite with devenv (official recommended way), install nix with your package manager (create nix build users on your machine, enable nix-daemon, etc.) and install devenv with nix.

NOTE: This process will install a custom CA + add certificates to your keychain and browser certificate stores, and update your /etc/hosts (handled by hostctl)! 


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
