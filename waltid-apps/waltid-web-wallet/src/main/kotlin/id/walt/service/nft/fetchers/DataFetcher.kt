package id.walt.service.nft.fetchers

import id.walt.service.dto.NftDetailDataTransferObject
import id.walt.service.nft.converters.*
import id.walt.service.nft.fetchers.parameters.TokenDetailParameter
import id.walt.service.nft.fetchers.parameters.TokenListParameter

interface DataFetcher {
    fun all(parameter: TokenListParameter): List<NftDetailDataTransferObject>
    fun byId(parameter: TokenDetailParameter): NftDetailDataTransferObject

    companion object {
        private val evmFetcher = EvmDataFetcher(EvmDetailConverter())
        private val tezosFetcher = TezosDataFetcher(TezosDetailConverter())
        private val nearFetcher = NearDataFetcher(NearDetailConverter())
        private val flowFetcher = FlowDataFetcher(FlowDetailConverter())
        private val algorandFetcher = AlgorandDataFetcher(AlgorandDetailConverter())

        fun select(ecosystem: String) = when (ecosystem) {
            "ethereum" -> evmFetcher
            "tezos" -> tezosFetcher
            "near" -> nearFetcher
            "flow" -> flowFetcher
            "algorand" -> algorandFetcher
            else -> throw IllegalArgumentException("Ecosystem $ecosystem not supported.")
        }
    }
}
