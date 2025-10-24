// Photo ID mdoc Example
// This example demonstrates how to create and work with Photo ID mdocs
// following the same patterns as the existing MDL implementation

const { PhotoId, BirthDate, IsoSexEnum, MDocTypes } = require('waltid-mdoc-credentials');

// Example: Creating a Photo ID
function createPhotoIdExample() {
    // Create birth date with optional approximate mask
    const birthDate = new BirthDate(
        birthDate: new Date('1990-05-15'), // YYYY-MM-DD format
        approximateMask: null // or "00000000" for 8-digit binary mask
    );

    // Create Photo ID with required and optional fields
    const photoId = new PhotoId(
        // Required fields
        familyName: "DOE",
        givenName: "JOHN",
        birthDate: birthDate,
        portrait: new Uint8Array([/* portrait image bytes */]), // JPEG/JPEG 2000
        issueDate: new Date('2024-01-01'),
        expiryDate: new Date('2034-01-01'),
        issuingAuthority: "Federal Police",
        issuingCountry: "DE",
        ageOver18: true,
        
        // Optional fields
        familyNameViz: "DOE",
        givenNameViz: "JOHN",
        familyNameLatin1: "DOE",
        givenNameLatin1: "JOHN",
        issuingSubdivision: "BE",
        ageInYears: 34,
        portraitCaptureDate: new Date('2024-01-01'),
        birthplace: "Berlin, Germany",
        nameAtBirth: "JOHN DOE",
        residentAddress: "123 Example Street",
        residentCity: "Berlin",
        residentPostalCode: "10115",
        residentCountry: "DE",
        sex: IsoSexEnum.MALE,
        nationality: "DEU",
        documentNumber: "C1234567",
        
        // Additional Photo ID fields
        personId: "PID-987654321",
        birthCountry: "DE",
        birthState: "Berlin",
        birthCity: "Berlin",
        administrativeNumber: "ADM-123456",
        residentStreet: "Example Strasse",
        residentHouseNumber: "123",
        residentState: "BE",
        travelDocumentType: "P",
        travelDocumentNumber: "X1234567",
        travelDocumentMrz: "P<DEUDOEXXXXXXXX<JOHN<<<<<<<<<<<<<<<<<<<<<"
    );

    return photoId;
}

// Example: Serialization to different formats
function serializationExample() {
    const photoId = createPhotoIdExample();
    
    // Convert to CBOR
    const cborData = photoId.toCBOR();
    console.log("CBOR data length:", cborData.length);
    
    // Convert to CBOR hex string
    const cborHex = photoId.toCBORHex();
    console.log("CBOR hex:", cborHex.substring(0, 50) + "...");
    
    // Convert to JSON
    const jsonData = photoId.toJSON();
    console.log("JSON keys:", Object.keys(jsonData));
    
    return { cborData, cborHex, jsonData };
}

// Example: Deserialization from different formats
function deserializationExample() {
    const photoId = createPhotoIdExample();
    
    // Serialize to CBOR
    const cborData = photoId.toCBOR();
    
    // Deserialize from CBOR
    const deserializedPhotoId = PhotoId.fromCBOR(cborData);
    console.log("Deserialized family name:", deserializedPhotoId.familyName);
    
    // Serialize to JSON
    const jsonData = photoId.toJSON();
    
    // Deserialize from JSON
    const deserializedFromJson = PhotoId.fromJSON(jsonData);
    console.log("Deserialized from JSON - given name:", deserializedFromJson.givenName);
    
    return { deserializedPhotoId, deserializedFromJson };
}

// Example: Validation
function validationExample() {
    try {
        // This will fail validation due to invalid country code
        const invalidPhotoId = new PhotoId(
            familyName: "DOE",
            birthDate: new BirthDate(new Date('1990-05-15')),
            portrait: new Uint8Array([1, 2, 3]),
            issueDate: new Date('2024-01-01'),
            expiryDate: new Date('2034-01-01'),
            issuingAuthority: "Test Authority",
            issuingCountry: "INVALID", // This should fail validation
            ageOver18: true
        );
    } catch (error) {
        console.log("Validation error caught:", error.message);
    }
}

// Example: Working with document types
function documentTypeExample() {
    console.log("MDL Document Type:", MDocTypes.ISO_MDL);
    console.log("Photo ID Document Type:", MDocTypes.ISO_PHOTO_ID);
}

// Run examples
console.log("=== Photo ID mdoc Examples ===");
console.log("\n1. Creating Photo ID:");
const photoId = createPhotoIdExample();
console.log("Created Photo ID for:", photoId.familyName, photoId.givenName);

console.log("\n2. Serialization:");
serializationExample();

console.log("\n3. Deserialization:");
deserializationExample();

console.log("\n4. Validation:");
validationExample();

console.log("\n5. Document Types:");
documentTypeExample();

console.log("\n=== Examples completed ===");
