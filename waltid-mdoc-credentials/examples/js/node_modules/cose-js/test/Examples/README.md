# Examples

This project contains a number of examples and test cases for the IETF COSE WG specification.


The file Example.cddl contains a schema for how the example files are formatted.  

As time and the spec progresses, it is expected that we will start looking at adding examples that fail as well as successful examples.  While every attempt will be made to keep the examples in sync with the specifications, they may trail the current version at times.

# What is in each directory

* spec-examples - Contains the examples assoicated with the IETF specification for the [COSE message syntax](https://tools.ietf.org/html/draft-ietf-cose-msg).

* aes-ccm-examples - Contains Enveloped and Encrypt examples related to the AES CCM algorithm

* aes-gcm-examples - Contains Enveloped and Encrypt examples related to the AES GCM algorithm

* cbc-mac-examples - Contians Mac and Mac0 examples related to the AES CBC Mac algorithm

* chacha-poly-examples - Contains Enveloped and Encrypt examples related to the ChaCha-Poly1305 algorithm

* ecdh-direct-examples - Contains Enveloped and MAC examples related to the ECDH key managment algorithm where no key wrap algorithm is used

* ecdh-wrap-examples - Contains Enveloped and MAC examples related to the ECDH key managment algorithm where a key wrap algorithm is used

* ecdsa-examples - Contains Sign and Sign0 examples related to the ECDSA signature algorithm

* encrypted-tests - Contains Encrypt test examples

* hkdf-aes-examples - Contains Enveloped and Mac examples related to the use of direct key with the HKDF-AES recipient algorithms

* hkdf-hmac-sha-examples - Contains Enveloped and Mac examples related to the use of direct key with the HKDF-HMAC-SHA recipient algorithms

* hmac-examples - Contains Mac and Mac0 examples related to the HMAC-SHA algorithm

* CWT - Contains the examples from draft-ace-cwt


# Random number generation

The examples can potentially contain a random number generation stream.  This field contains a re-playable random number generator sequence that is used by the program which generates the examples.  The order in which calls are made to the random number generator should be documented in the description field when this present in the file.
