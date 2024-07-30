import sdlib from "waltid-sd-jwt";

const sharedSecret = "ef23f749-7238-481a-815c-f0c2157dfa8e"
const cryptoProvider = new sdlib.id.walt.sdjwt.SimpleAsyncJWTCryptoProvider("HS256", new TextEncoder().encode(sharedSecret))

const sdMap = new sdlib.id.walt.sdjwt.SDMapBuilder(sdlib.id.walt.sdjwt.DecoyMode.FIXED.name, 2).addField("sub", true,
    new sdlib.id.walt.sdjwt.SDMapBuilder().addField("child", true).build()
).build()

console.log(sdMap, JSON.stringify(sdMap))

const sdPayload = new sdlib.id.walt.sdjwt.SDPayloadBuilder({"sub": "123", "aud": "345"}).buildForUndisclosedPayload({"aud": "345"})
const sdPayload2 = new sdlib.id.walt.sdjwt.SDPayloadBuilder({"sub": "123", "aud": "345"}).buildForSDMap(sdMap)

const jwt = await sdlib.id.walt.sdjwt.SDJwtJS.Companion.signAsync(
    sdPayload, cryptoProvider)
console.log(jwt.toString())

const jwt2 = await sdlib.id.walt.sdjwt.SDJwtJS.Companion.signAsync(
    sdPayload2, cryptoProvider)
console.log(jwt2.toString())

console.log("Verified:", (await jwt.verifyAsync(cryptoProvider)).verified)
console.log("Verified:", (await jwt2.verifyAsync(cryptoProvider)).verified)

const presentedJwt = await jwt.presentAllAsync(false)
console.log("Presented undisclosed SD-JWT:", presentedJwt.toString())
console.log("Verified: ", (await presentedJwt.verifyAsync(cryptoProvider)).verified)

const sdMap2 = new sdlib.id.walt.sdjwt.SDMapBuilder().buildFromJsonPaths(["sub"])
console.log("SDMap2:", sdMap2)
const presentedJwt2 = await jwt.presentAsync(sdMap2)
console.log("Presented disclosed SD-JWT:", presentedJwt2.toString())
const verificationResultPresentedJwt2 = await presentedJwt2.verifyAsync(cryptoProvider)
console.log("Presented payload", verificationResultPresentedJwt2.sdJwt.fullPayload)
console.log("Presented disclosures", verificationResultPresentedJwt2.sdJwt.disclosureObjects)
console.log("Presented disclosure strings", verificationResultPresentedJwt2.sdJwt.disclosures)
console.log("Verified: ", verificationResultPresentedJwt2.verified)
console.log("SDMap reconstructed", presentedJwt2.sdMap)
