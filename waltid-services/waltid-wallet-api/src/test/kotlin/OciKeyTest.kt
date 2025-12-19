import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.oci.OCIKey
import id.walt.crypto.keys.oci.OCIsdkMetadata
import id.walt.did.dids.DidService
import kotlinx.coroutines.test.runTest

class OciKeyTest {

    //    @Test // oci authentication configuration required
    fun test() = runTest {
        DidService.minimalInit()

        val keyId = "ocid1.key.oc1.eu-frankfurt-1.entaeh2zaafiy.abtheljtxwi3nb5wled526ebomymdw4ytoa5hekqsbuzeg2aaee35cgidx7q"
        val vaultId = "ocid1.vault.oc1.eu-frankfurt-1.entaeh2zaafiy.abtheljss64qlgv6cxm7t4fi5dvfntfbval2ldt6yja3s4niix2hf36defua"
        val compartmentId = "ocid1.compartment.oc1..aaaaaaaawirugoz35riiybcxsvf7bmelqsxo3sajaav5w3i2vqowcwqrllxa"

        val config = OCIsdkMetadata(vaultId, compartmentId)

        val testkey = OCIKey(
            keyId,
            config,
            _keyType = KeyType.secp256r1
        )
        println("Public key: ${testkey.getPublicKey().exportJWK()}")

        val plaintext =
            """{"abc": "6,816,313 articles in EnglishFrom today's featured articleBlair PeachBlair PeachBlair Peach died on 24 April 1979 after an anti-racism demonstration in Southall, London, England. Peach, a New Zealand teacher and activist born in 1946, had taken part in an Anti-Nazi League demonstration against a National Front election meeting in Southall Town Hall. An investigation by Commander John Cass of the Metropolitan Police Service concluded that Peach had been fatally hit on the head by an officer of the service's Special Patrol Group, and that other officers had obstructed the investigation. Excerpts from a leaked copy of the report were published in early 1980. In 1988 the Metropolitan Police paid £75,000 compensation to Peach's family. The full report was not released to the public until 2009, after a newspaper vendor died from being struck from behind by a member of the Territorial Support Group, the Special Patrol Group's successor organisation. An award in Peach's honour was set up by the National Union of Teachers, and a school in Southall is named after him. (Full article...)Recently featured:    Stanley Price Weir Kathleen Ferrier 1984 World Snooker Championship    Archive By email More featured articles AboutDid you know ...Celestial globe by ÅkermanCelestial globe by Åkerman    ... that Anders Åkerman started the production of terrestrial and celestial globes (example pictured) in Sweden?    ... that the West Georgia Wolves football team won 13 games in 13 years before folding, but upon returning two decades later compiled consecutive undefeated regular seasons and became national champions?    ... that Fūka Izumi became a voice actress despite initially doubting that she could be one?    ... that seven countries competed in the Eurovision Song Contest 1994 for the first time, the largest single expansion of participating countries since the contest's first edition?    ... that the young Turkish open water swimmer Aysu Türkoğlu has completed three of the Oceans Seven series?    ... that the Indianapolis African-American community raised ${'$'}100,000 in just ten days in 1911 to establish the Senate Avenue YMCA?    ... that Edgar Wright's pitch for an Ant-Man film in 2006 helped to shape the early films of Phase One of the Marvel Cinematic Universe?    ... that while touring for her album Wallsocket, Underscores handed out pizza before her sets?    ... that The Glorious Cause: The American Revolution, 1763–1789 has been the first, second, and third volume of the Oxford History of the United States?    Archive Start a new article Nominate an articleIn the newsIchthyotitan severnensis compared to a humanIchthyotitan severnensis compared to a human    Ich­thy­o­titan, the largest known marine reptile (size com­par­ison shown), is formally described.    Flooding in the Persian Gulf and Arabian Peninsula leaves more than thirty people dead.    The historic Børsen in Copenhagen, Denmark, is severely damaged by a fire.    A knife attack in Sydney, Australia, leaves seven people dead.    In retaliation for an Israeli airstrike on the Iranian consulate in Damascus, Iran conducts missile and drone strikes against Israel.Ongoing:    Israel–Hamas war Myanmar civil war Russian invasion of Ukraine        timeline War in Sudan        timelineRecent deaths:    Terry Anderson Cecil Williams Andrew Davis Whitey Herzog Käthe Sasso Palitha Thewarapperuma    Nominate an articleOn this dayApril 24: Armenian Genocide Remembrance Day (1915); Administrative Professionals Day in various countries (2024)Hubble Space TelescopeHubble Space Telescope    1837 – A fire broke out in Surat, India, which went on to destroy about 75% of the city.    1916 – Irish republicans led by Patrick Pearse began the Easter Rising against British rule in Ireland, and proclaimed the Irish Republic an independent state.    1990 – The Hubble Space Telescope (pictured) was launched aboard STS-31 by Space Shuttle Discovery.    1993 – The Provisional Irish Republican Army detonated a truck bomb in London's financial district in Bishopsgate, killing one person, injuring forty-four others, and causing damage that cost £350 million to repair.    Mellitus (d. 624)Kumar Dharmasena (b. 1971)Estée Lauder (d. 2004)More anniversaries:    April 23 April 24 April 25    Archive By email List of days of the yearToday's featured pictureLuis Walter Alvarez 	Luis Walter Alvarez (1911–1988) was an American experimental physicist who was awarded the Nobel Prize in Physics in 1968 for his discovery of resonance states in particle physics using the hydrogen bubble chamber. After receiving his PhD from the University of Chicago in 1936, Alvarez went to work for Ernest Lawrence at the Radiation Laboratory at the University of California, Berkeley. He joined MIT Radiation Laboratory in 1940, where he contributed to a number of World War II radar projects and worked as a test pilot, before joining J. Robert Oppenheimer on the Manhattan Project in 1943. He moved back to Berkeley as a full professor after the war, going on to use his knowledge in work on improving particle accelerators. This 1969 photograph shows Alvarez with a magnetic monopole detector at Berkeley.Photograph credit: Lawrence Berkeley National Laboratory / Department of EnergyRecently featured:    Bistorta officinalis Pelophylax cypriensis Walter White    Archive More featured picturesOther areas of Wikipedia    Community portal – The central hub for editors, with resources, links, tasks, and announcements.    Village pump – Forum for discussions about Wikipedia itself, including policies and technical issues.    Site news – Sources of news about Wikipedia and the broader Wikimedia movement.    Teahouse – Ask basic questions about using or editing Wikipedia.    Help desk – Ask questions about using or editing Wikipedia.    Reference desk – Ask research questions about encyclopedic topics.    Content portals – A unique way to navigate the encyclopedia."}""".encodeToByteArray()

        val signed = testkey.signJws(plaintext)


        val issuerDid1 = DidService.registerByKey("jwk", testkey.getPublicKey()).did
        println("Issuer DID1: $issuerDid1")

        val publicKey = DidService.resolveToKey(issuerDid1).getOrThrow()

        val result = publicKey.verifyJws(signed)
        println(result)

        check(result.isSuccess)
    }

}
