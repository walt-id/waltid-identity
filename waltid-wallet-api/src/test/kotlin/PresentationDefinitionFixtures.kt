
class PresentationDefinitionFixtures {
    
    companion object {
        val presentationDefinitionExample1 = "{\n" +
            "    \"id\": \"vp token example\",\n" +
            "    \"input_descriptors\": [\n" +
            "        {\n" +
            "            \"id\": \"id card credential\",\n" +
            "            \"format\": {\n" +
            "                \"ldp_vc\": {\n" +
            "                    \"proof_type\": [\n" +
            "                        \"Ed25519Signature2018\"\n" +
            "                    ]\n" +
            "                }\n" +
            "            },\n" +
            "            \"constraints\": {\n" +
            "                \"fields\": [\n" +
            "                    {\n" +
            "                        \"path\": [\n" +
            "                            \"\$.type\"\n" +
            "                        ],\n" +
            "                        \"filter\": {\n" +
            "                            \"type\": \"string\",\n" +
            "                            \"pattern\": \"IDCardCredential\"\n" +
            "                        }\n" +
            "                    }\n" +
            "                ]\n" +
            "            }\n" +
            "        }\n" +
            "    ]\n" +
            "}"
    }
   
}