package org.http4k.connect.amazon.s3

import org.http4k.connect.ChaosFake
import org.http4k.connect.storage.InMemory
import org.http4k.connect.storage.Storage
import org.http4k.core.Body
import org.http4k.core.ContentType.Companion.APPLICATION_XML
import org.http4k.core.Method.DELETE
import org.http4k.core.Method.GET
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.METHOD_NOT_ALLOWED
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.viewModel
import java.time.Clock
import java.time.Instant

/**
 * Global S3 operations (manage buckets)
 */
class FakeS3(
    private val buckets: Storage<Unit> = Storage.InMemory(),
    private val bucketContent: Storage<BucketKeyContent> = Storage.InMemory(),
    private val clock: Clock = Clock.systemDefaultZone()) : ChaosFake() {

    private val lens = Body.viewModel(HandlebarsTemplates().CachingClasspath(), APPLICATION_XML).toLens()

    override val app = routes(
        "/{id:.+}" bind routes(
            GET to {
                val id = it.path("id")!!
                when (val subdomain = it.subdomain()) {
                    "s3" -> listBucketKeys(subdomain) //??
                    else -> getKey(subdomain, id)
                }
            },
            PUT to {
                val id = it.path("id")!!
                when (val subdomain = it.subdomain()) {
                    "s3" -> putBucket(id)
                    else -> {
                        when (val source = it.header("x-amz-copy-source")) {
                            null -> putKey(subdomain, id, it.body.payload.array())
                            else -> copyKey(subdomain, source, id)
                        }

                    }
                }
            },
            DELETE to {
                val id = it.path("id")!!
                when (val subdomain = it.subdomain()) {
                    "s3" -> deleteBucket(id)
                    else -> deleteKey(subdomain, id)
                }
            }
        ),
        "/" bind routes(
            PUT to {
                when (val subdomain = it.subdomain()) {
                    "s3" -> Response(METHOD_NOT_ALLOWED)
                    else -> putBucket(subdomain)
                }
            },
            DELETE to {
                when (val subdomain = it.subdomain()) {
                    "s3" -> Response(METHOD_NOT_ALLOWED)
                    else -> deleteBucket(subdomain)
                }
            },
            GET to {
                when (val subdomain = it.subdomain()) {
                    "s3" -> listBuckets()
                    else -> listBucketKeys(subdomain)
                }
            }
        )
    )

    private fun copyKey(destinationBucket: String, source: String, destinationKey: String) =
        bucketContent[source.split("/").let { (sourceBucket, sourceKey) -> "$sourceBucket-$sourceKey" }]
            ?.let {
                putKey(destinationBucket, destinationKey, it.content)
                Response(OK)
            } ?: Response(NOT_FOUND)


    private fun getKey(bucket: String, key: String): Response {
        return buckets[bucket]
            ?.let {
                bucketContent["$bucket-$key"]?.content?.inputStream()
            }?.let { Response(OK).body(it) }
            ?: Response(NOT_FOUND)
    }

    private fun listBucketKeys(bucket: String) = buckets[bucket]
        ?.let {
            Response(OK)
                .with(lens of ListBucketResult(
                    bucketContent.keySet(bucket) { it.removePrefix("$bucket-") }
                        .map { bucketContent["$bucket-$it"]!! }
                ))
        }
        ?: Response(NOT_FOUND)

    private fun listBuckets() = Response(OK)
        .with(lens of ListAllMyBuckets(buckets.keySet("", ::BucketName).toList()))

    private fun deleteBucket(bucket: String) = Response(if (buckets.remove(bucket)) OK else NOT_FOUND)

    private fun deleteKey(bucket: String, key: String) = (buckets[bucket]
        ?.let { Response(if (bucketContent.remove("$bucket-$key")) OK else NOT_FOUND) }
        ?: Response(NOT_FOUND))

    private fun putKey(bucket: String, key: String, bytes: ByteArray) = buckets[bucket]
        ?.let {
            bucketContent["$bucket-$key"] = BucketKeyContent(BucketKey(key), bytes, Instant.now(clock))
            Response(CREATED)
        }
        ?: Response(NOT_FOUND)

    private fun putBucket(id: String): Response {
        buckets[id] ?: { buckets[id] = Unit }()
        return Response(CREATED)
    }

    private fun Request.subdomain() =
        (header("x-forwarded-host") ?: header("host"))?.split('.')?.firstOrNull() ?: "unknown"
}

fun main() {
    FakeS3().start()
}