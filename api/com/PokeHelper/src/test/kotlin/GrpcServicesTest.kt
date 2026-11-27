package com.PokeHelper

import com.example.adapters.services.PokeServicesClient
import com.example.domain.InvalidPokemonRequest
import com.example.domain.PokemonNotFound
import com.example.domain.PokemonServiceUnavailable
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.Empty
import com.google.protobuf.Struct
import com.google.protobuf.util.JsonFormat
import com.pokehelper.contract.v1.*
import io.grpc.Context
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GrpcServicesTest {
    private val json = ObjectMapper()

    private fun document(text: String): Document = Document.newBuilder().setData(
        Struct.newBuilder().also { JsonFormat.parser().merge(text, it) }
    ).build()

    private fun fixture(test: (PokeServicesClient) -> Unit) {
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name).directExecutor()
            .addService(object : PokemonCatalogGrpc.PokemonCatalogImplBase() {
                private fun respond(id: String, observer: StreamObserver<Document>) {
                    val deadline = Context.current().deadline
                    assertTrue(deadline != null && deadline.timeRemaining(TimeUnit.MILLISECONDS) <= 1_000)
                    when (id) {
                        "invalid" -> observer.onError(Status.INVALID_ARGUMENT.withDescription("invalid identifier").asRuntimeException())
                        "missing" -> observer.onError(Status.NOT_FOUND.asRuntimeException())
                        "unavailable" -> observer.onError(Status.UNAVAILABLE.asRuntimeException())
                        "internal" -> observer.onError(Status.INTERNAL.asRuntimeException())
                        "slow" -> return // Accepted but never completes: the client deadline must fire.
                        else -> {
                            observer.onNext(document("""{"id":25,"name":"pikachu","sprites":{"extra":"preserved"}}"""))
                            observer.onCompleted()
                        }
                    }
                }
                override fun getPokemon(request: LookupRequest, observer: StreamObserver<Document>) = respond(request.identifier, observer)
                override fun getType(request: LookupRequest, observer: StreamObserver<Document>) = respond(request.identifier, observer)
                override fun listTypes(request: Empty, observer: StreamObserver<Document>) = respond("types", observer)
            })
            .addService(object : RecommendationsGrpc.RecommendationsImplBase() {
                override fun analyzeTeam(request: TeamRequest, observer: StreamObserver<Document>) {
                    assertEquals("pikachu", request.pokemonsList.single().fieldsMap["name"]!!.stringValue)
                    assertTrue(Context.current().deadline != null)
                    observer.onNext(document("""{"recommendedTypes":[{"type":"flying","score":4}]}"""))
                    observer.onCompleted()
                }
                override fun recommendPokemon(request: RecommendationRequest, observer: StreamObserver<Document>) {
                    assertEquals("flying", request.type)
                    assertEquals(1, request.pokemonsCount)
                    assertTrue(Context.current().deadline != null)
                    observer.onNext(document("""{"items":[]}"""))
                    observer.onCompleted()
                }
            }).build().start()
        val catalog = InProcessChannelBuilder.forName(name).directExecutor().build()
        val recommendations = InProcessChannelBuilder.forName(name).directExecutor().build()
        val adapter = PokeServicesClient(catalog, recommendations, 1_000)
        try { test(adapter) } finally { adapter.close(); server.shutdownNow() }
    }

    @Test
    fun `adapter calls both services and preserves documents`() = fixture { adapter -> runBlocking {
        assertEquals("preserved", adapter.pokemon("pikachu")["sprites"]["extra"].asText())
        assertEquals("pikachu", adapter.type("electric")["name"].asText())
        assertEquals("pikachu", adapter.types()["name"].asText())
        val body = json.readTree("""{"pokemons":[{"id":25,"name":"pikachu"}],"type":"flying"}""")
        assertEquals("flying", adapter.analyze(body)["recommendedTypes"][0]["type"].asText())
        assertTrue(adapter.recommendPokemon(body)["items"].isEmpty)
    } }

    @Test
    fun `adapter maps statuses and enforces deadline`() = fixture { adapter -> runBlocking {
        assertFailsWith<InvalidPokemonRequest> { adapter.pokemon("invalid") }
        assertFailsWith<PokemonNotFound> { adapter.pokemon("missing") }
        for (id in listOf("unavailable", "internal", "slow")) {
            val start = System.nanoTime()
            assertFailsWith<PokemonServiceUnavailable> { adapter.pokemon(id) }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3_000)
        }
    } }

    @Test
    fun `HTTP flow returns result and remains available after dependency fails`() = fixture { adapter ->
        testApplication {
            application { configureTestApi(adapter) }
            val success = client.get("/pokemon/pikachu")
            assertEquals(HttpStatusCode.OK, success.status, success.bodyAsText())
            assertEquals(HttpStatusCode.BadRequest, client.get("/pokemon/invalid").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/pokemon/missing").status)
            val start = System.nanoTime()
            val response = client.get("/pokemon/slow")
            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertEquals(502, json.readTree(response.bodyAsText())["status"].asInt())
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3_000)
            assertEquals(HttpStatusCode.OK, client.get("/").status)
        }
    }

    @Test
    fun `API starts and responds with server stopped`() {
        val name = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(name).directExecutor().build().start()
        server.shutdownNow().awaitTermination(1, TimeUnit.SECONDS)
        val adapter = PokeServicesClient(
            InProcessChannelBuilder.forName(name).directExecutor().build(),
            InProcessChannelBuilder.forName(name).directExecutor().build(), 300
        )
        try {
            testApplication {
                application { configureTestApi(adapter) }
                val start = System.nanoTime()
                assertEquals(HttpStatusCode.BadGateway, client.get("/pokemon/pikachu").status)
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1_500)
                assertEquals(HttpStatusCode.OK, client.get("/").status)
            }
        } finally { adapter.close() }
    }
}
