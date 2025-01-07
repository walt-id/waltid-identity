export class Eip4361Message {
    private readonly _domain: String
    private readonly _address: String
    private readonly description: String
    private readonly _uri: String
    private readonly _version: Number
    private readonly _chainId: Number
    private readonly _nonce: String

    constructor(domain: String, address: String, description: String, uri: String, version: Number, chainId: Number, nonce: String) {
        this._domain = domain
        this._address = address
        this.description = description
        this._uri = uri
        this._version = version
        this._chainId = chainId
        this._nonce = nonce
    }

    serialize() {
        return `${this._domain} wants you to sign in with your Ethereum account:
${this._address}

${this.description}

URI: ${this._uri}
Version: ${this._version}
Chain ID: ${this._chainId}
Nonce: ${this._nonce}`
    }
}