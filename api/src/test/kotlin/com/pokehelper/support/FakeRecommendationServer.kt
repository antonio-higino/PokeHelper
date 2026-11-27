package com.pokehelper.support

import com.pokehelper.grpc.recommendation.v1.AnalyzeTeamRequest
import com.pokehelper.grpc.recommendation.v1.AnalyzeTeamResponse
import com.pokehelper.grpc.recommendation.v1.RecommendPokemonRequest
import com.pokehelper.grpc.recommendation.v1.RecommendPokemonResponse
import com.pokehelper.grpc.recommendation.v1.RecommendationServiceGrpcKt.RecommendationServiceCoroutineImplBase
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder

class FakeRecommendationServer(
    private val analyze: suspend (AnalyzeTeamRequest) -> AnalyzeTeamResponse = { throw StatusException(Status.UNIMPLEMENTED) },
    private val recommend: suspend (RecommendPokemonRequest) -> RecommendPokemonResponse = {
        throw StatusException(Status.UNIMPLEMENTED)
    }
) : AutoCloseable {
    private val name = InProcessServerBuilder.generateName()
    private val server: Server = InProcessServerBuilder.forName(name)
        .addService(object : RecommendationServiceCoroutineImplBase() {
            override suspend fun analyzeTeam(request: AnalyzeTeamRequest) = analyze(request)
            override suspend fun recommendPokemon(request: RecommendPokemonRequest) = recommend(request)
        })
        .build()
        .start()

    val channel: ManagedChannel = InProcessChannelBuilder.forName(name).build()

    fun stop() {
        server.shutdownNow().awaitTermination()
    }

    override fun close() {
        channel.shutdownNow()
        stop()
    }
}
